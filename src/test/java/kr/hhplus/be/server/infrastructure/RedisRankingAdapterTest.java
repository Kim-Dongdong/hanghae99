package kr.hhplus.be.server.infrastructure;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Optional;

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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import kr.hhplus.be.server.domain.port.SoldOutRankingPort;
import kr.hhplus.be.server.infrastructure.persistence.adapter.RedisRankingAdapter;

@SpringBootTest(classes = {
	RedisRankingAdapter.class,
	RedisRankingAdapterTest.TestRedisConfig.class
})
@Testcontainers
class RedisRankingAdapterTest {

	@Container
	static GenericContainer<?> redisContainer =
		new GenericContainer<>("redis:7.0-alpine")
			.withExposedPorts(6379);

	// 2. TestConfiguration 클래스 - redis 수동 설정
	@TestConfiguration
	static class TestRedisConfig {

		// Redis 연결 팩토리 생성
		@Bean
		public RedisConnectionFactory redisConnectionFactory() {
			RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
				redisContainer.getHost(),
				redisContainer.getMappedPort(6379)
			);
			return new LettuceConnectionFactory(config);
		}

		// <String, String> 타입의 Template 직접 생성
		@Bean
		public RedisTemplate<String, String> redisTemplate() {
			RedisTemplate<String, String> template = new RedisTemplate<>();
			template.setConnectionFactory(redisConnectionFactory());
			template.setKeySerializer(new StringRedisSerializer());
			template.setValueSerializer(new StringRedisSerializer());
			return template;
		}
	}

	@Autowired
	private RedisRankingAdapter rankingAdapter;

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
	@DisplayName("매진 기록을 Redis에 저장한다")
	void recordSoldOut() {
		// given
		Long showId = 1L;
		long duration = 300L;

		// when
		rankingAdapter.recordSoldOut(showId, duration);

		// then
		assertThat(rankingAdapter.hasSoldOutRecord(showId)).isTrue();
		assertThat(rankingAdapter.getSoldOutDuration(showId)).isEqualTo(Optional.of(300L));
	}

	@Test
	@DisplayName("가장 빠른 매진 순으로 정렬되어 조회된다")
	void getFastestSoldOut_sortedByDuration() {
		// given
		rankingAdapter.recordSoldOut(1L, 600L);
		rankingAdapter.recordSoldOut(2L, 120L);
		rankingAdapter.recordSoldOut(3L, 300L);

		// when
		List<SoldOutRankingPort.RankingEntry> rankings = rankingAdapter.getFastestSoldOut(10);

		// then
		assertThat(rankings).hasSize(3);
		assertThat(rankings.get(0).showId()).isEqualTo(2L);
		assertThat(rankings.get(0).soldOutDurationSeconds()).isEqualTo(120L);
		assertThat(rankings.get(1).showId()).isEqualTo(3L);
		assertThat(rankings.get(2).showId()).isEqualTo(1L);
	}

	@Test
	@DisplayName("limit만큼만 조회된다")
	void getFastestSoldOut_withLimit() {
		// given
		for (int i = 1; i <= 20; i++) {
			rankingAdapter.recordSoldOut((long) i, i * 60L);
		}

		// when
		List<SoldOutRankingPort.RankingEntry> rankings = rankingAdapter.getFastestSoldOut(5);

		// then
		assertThat(rankings).hasSize(5);
	}

	@Test
	@DisplayName("랭킹을 정확하게 반환한다")
	void getRank() {
		// given
		rankingAdapter.recordSoldOut(1L, 600L);
		rankingAdapter.recordSoldOut(2L, 120L);
		rankingAdapter.recordSoldOut(3L, 300L);

		// when & then
		assertThat(rankingAdapter.getRank(2L)).isEqualTo(Optional.of(1L));
		assertThat(rankingAdapter.getRank(3L)).isEqualTo(Optional.of(2L));
		assertThat(rankingAdapter.getRank(1L)).isEqualTo(Optional.of(3L));
	}

	@Test
	@DisplayName("기록이 없으면 empty를 반환한다")
	void getRank_notFound() {
		// when & then
		assertThat(rankingAdapter.getRank(999L)).isEmpty();
		assertThat(rankingAdapter.getSoldOutDuration(999L)).isEmpty();
	}
}
