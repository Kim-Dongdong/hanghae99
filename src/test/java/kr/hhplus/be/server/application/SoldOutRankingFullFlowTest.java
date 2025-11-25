package kr.hhplus.be.server.application;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import kr.hhplus.be.server.application.usecase.GetFastestSoldOutRankingUseCase;
import kr.hhplus.be.server.application.usecase.HoldSeatUseCase;
import kr.hhplus.be.server.application.usecase.RecordSoldOutUseCase;
import kr.hhplus.be.server.config.manager.DistributedLockManager;
import kr.hhplus.be.server.infrastructure.persistence.adapter.RedisRankingAdapter;
import kr.hhplus.be.server.infrastructure.persistence.adapter.ReservationJpaAdapter;
import kr.hhplus.be.server.infrastructure.persistence.adapter.SeatInventoryJpaAdapter;
import kr.hhplus.be.server.infrastructure.persistence.adapter.ShowQueryJpaAdapter;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowSeatEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowSeatJpa;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableJpaRepositories(basePackages = "kr.hhplus.be.server.infrastructure.persistence.springdata")
@EntityScan(basePackages = "kr.hhplus.be.server.infrastructure.persistence.entity")
@Import({
	// UseCases
	HoldSeatUseCase.class,
	RecordSoldOutUseCase.class,
	GetFastestSoldOutRankingUseCase.class,

	// Adapters
	ShowQueryJpaAdapter.class,
	RedisRankingAdapter.class,
	SeatInventoryJpaAdapter.class,
	ReservationJpaAdapter.class,

	// Managers
	DistributedLockManager.class,

	// Test Config
	SoldOutRankingFullFlowTest.TestRedisConfig.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SoldOutRankingFullFlowTest {

	@Container
	static GenericContainer<?> redisContainer =
		new GenericContainer<>("redis:7.0-alpine")
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		// H2 설정
		registry.add("spring.datasource.url",
			() -> "jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
	}

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
				.setAddress(redisAddress);
			return Redisson.create(config);
		}
	}

	@Autowired
	private SpringShowJpa showJpa;

	@Autowired
	private SpringShowSeatJpa showSeatJpa;

	@Autowired
	private HoldSeatUseCase reserveSeatsUseCase;

	@Autowired
	private RecordSoldOutUseCase recordSoldOutUseCase;

	@Autowired
	private GetFastestSoldOutRankingUseCase getRankingUseCase;

	@Autowired
	private RedisTemplate<String, String> redisTemplate;

	private static final String RANKING_KEY = "show:soldout:ranking";

	@BeforeEach
	void setUp() {
		redisTemplate.delete(RANKING_KEY);

		showSeatJpa.deleteAll(); // 좌석 먼저 삭제 (FK 고려)
		showJpa.deleteAll();     // 공연 삭제
	}

	@AfterEach
	void tearDown() {
		redisTemplate.delete(RANKING_KEY);
	}

	@Test
	@DisplayName("전체 플로우: 콘서트 생성 → 매진 → 랭킹 기록 → 조회")
	void fullFlow_createConcert_soldOut_ranking() throws InterruptedException {
		// given: 3개의 콘서트 생성 (좌석 수가 다름)
		ShowEntity show1 = createShowWithSeats(1L, "BTS Concert", 10,
			LocalDateTime.now().minusMinutes(20));
		ShowEntity show2 = createShowWithSeats(2L, "IU Concert", 20,
			LocalDateTime.now().minusMinutes(15));
		ShowEntity show3 = createShowWithSeats(3L, "Blackpink Concert", 30,
			LocalDateTime.now().minusMinutes(10));

		// when: 각 콘서트의 모든 좌석을 동시에 예약 (매진)
		reserveAllSeats(show1.id, 10);
		Thread.sleep(100);

		reserveAllSeats(show2.id, 20);
		Thread.sleep(100);

		reserveAllSeats(show3.id, 30);
		Thread.sleep(100);

		// 매진 기록
		recordSoldOutUseCase.handle(show1.id);
		recordSoldOutUseCase.handle(show2.id);
		recordSoldOutUseCase.handle(show3.id);

		// then: 랭킹 조회
		GetFastestSoldOutRankingUseCase.Result result = getRankingUseCase.handle(10);

		assertThat(result.rankings()).hasSize(3);

		// show3가 가장 빠르게 매진 (10분 전 시작, 30석)
		assertThat(result.rankings().get(0).showId()).isEqualTo(show3.id);
		assertThat(result.rankings().get(0).title()).isEqualTo("Blackpink Concert");

		// show2가 두 번째
		assertThat(result.rankings().get(1).showId()).isEqualTo(show2.id);

		// show1이 세 번째
		assertThat(result.rankings().get(2).showId()).isEqualTo(show1.id);

		System.out.println("\n=== 매진 랭킹 ===");
		for (int i = 0; i < result.rankings().size(); i++) {
			var ranking = result.rankings().get(i);
			System.out.printf("%d위: %s - %s\n",
				i + 1,
				ranking.title(),
				ranking.durationFormatted());
		}
	}

	@Test
	@DisplayName("동시성 테스트: 50명이 동시에 마지막 좌석 예약 시도, 매진 기록은 1번만")
	void concurrency_lastSeat_soldOutRecordOnce() throws InterruptedException {
		// given: 좌석 1개만 있는 콘서트
		ShowEntity show = createShowWithSeats(1L, "Limited Concert", 1,
			LocalDateTime.now().minusMinutes(5));

		int threadCount = 50;
		ExecutorService executorService = Executors.newFixedThreadPool(10);
		CountDownLatch latch = new CountDownLatch(threadCount);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger soldOutRecordCount = new AtomicInteger(0);

		// when: 50명이 동시에 예약 시도
		for (int i = 0; i < threadCount; i++) {
			final int userId = i;
			executorService.submit(() -> {
				try {
					// 예약 시도
					reserveSeatsUseCase.handle((long)userId, show.id, 1);
					successCount.incrementAndGet();

					// 매진 확인 후 기록 시도
					if (isSoldOut(show.id)) {
						RecordSoldOutUseCase.Result result =
							recordSoldOutUseCase.handle(show.id);
						if (result.newRecord()) {
							soldOutRecordCount.incrementAndGet();
						}
					}
				} catch (Exception e) {
					// 예약 실패는 정상
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await();
		executorService.shutdown();
		executorService.awaitTermination(10, TimeUnit.SECONDS);

		// then
		assertThat(successCount.get()).isEqualTo(1); // 1명만 예약 성공
		assertThat(soldOutRecordCount.get()).isEqualTo(1); // 매진 기록도 1번만
	}

	private ShowEntity createShowWithSeats(Long showId, String title,
		int seatCount, LocalDateTime salesOpenAt) {
		// Show 생성
		ShowEntity show = new ShowEntity();
		show.id = showId;
		show.title = title;
		show.venue = "Test Venue";
		show.startsAt = LocalDateTime.now().plusDays(1);
		show.salesOpenAt = salesOpenAt;
		show.salesCloseAt = LocalDateTime.now().plusDays(1);
		show.seatCount = seatCount;
		show.status = "AVAILABLE";
		show.createdAt = LocalDateTime.now();
		showJpa.save(show);

		// 좌석 생성
		for (int i = 1; i <= seatCount; i++) {
			ShowSeatEntity seat = new ShowSeatEntity();
			seat.showId = showId;
			seat.seatNo = i;
			seat.seatTier = "A";
			seat.basePrice = 100000L;
			seat.isActive = true;
			showSeatJpa.save(seat);
		}

		return show;
	}

	private void reserveAllSeats(Long showId, int seatCount) throws InterruptedException {
		ExecutorService executorService = Executors.newFixedThreadPool(10);
		CountDownLatch latch = new CountDownLatch(seatCount);

		for (int i = 1; i <= seatCount; i++) {
			final int seatNo = i;
			final int userId = i;
			executorService.submit(() -> {
				try {
					reserveSeatsUseCase.handle(showId, (long)userId, seatNo);
				} catch (Exception e) {
					// 예약 실패는 무시
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await();
		executorService.shutdown();
		executorService.awaitTermination(10, TimeUnit.SECONDS);
	}

	// soldout되었는지 확인하는 메서드, 여기서는 간략화
	private boolean isSoldOut(Long showId) {
		return true; // 예시
	}
}
