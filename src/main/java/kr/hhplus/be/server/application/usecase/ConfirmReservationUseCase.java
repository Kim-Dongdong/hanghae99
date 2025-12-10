package kr.hhplus.be.server.application.usecase;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.event.ReservationConfirmedEvent;
import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.exceptions.InsufficientBalanceException;
import kr.hhplus.be.server.domain.model.exceptions.ReservationExpiredException;
import kr.hhplus.be.server.domain.model.exceptions.SeatStateRaceException;
import kr.hhplus.be.server.domain.port.ReservationPort;
import kr.hhplus.be.server.domain.port.SeatInventoryPort;
import kr.hhplus.be.server.domain.port.WalletPort;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowSeatJpa;

@Service
public class ConfirmReservationUseCase {

	private final ReservationPort reservationPort;
	private final WalletPort walletPort;
	private final SeatInventoryPort seatInventory;
	private final SpringShowSeatJpa showSeatJpa;
	private final ApplicationEventPublisher eventPublisher;

	public ConfirmReservationUseCase(ReservationPort reservationPort,
		WalletPort walletPort,
		SeatInventoryPort seatInventory,
		SpringShowSeatJpa showSeatJpa,
		ApplicationEventPublisher eventPublisher) {

		this.reservationPort = reservationPort;
		this.walletPort = walletPort;
		this.seatInventory = seatInventory;
		this.showSeatJpa = showSeatJpa;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * 예약 ID 기반으로 확정 (지금 ReservationController에서 사용하는 버전)
	 */
	@Transactional
	public Result handle(Long reservationId, Long userId, Money amount, String idempotencyKey) {
		// 1) 예약 & 지갑 조회 (비관락)
		Reservation reservation = reservationPort.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new IllegalArgumentException("reservation not found"));

		PointWallet wallet = walletPort.findByUserIdForUpdate(userId);

		// 2) 지갑 차감
		wallet.deduct(amount, idempotencyKey);

		// 3) 좌석 확정
		for (Integer seatNo : reservation.getSeatNos()) {
			boolean ok = seatInventory.markConfirmed(reservation.getShowId(), seatNo);
			if (!ok) {
				throw new SeatStateRaceException(reservation.getShowId(), seatNo);
			}
		}

		// 4) 예약 도메인 상태 변경(HELD -> CONFIRMED)
		LocalDateTime now = LocalDateTime.now();
		reservation.confirm(now, amount, userId);

		// 5) 저장
		reservationPort.save(reservation);
		walletPort.save(wallet);
		// PointHistory 기록은 adapter에서 처리 예정

		// 6) 도메인 이벤트 발행 (트랜잭션 안에서는 "사실"만 던짐)
		var event = new ReservationConfirmedEvent(
			reservation.getReservationId(),
			reservation.getUserId(),
			reservation.getShowId(),
			reservation.getSeatNos(),
			amount,
			now
		);
		eventPublisher.publishEvent(event);

		return new Result("OK", wallet.getBalance());
	}

	/**
	 * (기존 두번째 confirm 메서드, 필요하다면 그대로 유지)
	 * userId, showId, 좌석 리스트 기반으로 바로 확정하는 버전
	 */
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
			throw new ReservationExpiredException(
				reservationPort.findByIdForUpdate(showId).get().getReservationId()
			);
		}

		// 잔액 차감 & 저장
		wallet = wallet.debit(total);
		walletPort.save(wallet);

		// 예약 포트(도메인 기록/이벤트 등)는 마지막에 호출
		reservationPort.markConfirmed(userId, showId, seatNos);

		// 이 confirm(...) 버전에서도 이벤트를 발행하고 싶다면
		// 여기서도 ReservationConfirmedEvent 를 만들어서 publishEvent 해주면 됨
	}

	public record Result(String status, Money walletBalance) {}
}
