package kr.hhplus.be.server.domain.event;

import java.time.LocalDateTime;
import java.util.List;

import kr.hhplus.be.server.domain.model.Money;

public record ReservationConfirmedEvent(
	Long reservationId,
	Long userId,
	Long showId,
	List<Integer> seatNos,
	Money amount,
	LocalDateTime confirmedAt
) {
}
