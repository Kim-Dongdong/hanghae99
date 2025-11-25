package kr.hhplus.be.server.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import kr.hhplus.be.server.application.usecase.GetFastestSoldOutRankingUseCase;
import kr.hhplus.be.server.domain.port.ConcertQueryPort;
import kr.hhplus.be.server.domain.port.SoldOutRankingPort;
import kr.hhplus.be.server.infrastructure.persistence.adapter.RedisRankingAdapter;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowEntity;

@SpringBootTest(classes = {
	GetFastestSoldOutRankingUseCase.class,
	RedisRankingAdapter.class,
	GetFastestSoldOutRankingUseCaseTest.TestRedisConfig.class
})
@Testcontainers
class GetFastestSoldOutRankingUseCaseTest {

	@Container
	static GenericContainer<?> redisContainer =
		new GenericContainer<>("redis:7.0-alpine")
			.withExposedPorts(6379);

	@TestConfiguration
	static class TestRedisConfig {

		@Bean
		public RedisConnectionFactory redisConnectionFactory() {
			RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
				redisContainer.getHost(),
				redisContainer.getMappedPort(6379)
			);
			return new LettuceConnectionFactory(config);
		}

		@Bean
		public RedisTemplate<String, String> redisTemplate() {
			RedisTemplate<String, String> template = new RedisTemplate<>();
			template.setConnectionFactory(redisConnectionFactory());
			template.setKeySerializer(new StringRedisSerializer());
			template.setValueSerializer(new StringRedisSerializer());
			return template;
		}
	}

	@MockitoBean
	private ConcertQueryPort concertQueryPort;

	@Autowired
	private GetFastestSoldOutRankingUseCase getRankingUseCase;

	@Autowired
	private SoldOutRankingPort rankingPort;

	@Autowired
	private RedisTemplate<String, String> redisTemplate;

	private static final String RANKING_KEY = "show:soldout:ranking";

	@BeforeEach
	void setUp() {
		redisTemplate.delete(RANKING_KEY);
	}

	@AfterEach
	void tearDown() {
		redisTemplate.delete(RANKING_KEY);
	}

	@Test
	@DisplayName("Top 10 랭킹을 조회한다")
	void getFastestSoldOut() {
		// given
		rankingPort.recordSoldOut(1L, 120L);  // 2분
		rankingPort.recordSoldOut(2L, 300L);  // 5분
		rankingPort.recordSoldOut(3L, 600L);  // 10분

		ShowEntity show1 = createShow(1L, "BTS Concert");
		ShowEntity show2 = createShow(2L, "IU Concert");
		ShowEntity show3 = createShow(3L, "Blackpink Concert");

		given(concertQueryPort.findAllByIds(List.of(1L, 2L, 3L)))
			.willReturn(List.of(show1, show2, show3));

		// when
		GetFastestSoldOutRankingUseCase.Result result = getRankingUseCase.handle(10);

		// then
		assertThat(result.rankings()).hasSize(3);
		assertThat(result.rankings().get(0).showId()).isEqualTo(1L);
		assertThat(result.rankings().get(0).title()).isEqualTo("BTS Concert");
		assertThat(result.rankings().get(0).soldOutDurationSeconds()).isEqualTo(120L);
		assertThat(result.rankings().get(0).durationFormatted()).isEqualTo("2분");
	}

	@Test
	@DisplayName("매진 시간 포맷팅이 정확하다")
	void formatDuration() {
		// given
		rankingPort.recordSoldOut(1L, 30L);    // 30초
		rankingPort.recordSoldOut(2L, 120L);   // 2분
		rankingPort.recordSoldOut(3L, 185L);   // 3분 5초

		ShowEntity show1 = createShow(1L, "Show 1");
		ShowEntity show2 = createShow(2L, "Show 2");
		ShowEntity show3 = createShow(3L, "Show 3");

		given(concertQueryPort.findAllByIds(List.of(1L, 2L, 3L)))
			.willReturn(List.of(show1, show2, show3));

		// when
		GetFastestSoldOutRankingUseCase.Result result = getRankingUseCase.handle(10);

		// then
		assertThat(result.rankings().get(0).durationFormatted()).isEqualTo("30초");
		assertThat(result.rankings().get(1).durationFormatted()).isEqualTo("2분");
		assertThat(result.rankings().get(2).durationFormatted()).isEqualTo("3분 5초");
	}

	@Test
	@DisplayName("limit만큼만 조회된다")
	void getFastestSoldOut_withLimit() {
		// given
		for (int i = 1; i <= 20; i++) {
			rankingPort.recordSoldOut((long) i, i * 60L);
		}

		List<ShowEntity> shows = List.of(
			createShow(1L, "Show 1"),
			createShow(2L, "Show 2"),
			createShow(3L, "Show 3"),
			createShow(4L, "Show 4"),
			createShow(5L, "Show 5")
		);
		given(concertQueryPort.findAllByIds(List.of(1L, 2L, 3L, 4L, 5L)))
			.willReturn(shows);

		// when
		GetFastestSoldOutRankingUseCase.Result result = getRankingUseCase.handle(5);

		// then
		assertThat(result.rankings()).hasSize(5);
	}

	@Test
	@DisplayName("랭킹이 없으면 빈 리스트를 반환한다")
	void getFastestSoldOut_empty() {
		// when
		GetFastestSoldOutRankingUseCase.Result result = getRankingUseCase.handle(10);

		// then
		assertThat(result.rankings()).isEmpty();
	}

	private ShowEntity createShow(Long showId, String title) {
		ShowEntity show = new ShowEntity();
		show.id = showId;
		show.title = title;
		show.venue = "Test Venue";
		return show;
	}
}
