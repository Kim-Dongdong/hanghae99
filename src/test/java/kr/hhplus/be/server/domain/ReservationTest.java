package kr.hhplus.be.server.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.exceptions.InvalidReservationStateException;
import kr.hhplus.be.server.domain.model.exceptions.NotOwnerException;
import kr.hhplus.be.server.domain.model.exceptions.PriceMismatchException;
import kr.hhplus.be.server.domain.model.exceptions.ReservationExpiredException;

public class ReservationTest {

	// Money Mock 객체 생성
	private Money money(boolean negative) {
		Money m = Mockito.mock(Money.class);
		when(m.isNegative()).thenReturn(negative);
		// equalsAmount 는 호출 시점의 인자로 테스트 케이스별로 오버라이드 함
		when(m.equalsAmount(any())).thenAnswer(inv -> {
			Money other = inv.getArgument(0);
			// 동일 mock 인스턴스면 true 로 간주
			return other == m;
		});
		return m;
	}

	@Test
	@DisplayName("hold: 정상 보류 생성")
	void hold_success() {
		Long userId = 1L;
		Long showId = 10L;
		List<Integer> seats = List.of(1, 2, 3);
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
		Money amount = money(false);

		Reservation r = Reservation.hold(userId, showId, seats, expiresAt, amount);

		assertNull(r.getReservationId());
		assertEquals(userId, r.getUserId());
		assertEquals(showId, r.getShowId());
		assertEquals(seats, r.getSeatNos());
		assertEquals(Reservation.Status.HELD, r.getStatus());
		assertEquals(expiresAt, r.getExpiresAt());
		assertEquals(amount, r.getPayableAmount());
		assertNotNull(r.getCreatedAt());
	}

	@Test
	@DisplayName("hold: 만료시각이 과거면 IllegalArgumentException")
	void hold_expiresInPast_throws() {
		LocalDateTime past = LocalDateTime.now().minusSeconds(1); // minus로 과거 지정
		Money amount = money(false);

		assertThrows(IllegalArgumentException.class, () ->
			Reservation.hold(1L, 10L, List.of(1), past, amount)
		);
	}

	@Test
	@DisplayName("hold: 금액이 음수면 IllegalArgumentException")
	void hold_negativeAmount_throws() {
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(1);
		Money negative = money(true);

		assertThrows(IllegalArgumentException.class, () ->
			Reservation.hold(1L, 10L, List.of(1), expiresAt, negative)
		);
	}

	@Test
	@DisplayName("hold: 좌석이 없으면 IllegalArgumentException")
	void hold_emptySeats_throws() {
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(1);
		Money amount = money(false);

		assertThrows(IllegalArgumentException.class, () ->
			Reservation.hold(1L, 10L, List.of(), expiresAt, amount)
		);
	}

	@Test
	@DisplayName("confirm: 정상 확정")
	void confirm_success() {
		Long owner = 1L;
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

		Money snapshot = money(false);
		// 지급 금액이 스냅샷과 동일하다고 판단되도록
		when(snapshot.equalsAmount(snapshot)).thenReturn(true);

		Reservation r = Reservation.hold(owner, 10L, List.of(5, 6), expiresAt, snapshot);

		// amountToPay 로 같은 인스턴스 전달 => equalsAmount true
		r.confirm(LocalDateTime.now(), snapshot, owner);

		assertEquals(Reservation.Status.CONFIRMED, r.getStatus());
	}

	@Test
	@DisplayName("confirm: 소유자 아님 -> NotOwnerException")
	void confirm_notOwner_throws() {
		Long owner = 1L;
		Long stranger = 99L;
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(1);
		Money snapshot = money(false);
		when(snapshot.equalsAmount(snapshot)).thenReturn(true);

		Reservation r = Reservation.hold(owner, 10L, List.of(1), expiresAt, snapshot);

		assertThrows(NotOwnerException.class, () ->
			r.confirm(LocalDateTime.now(), snapshot, stranger)
		);
	}

	@Test
	@DisplayName("confirm: 만료 후 시도 -> ReservationExpiredException")
	void confirm_expired_throws() {
		Long owner = 1L;
		LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(1);
		Money snapshot = money(false);
		when(snapshot.equalsAmount(snapshot)).thenReturn(true);

		Reservation r = Reservation.hold(owner, 10L, List.of(1), expiresAt, snapshot);

		// 만료되도록 잠시 대기하지 않고 now 를 미래로 설정
		LocalDateTime afterExpiry = expiresAt.plusSeconds(1);

		assertThrows(ReservationExpiredException.class, () ->
			r.confirm(afterExpiry, snapshot, owner)
		);
	}

	@Test
	@DisplayName("confirm: 상태가 HELD 가 아니면 InvalidReservationStateException")
	void confirm_wrongState_throws() {
		Long owner = 1L;
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(2);
		Money snapshot = money(false);

		Reservation r = Reservation.hold(owner, 10L, List.of(1), expiresAt, snapshot);
		// 먼저 취소해서 상태를 CANCELLED 로 만듦
		r.cancel("user_cancel", owner);

		assertThrows(InvalidReservationStateException.class, () ->
			r.confirm(LocalDateTime.now(), snapshot, owner)
		);
	}

	@Test
	@DisplayName("confirm: 결제 금액 불일치 -> PriceMismatchException")
	void confirm_priceMismatch_throws() {
		Long owner = 1L;
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(2);

		Money snapshot = money(false);
		Money amountToPay = mock(Money.class);

		// 스냅샷 기준 equalsAmount(amountToPay) 가 false 가 되도록
		when(snapshot.equalsAmount(amountToPay)).thenReturn(false);

		Reservation r = Reservation.hold(owner, 10L, List.of(1), expiresAt, snapshot);

		assertThrows(PriceMismatchException.class, () ->
			r.confirm(LocalDateTime.now(), amountToPay, owner)
		);
	}

	@Test
	@DisplayName("cancel: 정상 취소")
	void cancel_success() {
		Long owner = 1L;
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(2);
		Money snapshot = money(false);

		Reservation r = Reservation.hold(owner, 10L, List.of(1, 2), expiresAt, snapshot);

		r.cancel("user_change_mind", owner);

		assertEquals(Reservation.Status.CANCELLED, r.getStatus());
	}

	@Test
	@DisplayName("cancel: 소유자 아님 -> NotOwnerException")
	void cancel_notOwner_throws() {
		Long owner = 1L;
		Long stranger = 2L;
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(2);
		Money snapshot = money(false);

		Reservation r = Reservation.hold(owner, 10L, List.of(1), expiresAt, snapshot);

		assertThrows(NotOwnerException.class, () ->
			r.cancel("nope", stranger)
		);
	}

	@Test
	@DisplayName("cancel: 상태가 HELD 가 아니면 InvalidReservationStateException")
	void cancel_wrongState_throws() {
		Long owner = 1L;
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(2);
		Money snapshot = money(false);

		Reservation r = Reservation.hold(owner, 10L, List.of(1), expiresAt, snapshot);
		// 확정해서 상태를 CONFIRMED 로
		when(snapshot.equalsAmount(snapshot)).thenReturn(true);
		r.confirm(LocalDateTime.now(), snapshot, owner);

		assertThrows(InvalidReservationStateException.class, () ->
			r.cancel("late_cancel", owner)
		);
	}

}
