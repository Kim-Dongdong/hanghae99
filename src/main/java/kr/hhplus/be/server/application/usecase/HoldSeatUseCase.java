package kr.hhplus.be.server.application.usecase;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.exceptions.SeatAlreadyHeldException;
import kr.hhplus.be.server.domain.port.ReservationRepository;
import kr.hhplus.be.server.domain.port.SeatInventoryPort;

@Service
public class HoldSeatUseCase {

	private final SeatInventoryPort seatInventory;
	private final ReservationRepository reservationRepository;
	private final long holdTtlSeconds;

	public HoldSeatUseCase(SeatInventoryPort seatInventory,
		ReservationRepository reservationRepository,
		long holdTtlSeconds) {
		this.seatInventory = seatInventory;
		this.reservationRepository = reservationRepository;
		this.holdTtlSeconds = holdTtlSeconds;
	}

	public Result handle(Long userId, Long scheduleId, Integer seatNo) {
		boolean ok = seatInventory.tryHold(scheduleId, seatNo, holdTtlSeconds);
		if (!ok) throw new SeatAlreadyHeldException(scheduleId, seatNo);

		Money price = seatInventory.seatPriceOf(scheduleId, seatNo);
		LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(holdTtlSeconds);

		Reservation res = Reservation.hold(userId, scheduleId, List.of(seatNo), expiresAt, price);
		Reservation saved = reservationRepository.save(res);
		return new Result(saved.getReservationId(), expiresAt, price);
	}

	public record Result(Long reservationId, LocalDateTime expiresAt, Money amount) {}
}
