package kr.hhplus.be.server.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.ServerApplication;
import kr.hhplus.be.server.application.usecase.ConfirmReservationUseCase;
import kr.hhplus.be.server.application.usecase.HoldSeatUseCase;
import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.port.ReservationPort;
import kr.hhplus.be.server.domain.port.SeatInventoryPort;
import kr.hhplus.be.server.domain.port.WalletPort;
import kr.hhplus.be.server.infrastructure.persistence.entity.MoneyEmbeddable;
import kr.hhplus.be.server.infrastructure.persistence.entity.ReservationEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.SeatStateEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowSeatEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.WalletEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringReservationJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringSeatStateJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowSeatJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringWalletJpa;

/**
 * 기능 흐름 통합 테스트
 * - 유저 토큰 발급(시뮬레이션) → 좌석 홀드 → 결제(확정)
 * - 만료 뒤 재예약 가능
 * - 동시성: 한 명만 성공
 */
@SpringBootTest(classes = ServerApplication.class)
@ActiveProfiles("test")
@EntityScan(basePackages = "kr.hhplus.be.server.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "kr.hhplus.be.server.infrastructure.persistence.springdata")
class ReservationFeatureFlowIntegrationTest {

	@Autowired SeatInventoryPort seatInventory;           // 실제 JPA 어댑터
	@Autowired WalletPort walletPort;                     // 실제 JPA 어댑터
	@Autowired ConfirmReservationUseCase confirmUseCase;  // 유즈케이스

	// DB 확인
	@Autowired SpringSeatStateJpa seatStateJpa;
	@Autowired SpringWalletJpa walletJpa;

	@MockitoBean
	ReservationPort reservationPort;

	private static final long USER_ID = 1001L;
	private static final long USER_ID_B = 1002L;
	private static final long SHOW_ID = 2001L;

	@BeforeEach
	void seed() {
		// 순수 seat_state / wallets 테이블만 정리
		seatStateJpa.deleteAllInBatch();
		walletJpa.deleteAllInBatch();

		// 지갑 (5만원)
		WalletEntity w = new WalletEntity();
		w.id = USER_ID;
		w.balance = new MoneyEmbeddable(50_000L);
		w.updatedAt = LocalDateTime.now();
		walletJpa.save(w);

		WalletEntity w2 = new WalletEntity();
		w2.id = USER_ID_B;
		w2.balance = new MoneyEmbeddable(50_000L);
		w2.updatedAt = LocalDateTime.now();
		walletJpa.save(w2);
	}

	@Test
	@DisplayName("플로우: 토큰 발급(모의) → 좌석 홀드 → 결제 확정 → 잔액 차감 & 좌석 CONFIRMED")
	@Transactional
	void flow_token_hold_confirm_success() {
		// 토큰 발급
		String accessToken = "dummy-token-for-" + USER_ID;
		assertThat(accessToken).isNotBlank();

		// 좌석 홀드
		int seatNo = 11;
		boolean held = seatInventory.tryHold(SHOW_ID, seatNo, /*ttl*/120);
		assertThat(held).isTrue();

		// DB 상태 확인
		SeatStateEntity s = seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, seatNo).orElseThrow();
		assertThat(s.status).isEqualTo("HELD");
		assertThat(s.expiresAt).isAfter(LocalDateTime.now());

		// 결제 완료
		doNothing().when(reservationPort).markConfirmed(anyLong(), anyLong(), anyList());

		confirmUseCase.confirm(USER_ID, SHOW_ID, List.of(seatNo));

		// 좌석이 CONFIRMED 되었는지
		SeatStateEntity after = seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, seatNo).orElseThrow();
		assertThat(after.status).isEqualTo("CONFIRMED");
		assertThat(after.expiresAt).isNull();

		// 잔액 50,000 -> 50,000 - 11,000 = 39,000
		long balance = walletJpa.findById(USER_ID).orElseThrow().balance.amount;
		assertThat(balance).isEqualTo(39_000L);

		// 예약 포트가 호출되었는지(한 번)
		verify(reservationPort, times(1)).markConfirmed(USER_ID, SHOW_ID, List.of(seatNo));
	}

	@Test
	@DisplayName("만료 시간 도래 후 → 좌석 다시 홀드 가능")
	@Transactional
	void hold_again_after_expired() {
		int seatNo = 12;

		// 이미 만료된 HELD 시드
		SeatStateEntity e = new SeatStateEntity();
		e.showId = SHOW_ID;
		e.seatNo = seatNo;
		e.status = "HELD";
		e.expiresAt = LocalDateTime.now().minusMinutes(1);
		seatStateJpa.save(e);

		int ttl = 120; // 2분
		boolean held = seatInventory.tryHold(SHOW_ID, seatNo, ttl);
		assertThat(held).isTrue();

		SeatStateEntity after = seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, seatNo).orElseThrow();
		assertThat(after.status).isEqualTo("HELD");
		assertThat(after.expiresAt).isAfter(LocalDateTime.now()); // 최소 조건

		long secs = java.time.Duration.between(LocalDateTime.now(), after.expiresAt).toSeconds();
		assertThat(secs).isBetween(100L, 150L);
	}

	@Test
	@DisplayName("동시성: 두 유저가 같은 좌석을 동시에 요청하면 한 명만 성공")
	void concurrent_hold_only_one_wins() throws InterruptedException {
		int seatNo = 13;

		// 동시에 시작시키기 위한 래치
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);

		AtomicReference<Boolean> r1 = new AtomicReference<>();
		AtomicReference<Boolean> r2 = new AtomicReference<>();

		Thread t1 = new Thread(() -> {
			ready.countDown();
			await(ready);
			await(go);
			r1.set(seatInventory.tryHold(SHOW_ID, seatNo, 120));
		});
		Thread t2 = new Thread(() -> {
			ready.countDown();
			await(ready);
			await(go);
			r2.set(seatInventory.tryHold(SHOW_ID, seatNo, 120));
		});

		t1.start();
		t2.start();
		// 동시에 출발
		go.countDown();

		t1.join();
		t2.join();

		// true/false 조합이어야 함(정확히 한 명만 성공)
		boolean a = Boolean.TRUE.equals(r1.get());
		boolean b = Boolean.TRUE.equals(r2.get());
		assertThat(a ^ b).as("정확히 한 스레드만 true").isTrue();

		// DB에는 딱 하나의 HELD 레코드가 있어야 함
		SeatStateEntity s = seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, seatNo).orElseThrow();
		assertThat(s.status).isEqualTo("HELD");
		assertThat(s.expiresAt).isAfter(LocalDateTime.now());
	}

	// --- small helper
	private static void await(CountDownLatch latch) {
		try { latch.await(); } catch (InterruptedException ignored) {}
	}
}
