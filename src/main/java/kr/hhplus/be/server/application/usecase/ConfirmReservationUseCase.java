package kr.hhplus.be.server.application.usecase;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.exceptions.InsufficientBalanceException;
import kr.hhplus.be.server.domain.model.exceptions.ReservationExpiredException;
import kr.hhplus.be.server.domain.model.exceptions.SeatStateRaceException;
import kr.hhplus.be.server.domain.port.ReservationPort;
import kr.hhplus.be.server.domain.port.SeatInventoryPort;
import kr.hhplus.be.server.domain.port.WalletPort;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowSeatEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.WalletEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowSeatJpa;

@Service
public class ConfirmReservationUseCase {
	private final ReservationPort reservationPort;
	private final WalletPort walletPort;
	private final SeatInventoryPort seatInventory;
	private final SpringShowSeatJpa  showSeatJpa;

	public ConfirmReservationUseCase(ReservationPort reservationPort,
		WalletPort walletPort,
		SeatInventoryPort seatInventory,
		SpringShowSeatJpa showSeatJpa) {
		this.reservationPort = reservationPort;
		this.walletPort = walletPort;
		this.seatInventory = seatInventory;
		this.showSeatJpa = showSeatJpa;
	}

	@Transactional
	public Result handle(Long reservationId, Long userId, Money amount, String idempotencyKey) {
		Reservation reservation = reservationPort.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new IllegalArgumentException("reservation not found"));

		PointWallet wallet = walletPort.findByUserIdForUpdate(userId);

		// 지갑 차감
		wallet.deduct(amount, idempotencyKey);

		// 좌석 확정
		for (Integer seatNo : reservation.getSeatNos()) {
			boolean ok = seatInventory.markConfirmed(reservation.getShowId(), seatNo);
			if (!ok) throw new SeatStateRaceException(reservation.getShowId(), seatNo);
		}

		// 예약 확정
		reservation.confirm(LocalDateTime.now(), amount, userId);

		// 저장
		reservationPort.save(reservation);
		walletPort.save(wallet);
		// PointHistory 기록은 adapter에서 처리 예정

		return new Result("OK", wallet.getBalance());
	}

	@Transactional
	public void confirm(Long userId, Long showId, List<Integer> seatNos) {

		// 금액 계산
		Money total = seatNos.stream()
			.map(no -> seatInventory.seatPriceOf(showId, no))
			.reduce(Money.zero(), Money::plus);

		// 지갑 비관락 조회
		PointWallet wallet = walletPort.findByUserIdForUpdate(userId);

		// 잔액 검증
		if (wallet.getBalance().isLessThan(total)) {
			throw new InsufficientBalanceException();
		}

		// 좌석 확정 (HELD → CONFIRMED).
		boolean ok = seatNos.stream()
			.allMatch(no -> seatInventory.markConfirmed(showId, no));
		if (!ok) {
			throw new ReservationExpiredException(reservationPort.findByIdForUpdate(showId).get().getReservationId());
		}

		// 잔액 차감 & 저장
		wallet = wallet.debit(total);
		walletPort.save(wallet);

		// 예약 포트(도메인 기록/이벤트 등)는 마지막에 호출
		reservationPort.markConfirmed(userId, showId, seatNos);
	}

	public record Result(String status, Money walletBalance) {}

}
