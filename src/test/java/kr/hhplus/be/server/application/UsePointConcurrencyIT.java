package kr.hhplus.be.server.application;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import kr.hhplus.be.server.ServerApplication;
import kr.hhplus.be.server.application.usecase.RechargePointUseCase;
import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.infrastructure.persistence.entity.MoneyEmbeddable;
import kr.hhplus.be.server.infrastructure.persistence.entity.WalletEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringPointHistoryJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringWalletJpa;

@SpringBootTest(classes = ServerApplication.class)
@ActiveProfiles("test")
@EntityScan(basePackages = "kr.hhplus.be.server.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "kr.hhplus.be.server.infrastructure.persistence.springdata")
class UsePointConcurrencyIT {

	@Autowired RechargePointUseCase rechargePointUseCase;
	@Autowired SpringWalletJpa walletJpa;
	@Autowired SpringPointHistoryJpa historyJpa;

	private static final Long USER_ID = 900L;

	@BeforeEach
	void clean() {
		historyJpa.deleteAllInBatch();
		walletJpa.deleteAllInBatch();
	}

	private void seedWallet(long balance) {
		WalletEntity w = new WalletEntity();
		w.id = USER_ID;
		w.balance = new MoneyEmbeddable(balance);
		walletJpa.saveAndFlush(w);
	}

	@Test
	@DisplayName("동일 requestId로 동시 충전 — 멱등하게 정확히 1회만 반영")
	void concurrent_recharge_same_requestId_is_idempotent() throws Exception {
		// given
		long initial = 10_000;
		long amount = 20_000;
		String reqId = "recharge-idem-001";
		int threadCount = 8;

		seedWallet(initial);

		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch doneGate = new CountDownLatch(threadCount);

		List<RechargePointUseCase.Result> results = new ArrayList<>(threadCount);
		AtomicInteger errors = new AtomicInteger();

		for (int i = 0; i < threadCount; i++) {
			pool.submit(() -> {
				try {
					startGate.await(); // 동시에 진입
					var res = rechargePointUseCase.handle(USER_ID, Money.of(amount), reqId);
					synchronized (results) { results.add(res); }
				} catch (Exception e) {
					errors.incrementAndGet();
				} finally {
					doneGate.countDown();
				}
			});
		}

		// when
		startGate.countDown();
		boolean finished = doneGate.await(10, TimeUnit.SECONDS);
		pool.shutdown();

		// then
		assertThat(finished).isTrue();
		assertThat(errors.get()).isZero();

		// 최종 잔액: initial + amount (정확히 1회만 반영)
		WalletEntity after = walletJpa.findById(USER_ID).orElseThrow();
		assertThat(after.balance.amount).isEqualTo(initial + amount);

		// 이력: (userId, requestId) 1건만
		long idemCnt = historyJpa.countByUserIdAndRequestId(USER_ID, reqId);
		assertThat(idemCnt).isEqualTo(1L);

		// 모든 스레드의 Result는 같은 최종 잔액을 바라보거나 그 이전 상태를 반환할 수 있으나,
		// 적어도 하나 이상은 최종 잔액이어야 함
		assertThat(results).isNotEmpty();
		assertThat(results.stream().map(r -> r.walletBalance().asLong()))
			.contains(initial + amount);
	}

	@Test
	@DisplayName("서로 다른 requestId로 동시 충전 — 모든 요청이 합산 반영")
	void concurrent_recharge_distinct_requestIds_all_applied() throws Exception {
		// given
		long initial = 5_000;
		long amount = 3_000;
		int threadCount = 10; // 10건 모두 합산되어야 함
		seedWallet(initial);

		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch doneGate = new CountDownLatch(threadCount);
		AtomicInteger errors = new AtomicInteger();

		for (int i = 0; i < threadCount; i++) {
			final String reqId = "recharge-" + UUID.randomUUID();
			pool.submit(() -> {
				try {
					startGate.await();
					rechargePointUseCase.handle(USER_ID, Money.of(amount), reqId);
				} catch (Exception e) {
					errors.incrementAndGet();
				} finally {
					doneGate.countDown();
				}
			});
		}

		// when
		startGate.countDown();
		boolean finished = doneGate.await(10, TimeUnit.SECONDS);
		pool.shutdown();

		// then
		assertThat(finished).isTrue();
		assertThat(errors.get()).isZero();

		// 최종 잔액: initial + threadCount * amount
		WalletEntity after = walletJpa.findById(USER_ID).orElseThrow();
		assertThat(after.balance.amount).isEqualTo(initial + threadCount * amount);

		// 이력: "RECHARGE"가 정확히 threadCount 건
		long cnt = historyJpa.countByUserIdAndType(USER_ID, "RECHARGE");
		assertThat(cnt).isEqualTo(threadCount);
	}

	@Test
	@DisplayName("혼합: 같은 requestId와 다른 requestId가 동시에 들어와도 멱등+합산 보장")
	void mixed_same_and_distinct_requestIds() throws Exception {
		// given
		long initial = 0;
		long amount = 10_000;
		int sameReqThreads = 5;    // 같은 reqId
		int distinctReqThreads = 4; // 다른 reqId
		String reqId = "recharge-mixed-001";

		seedWallet(initial);

		ExecutorService pool = Executors.newFixedThreadPool(sameReqThreads + distinctReqThreads);
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch doneGate = new CountDownLatch(sameReqThreads + distinctReqThreads);
		AtomicInteger errors = new AtomicInteger();

		// 동일 reqId 그룹
		for (int i = 0; i < sameReqThreads; i++) {
			pool.submit(() -> {
				try {
					startGate.await();
					rechargePointUseCase.handle(USER_ID, Money.of(amount), reqId);
				} catch (Exception e) {
					errors.incrementAndGet();
				} finally {
					doneGate.countDown();
				}
			});
		}
		// 서로 다른 reqId 그룹
		for (int i = 0; i < distinctReqThreads; i++) {
			final String anotherReq = "recharge-mixed-" + UUID.randomUUID();
			pool.submit(() -> {
				try {
					startGate.await();
					rechargePointUseCase.handle(USER_ID, Money.of(amount), anotherReq);
				} catch (Exception e) {
					errors.incrementAndGet();
				} finally {
					doneGate.countDown();
				}
			});
		}

		// when
		startGate.countDown();
		boolean finished = doneGate.await(10, TimeUnit.SECONDS);
		pool.shutdown();

		// then
		assertThat(finished).isTrue();
		assertThat(errors.get()).isZero();

		// 최종 잔액 = 동일 reqId 1회 + distinctReqThreads 회
		long expected = amount * (1 + distinctReqThreads);
		WalletEntity after = walletJpa.findById(USER_ID).orElseThrow();
		assertThat(after.balance.amount).isEqualTo(expected);

		// 동일 (userId, reqId)은 1건, 나머지는 distinctReqThreads 건 → 총 1 + distinctReqThreads
		long totalHistory = historyJpa.countByUserId(USER_ID);
		assertThat(totalHistory).isEqualTo(1 + distinctReqThreads);
		long idemCnt = historyJpa.countByUserIdAndRequestId(USER_ID, reqId);
		assertThat(idemCnt).isEqualTo(1L);
	}
}
