package kr.hhplus.be.server.application;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import kr.hhplus.be.server.application.usecase.RechargePointUseCase;
import kr.hhplus.be.server.config.manager.DistributedLockManager;
import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.domain.port.WalletPort;
import kr.hhplus.be.server.infrastructure.persistence.adapter.WalletJpaAdapter;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringReservationJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringReservationSeatJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringSeatStateJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowSeatJpa;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableJpaRepositories(basePackages = "kr.hhplus.be.server.infrastructure.persistence.springdata")
@EntityScan(basePackages = "kr.hhplus.be.server.infrastructure.persistence.entity")
@Import({
	RechargePointUseCase.class,
	WalletJpaAdapter.class,
	DistributedLockManager.class,
	RechargePointUseCaseIntegrationTest.TestRedissonConfig.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RechargePointUseCaseIntegrationTest {

	// 테스트와 무관한 Repository Mock
	@MockitoBean
	private SpringReservationJpa springReservationJpa;

	@MockitoBean
	private SpringReservationSeatJpa springReservationSeatJpa;

	@MockitoBean
	private SpringSeatStateJpa springSeatStateJpa;

	@MockitoBean
	private SpringShowJpa springShowJpa;

	@MockitoBean
	private SpringShowSeatJpa springShowSeatJpa;

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

	// 테스트용 RedissonConfig
	@org.springframework.context.annotation.Configuration
	static class TestRedissonConfig {
		@org.springframework.context.annotation.Bean
		public org.redisson.api.RedissonClient redissonClient() {
			org.redisson.config.Config config = new org.redisson.config.Config();
			String redisAddress = String.format("redis://%s:%d",
				redisContainer.getHost(),
				redisContainer.getMappedPort(6379));
			config.useSingleServer()
				.setAddress(redisAddress)
				.setConnectionPoolSize(50)
				.setConnectionMinimumIdleSize(10);
			return org.redisson.Redisson.create(config);
		}
	}

	@Autowired
	private RechargePointUseCase rechargePointUseCase;

	@Autowired
	private WalletPort walletPort;

	private static final Long TEST_USER_ID = 1L;
	private static final Money CHARGE_AMOUNT = Money.of(1000L);

	@BeforeEach
	void setUp() {
		walletPort.findByUserId(TEST_USER_ID);
	}

	@Test
	@DisplayName("분산락 동시성 테스트")
	void concurrencyWithDistributedLock() throws InterruptedException {
		// given
		int threadCount = 50;
		ExecutorService executorService = Executors.newFixedThreadPool(10);
		CountDownLatch latch = new CountDownLatch(threadCount);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// when
		long startTime = System.currentTimeMillis();

		for (int i = 0; i < threadCount; i++) {
			final int index = i;
			executorService.submit(() -> {
				try {
					rechargePointUseCase.handleWithDistributedLock(
						TEST_USER_ID,
						CHARGE_AMOUNT,
						"request-" + index
					);
					successCount.incrementAndGet();
				} catch (Exception e) {
					failCount.incrementAndGet();
					System.err.println("요청 " + index + " 실패: " + e.getMessage());
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await();
		executorService.shutdown();
		executorService.awaitTermination(30, TimeUnit.SECONDS);

		// 트랜잭션 반영 대기
		Thread.sleep(500);

		long duration = System.currentTimeMillis() - startTime;

		// then
		PointWallet after = walletPort.findByUserId(TEST_USER_ID);
		Money expectedBalance = CHARGE_AMOUNT.multiply(successCount.get());

		System.out.println("\n=== 분산락 동시성 테스트 ===");
		System.out.println("소요 시간: " + duration + "ms");
		System.out.println("성공: " + successCount.get() + "/" + threadCount);
		System.out.println("실패: " + failCount.get());
		System.out.println("예상 잔액: " + expectedBalance.asLong());
		System.out.println("실제 잔액: " + after.getBalance().asLong());

		assertThat(successCount.get()).isGreaterThan(0);
		assertThat(after.getBalance()).isEqualTo(expectedBalance);
	}
}
