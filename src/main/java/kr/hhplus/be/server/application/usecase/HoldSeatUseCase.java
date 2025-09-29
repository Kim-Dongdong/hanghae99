package kr.hhplus.be.server.application.usecase;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.exceptions.SeatAlreadyHeldException;
import kr.hhplus.be.server.domain.port.ReservationPort;
import kr.hhplus.be.server.domain.port.SeatInventoryPort;

@Service
public class HoldSeatUseCase {

	private final SeatInventoryPort seatInventory;
	private final ReservationPort reservationPort;
	private final long holdTtlSeconds;

	public HoldSeatUseCase(SeatInventoryPort seatInventory,
		ReservationPort reservationPort,
		@Value("${reservation.hold-ttl-seconds:120}") long holdTtlSeconds) {
		this.seatInventory = seatInventory;
		this.reservationPort = reservationPort;
		this.holdTtlSeconds = holdTtlSeconds;
	}

	/** 좌석 점유를 시도하고, 성공하면 해당 좌석에 대한 예약 정보를 생성 **/
	public Result handle(Long userId, Long scheduleId, Integer seatNo) {
		// 좌석 점유 시도
		boolean ok = seatInventory.tryHold(scheduleId, seatNo, holdTtlSeconds);
		if (!ok) throw new SeatAlreadyHeldException(scheduleId, seatNo);

		// 점유 성공 시 해당 좌석의 가격을 가져옴
		Money price = seatInventory.seatPriceOf(scheduleId, seatNo);
		LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(holdTtlSeconds);

		// Reservation.hold로 HOLD 상태의 새로운 예약 도메인 객체를 생성
		Reservation res = Reservation.hold(userId, scheduleId, List.of(seatNo), expiresAt, price);
		Reservation saved = reservationPort.save(res); // db 저장
		return new Result(saved.getReservationId(), expiresAt, price);
	}

	// 반환을 위한 record Result 객체
	public record Result(Long reservationId, LocalDateTime expiresAt, Money amount) {}
}
