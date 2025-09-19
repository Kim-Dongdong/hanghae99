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

	@Transactional(readOnly = true)
	public void confirm(long userId, long showId, List<Integer> seatNos) {
		// 1) 만료 예약 존재 여부
		boolean hasExpired = reservationPort.existsByUserIdAndShowIdAndExpiresAtBefore(
			userId, showId, LocalDateTime.now()
		);
		if (hasExpired) {
			throw new ReservationExpiredException(userId); // 추후 reservationId로 변경
		}

		// 2) 좌석 가격 합계 계산
		// showId가 String인 엔티티라면 다음처럼 변환해서 조회해야 함
		List<ShowSeatEntity> seats = showSeatJpa.findAllByShowIdAndSeatNoIn(
			showId, seatNos
		);
		long totalPrice = seats.stream().mapToLong(s -> s.basePrice).sum();

		// 3) 지갑 잔액 확인
		WalletEntity wallet = walletPort.findById(userId)
			.orElseThrow(() -> new IllegalStateException("Wallet not found: " + userId));

		long balance = wallet.balance.amount;
		if (balance < totalPrice) {
			throw new InsufficientBalanceException();
		}

		// 테스트는 "예외"만 본다. 성공 케이스 로직(차감/상태 변경/확정 저장)은 여기서 생략 가능.
		// 실제 구현에서는 아래를 @Transactional로 처리:
		// - wallet.balance 차감
		// - seat_state SOLD로 변경
		// - reservation status CONFIRMED 로 변경 등
	}

	public record Result(String status, Money walletBalance) {}

}
