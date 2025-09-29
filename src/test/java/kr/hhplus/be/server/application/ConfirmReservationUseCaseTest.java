package kr.hhplus.be.server.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.hhplus.be.server.application.usecase.ConfirmReservationUseCase;
import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.exceptions.InsufficientBalanceException;
import kr.hhplus.be.server.domain.model.exceptions.ReservationExpiredException;
import kr.hhplus.be.server.domain.port.ReservationPort;
import kr.hhplus.be.server.domain.port.SeatInventoryPort;
import kr.hhplus.be.server.domain.port.WalletPort;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowSeatEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowSeatJpa;

/** 예약 확정 유스케이스 단위 테스트 **/
@ExtendWith(MockitoExtension.class)
public class ConfirmReservationUseCaseTest {

	@Mock
	ReservationPort reservationPort;
	@Mock
	WalletPort walletPort;
	@Mock
	SeatInventoryPort seatInventory;
	@Mock
	SpringShowSeatJpa showSeatJpa;

	private Reservation heldReservation(Long userId, Long showId, List<Integer> seats,
		Money price, LocalDateTime expiresAt) {
		// 과거 expiresAt도 허용하는 재조립 팩토리 사용 (검증 우회)
		return Reservation.rehydrate(
			/*id*/ null,               // 필요 없으면 null/생략
			userId,
			showId,
			seats,
			Reservation.Status.HELD,   // 현재 상태
			expiresAt,                 // 과거도 허용
			price,
			LocalDateTime.now().minusMinutes(10) // createdAt
		);
	}

	@Test
	@DisplayName("예약 확정 성공 시: 지갑 차감 + 좌석 확정 + 상태 CONFIRMED")
	void confirm_success() {
		// given
		ConfirmReservationUseCase sut = new ConfirmReservationUseCase(reservationPort, walletPort, seatInventory, showSeatJpa);

		Long reservationId = 1L;
		Long userId = 11L;
		Long showId = 101L;
		List<Integer> seats = List.of(5);
		Money price = Money.of(15000);
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(3);

		// 예약 진행
		Reservation res = heldReservation(userId, showId, seats, price, expiresAt);

		// 예약 이력 조회 정의
		when(reservationPort.findByIdForUpdate(reservationId)).thenReturn(Optional.of(res));
		// 결제 정보 조회 정의
		when(walletPort.findByUserIdForUpdate(userId)).thenReturn(new PointWallet(userId, Money.of(50000)));
		// 좌석 5번 확정 처리 정의
		when(seatInventory.markConfirmed(showId, 5)).thenReturn(true);

		// when (handle 실행)
		var result = sut.handle(reservationId, userId, Money.of(15000), "idem-001");

		// then
		// 값 검증
		assertEquals("OK", result.status());
		assertEquals(35000, result.walletBalance().asLong());
		assertEquals(Reservation.Status.CONFIRMED, res.getStatus());

		// 호출 검증
		verify(reservationPort).findByIdForUpdate(reservationId);
		verify(walletPort).findByUserIdForUpdate(userId);
		verify(seatInventory).markConfirmed(showId, 5);
		verify(reservationPort).save(res);
		verify(walletPort).save(any(PointWallet.class));
		verifyNoMoreInteractions(reservationPort, walletPort, seatInventory);
	}

	@Test
	@DisplayName("만료된 예약은 확정 불가")
	void confirm_expired() {
		// given
		ConfirmReservationUseCase sut = new ConfirmReservationUseCase(reservationPort, walletPort, seatInventory, showSeatJpa);

		Long reservationId = 1L;
		Long userId = 11L;
		Long showId = 101L;
		Reservation res = heldReservation(userId, showId, List.of(5),
			Money.of(15000), LocalDateTime.now().minusSeconds(1)); // 1초 지정

		when(reservationPort.findByIdForUpdate(reservationId)).thenReturn(Optional.of(res));
		when(walletPort.findByUserIdForUpdate(userId)).thenReturn(new PointWallet(userId, Money.of(50000)));
		when(seatInventory.markConfirmed(any(), anyInt())).thenReturn(true);

		// when & then
		// 예외 검증
		assertThrows(ReservationExpiredException.class,
			() -> sut.handle(reservationId, userId, Money.of(15000), "idem-001"));

		verify(reservationPort).findByIdForUpdate(reservationId);
		verify(walletPort).findByUserIdForUpdate(userId);
		verifyNoMoreInteractions(reservationPort, walletPort, seatInventory);
	}

	@Test
	@DisplayName("지갑 잔액 부족 시 확정 불가")
	void confirm_insufficientBalance() {
		ConfirmReservationUseCase sut = new ConfirmReservationUseCase(reservationPort, walletPort, seatInventory, showSeatJpa);

		Long reservationId = 1L;
		Long userId = 11L;
		Long showId = 101L;
		Reservation res = heldReservation(userId, showId, List.of(5), Money.of(15000), LocalDateTime.now().plusMinutes(1));

		when(reservationPort.findByIdForUpdate(reservationId)).thenReturn(Optional.of(res));
		when(walletPort.findByUserIdForUpdate(userId)).thenReturn(new PointWallet(userId, Money.of(10000)));

		assertThrows(InsufficientBalanceException.class,
			() -> sut.handle(reservationId, userId, Money.of(15000), "idem-001"));
	}
}
