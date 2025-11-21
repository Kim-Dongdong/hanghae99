package kr.hhplus.be.server.application;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import kr.hhplus.be.server.domain.port.SeatInventoryPort;
import kr.hhplus.be.server.infrastructure.persistence.adapter.SeatInventoryJpaAdapter;
import kr.hhplus.be.server.infrastructure.persistence.entity.SeatStateEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringSeatStateJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowSeatJpa;

/**
 * 좌석 홀드 동시성 제어 테스트
 * - 조건부 UPDATE 쿼리를 통한 동시성 제어 검증
 */
@DataJpaTest
@ActiveProfiles("test")
@EntityScan(basePackages = "kr.hhplus.be.server.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "kr.hhplus.be.server.infrastructure.persistence.springdata")
@Import(SeatInventoryJpaAdapter.class)
public class HoldSeatConcurrencyIT {

	@Autowired SeatInventoryPort seatInventory;
	@Autowired SpringSeatStateJpa seatStateJpa;
	@Autowired SpringShowSeatJpa showSeatJpa;

	private static final long SHOW_ID = 200L;
	private static final int SEAT_NO = 20;
	private static final long TTL_SECONDS = 120L;

	@BeforeEach
	void setUp() {
		seatStateJpa.deleteAllInBatch();
	}

	@Test
	@DisplayName("동일 좌석에 대한 동시 홀드 요청 - 1명만 성공해야 함")
	void concurrent_hold_same_seat_only_one_should_succeed() throws InterruptedException {
		// given
		int threadCount = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);

		// 모든 쓰레드가 준비될 때까지 대기 → 한 번에 출발시키기 위한 게이트
		CountDownLatch startGate = new CountDownLatch(1);
		// 모든 작업 종료를 기다리기 위한 게이트
		CountDownLatch doneLatch = new CountDownLatch(threadCount);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failCount = new AtomicInteger(0);

		// when: 10개의 스레드가 동시에 같은 좌석을 홀드 시도
		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					// startGate 열리면 동시에 진입
					startGate.await();

					boolean result = seatInventory.tryHold(SHOW_ID, SEAT_NO, TTL_SECONDS);
					if (result) {
						successCount.incrementAndGet();
					} else {
						failCount.incrementAndGet();
					}
				} catch (Exception e) { // 유니크 충돌 등 예외도 실패로 집계
					failCount.incrementAndGet();
				} finally {
					doneLatch.countDown();
				}
			});
		}

		// 모든 작업이 큐에 올라간 뒤 동시에 출발
		startGate.countDown();

		// 모든 쓰레드 종료 대기
		boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
		executor.shutdown();

		// then
		assertThat(finished).as("작업이 시간 내에 끝나야 함").isTrue();
		assertThat(successCount.get()).isEqualTo(1);
		assertThat(failCount.get()).isEqualTo(threadCount - 1);

		// DB 상태 검증
		SeatStateEntity state = seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, SEAT_NO).orElseThrow();
		assertThat(state.status).isEqualTo("HELD");
		assertThat(state.expiresAt).isAfter(LocalDateTime.now());
	}


	@Test
	@DisplayName("서로 다른 좌석에 대한 동시 홀드 요청 - 모두 성공해야 함")
	void concurrent_hold_different_seats_all_should_succeed() throws InterruptedException {
		// given
		int threadCount = 5;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);
		AtomicInteger successCount = new AtomicInteger(0);

		// when: 각 스레드가 서로 다른 좌석을 홀드 시도
		for (int i = 0; i < threadCount; i++) {
			final int seatNo = 30 + i;
			executor.submit(() -> {
				try {
					boolean result = seatInventory.tryHold(SHOW_ID, seatNo, TTL_SECONDS);
					if (result) {
						successCount.incrementAndGet();
					}
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await(10, TimeUnit.SECONDS);
		executor.shutdown();

		// then: 모든 요청이 성공해야 함
		assertThat(successCount.get()).isEqualTo(threadCount);

		// 각 좌석의 상태 검증
		for (int i = 0; i < threadCount; i++) {
			int seatNo = 30 + i;
			SeatStateEntity state = seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, seatNo).orElseThrow();
			assertThat(state.status).isEqualTo("HELD");
		}
	}

	@Test
	@DisplayName("홀드와 확정이 동시에 발생할 때 - 데이터 정합성 보장")
	void concurrent_hold_and_confirm_data_consistency() throws InterruptedException {
		// given
		int holdThreads = 5;
		int confirmThreads = 3;
		int totalThreads = holdThreads + confirmThreads;

		ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
		CountDownLatch latch = new CountDownLatch(totalThreads);
		AtomicInteger holdSuccess = new AtomicInteger(0);
		AtomicInteger confirmSuccess = new AtomicInteger(0);

		// when: 홀드와 확정이 동시에 시도됨
		// 홀드 시도
		for (int i = 0; i < holdThreads; i++) {
			executor.submit(() -> {
				try {
					if (seatInventory.tryHold(SHOW_ID, SEAT_NO, TTL_SECONDS)) {
						holdSuccess.incrementAndGet();
					}
				} finally {
					latch.countDown();
				}
			});
		}

		// 확정 시도 (홀드가 성공한 경우에만 확정 가능)
		for (int i = 0; i < confirmThreads; i++) {
			executor.submit(() -> {
				try {
					// 약간의 지연을 주어 홀드가 먼저 일어날 확률 높임
					Thread.sleep(10);
					if (seatInventory.markConfirmed(SHOW_ID, SEAT_NO)) {
						confirmSuccess.incrementAndGet();
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await(10, TimeUnit.SECONDS);
		executor.shutdown();

		// then: 홀드는 최대 1명 성공, 확정도 최대 1명 성공
		assertThat(holdSuccess.get()).isLessThanOrEqualTo(1);
		assertThat(confirmSuccess.get()).isLessThanOrEqualTo(1);

		// 최종 상태 검증
		SeatStateEntity finalState = seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, SEAT_NO).orElse(null);
		if (finalState != null) {
			// HELD 또는 CONFIRMED 중 하나의 일관된 상태여야 함
			assertThat(finalState.status).isIn("HELD", "CONFIRMED");

			if ("CONFIRMED".equals(finalState.status)) {
				assertThat(finalState.expiresAt).isNull();
			} else {
				assertThat(finalState.expiresAt).isNotNull();
			}
		}
	}

	@Test
	@DisplayName("대량 동시 요청 시 데이터 일관성 검증")
	void massive_concurrent_requests_data_consistency() throws InterruptedException {
		// given
		int seatCount = 10;
		int requestsPerSeat = 20;
		int totalThreads = seatCount * requestsPerSeat;

		ExecutorService executor = Executors.newFixedThreadPool(50);
		CountDownLatch latch = new CountDownLatch(totalThreads);
		List<Boolean> results = new ArrayList<>();

		// when: 10개 좌석에 각각 20개의 동시 요청
		for (int seatNo = 40; seatNo < 40 + seatCount; seatNo++) {
			final int seat = seatNo;
			for (int j = 0; j < requestsPerSeat; j++) {
				executor.submit(() -> {
					try {
						boolean result = seatInventory.tryHold(SHOW_ID, seat, TTL_SECONDS);
						synchronized (results) {
							results.add(result);
						}
					} finally {
						latch.countDown();
					}
				});
			}
		}

		latch.await(15, TimeUnit.SECONDS);
		executor.shutdown();

		// then: 각 좌석당 정확히 1개의 성공만 있어야 함
		long totalSuccess = results.stream().filter(r -> r).count();
		assertThat(totalSuccess).isEqualTo(seatCount);

		// 각 좌석이 HELD 상태인지 확인
		for (int seatNo = 40; seatNo < 40 + seatCount; seatNo++) {
			SeatStateEntity state = seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, seatNo).orElseThrow();
			assertThat(state.status).isEqualTo("HELD");
			assertThat(state.expiresAt).isAfter(LocalDateTime.now());
		}
	}

	@Test
	@DisplayName("CONFIRMED 상태 좌석에 대한 동시 홀드 시도 - 모두 실패해야 함")
	void concurrent_hold_on_confirmed_seat_all_should_fail() throws InterruptedException {
		// given: CONFIRMED 상태의 좌석 생성
		SeatStateEntity confirmedSeat = new SeatStateEntity();
		confirmedSeat.showId = SHOW_ID;
		confirmedSeat.seatNo = SEAT_NO;
		confirmedSeat.status = "CONFIRMED";
		confirmedSeat.expiresAt = null;
		seatStateJpa.save(confirmedSeat);

		int threadCount = 15;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);
		AtomicInteger failCount = new AtomicInteger(0);

		// when: 15개의 스레드가 동시에 확정된 좌석을 홀드 시도
		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					boolean result = seatInventory.tryHold(SHOW_ID, SEAT_NO, TTL_SECONDS);
					if (!result) {
						failCount.incrementAndGet();
					}
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await(10, TimeUnit.SECONDS);
		executor.shutdown();

		// then: 모든 요청이 실패해야 함
		assertThat(failCount.get()).isEqualTo(threadCount);

		// 좌석 상태가 여전히 CONFIRMED인지 확인
		SeatStateEntity state = seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, SEAT_NO).orElseThrow();
		assertThat(state.status).isEqualTo("CONFIRMED");
		assertThat(state.expiresAt).isNull();
	}
}
