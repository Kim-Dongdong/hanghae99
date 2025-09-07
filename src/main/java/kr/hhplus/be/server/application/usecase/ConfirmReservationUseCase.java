package kr.hhplus.be.server.application.usecase;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.exceptions.SeatStateRaceException;
import kr.hhplus.be.server.domain.port.ReservationRepository;
import kr.hhplus.be.server.domain.port.SeatInventoryPort;
import kr.hhplus.be.server.domain.port.WalletRepository;

@Service
public class ConfirmReservationUseCase {
	private final ReservationRepository reservationRepository;
	private final WalletRepository walletRepository;
	private final SeatInventoryPort seatInventory;

	public ConfirmReservationUseCase(ReservationRepository reservationRepository,
		WalletRepository walletRepository,
		SeatInventoryPort seatInventory) {
		this.reservationRepository = reservationRepository;
		this.walletRepository = walletRepository;
		this.seatInventory = seatInventory;
	}

	@Transactional
	public Result handle(Long reservationId, Long userId, Money amount, String idempotencyKey) {
		Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new IllegalArgumentException("reservation not found"));

		PointWallet wallet = walletRepository.findByUserIdForUpdate(userId);

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
		reservationRepository.save(reservation);
		walletRepository.save(wallet);
		// PointHistory 기록은 adapter에서 처리 예정

		return new Result("OK", wallet.getBalance());
	}

	public record Result(String status, Money walletBalance) {}

}
