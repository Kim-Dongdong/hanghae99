package kr.hhplus.be.server.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
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

import kr.hhplus.be.server.application.usecase.RecordSoldOutUseCase;
import kr.hhplus.be.server.config.manager.DistributedLockManager;
import kr.hhplus.be.server.domain.port.ConcertQueryPort;
import kr.hhplus.be.server.domain.port.SoldOutRankingPort;
import kr.hhplus.be.server.infrastructure.persistence.adapter.RedisRankingAdapter;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowEntity;

@SpringBootTest(classes = {
	RecordSoldOutUseCase.class,
	RedisRankingAdapter.class,
	DistributedLockManager.class,
	RecordSoldOutUseCaseTest.TestRedisConfig.class
})
@Testcontainers
class RecordSoldOutUseCaseTest {

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

		@Bean
		public RedissonClient redissonClient() {
			Config config = new Config();
			String redisAddress = String.format("redis://%s:%d",
				redisContainer.getHost(),
				redisContainer.getMappedPort(6379));
			config.useSingleServer()
				.setAddress(redisAddress)
				.setConnectionPoolSize(50)
				.setConnectionMinimumIdleSize(10);
			return Redisson.create(config);
		}
	}

	@MockitoBean
	private ConcertQueryPort concertQueryPort;

	@Autowired
	private RecordSoldOutUseCase recordSoldOutUseCase;

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
	@DisplayName("매진 기록이 성공적으로 저장된다")
	void recordSoldOut_success() {
		// given
		Long showId = 1L;
		LocalDateTime salesOpenAt = LocalDateTime.now().minusMinutes(10);

		ShowEntity show = createShow(showId, salesOpenAt);
		given(concertQueryPort.findById(showId)).willReturn(show);

		// when
		RecordSoldOutUseCase.Result result = recordSoldOutUseCase.handle(showId);

		// then
		assertThat(result.newRecord()).isTrue();
		assertThat(result.showId()).isEqualTo(showId);
		assertThat(result.soldOutDurationSeconds()).isGreaterThan(0);
		assertThat(result.rank()).isEqualTo(1L);
		assertThat(rankingPort.hasSoldOutRecord(showId)).isTrue();
	}

	@Test
	@DisplayName("이미 기록된 매진은 중복 저장되지 않는다 (멱등성)")
	void recordSoldOut_idempotency() {
		// given
		Long showId = 1L;
		LocalDateTime salesOpenAt = LocalDateTime.now().minusMinutes(10);

		ShowEntity show = createShow(showId, salesOpenAt);
		given(concertQueryPort.findById(showId)).willReturn(show);

		// when
		RecordSoldOutUseCase.Result firstResult = recordSoldOutUseCase.handle(showId);
		RecordSoldOutUseCase.Result secondResult = recordSoldOutUseCase.handle(showId);

		// then
		assertThat(firstResult.newRecord()).isTrue();
		assertThat(secondResult.newRecord()).isFalse();
		assertThat(firstResult.soldOutDurationSeconds())
			.isEqualTo(secondResult.soldOutDurationSeconds());
	}

	@Test
	@DisplayName("동시에 여러 요청이 들어와도 한 번만 기록된다 (분산 락)")
	void recordSoldOut_concurrency() throws InterruptedException {
		// given
		Long showId = 1L;
		LocalDateTime salesOpenAt = LocalDateTime.now().minusMinutes(10);
		ShowEntity show = createShow(showId, salesOpenAt);
		given(concertQueryPort.findById(showId)).willReturn(show);

		int threadCount = 10;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);
		AtomicInteger newRecordCount = new AtomicInteger(0);

		// when
		for (int i = 0; i < threadCount; i++) {
			executorService.submit(() -> {
				try {
					RecordSoldOutUseCase.Result result = recordSoldOutUseCase.handle(showId);
					if (result.newRecord()) {
						newRecordCount.incrementAndGet();
					}
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await();
		executorService.shutdown();
		executorService.awaitTermination(10, TimeUnit.SECONDS);

		// then
		assertThat(newRecordCount.get()).isEqualTo(1);
		assertThat(rankingPort.hasSoldOutRecord(showId)).isTrue();
	}

	@Test
	@DisplayName("매진 시간이 정확히 계산된다")
	void recordSoldOut_calculateDuration() {
		// given
		Long showId = 1L;
		LocalDateTime salesOpenAt = LocalDateTime.now().minusMinutes(5);
		ShowEntity show = createShow(showId, salesOpenAt);
		given(concertQueryPort.findById(showId)).willReturn(show);

		// when
		RecordSoldOutUseCase.Result result = recordSoldOutUseCase.handle(showId);

		// then
		// 약 5분(300초) 정도 소요되었을 것
		assertThat(result.soldOutDurationSeconds())
			.isBetween(295L, 305L);
	}

	private ShowEntity createShow(Long showId, LocalDateTime salesOpenAt) {
		ShowEntity show = new ShowEntity();
		show.id = showId;
		show.title = "Test Concert " + showId;
		show.venue = "Test Venue";
		show.salesOpenAt = salesOpenAt;
		show.status = "AVAILABLE";
		return show;
	}
}
