package kr.hhplus.be.server.domain.port;

import java.util.List;

public interface DataPlatformPort {

	void sendReservationConfirmed(ReservationConfirmedPayload payload);

	record ReservationConfirmedPayload(
		Long reservationId,
		Long userId,
		Long showId,
		List<Integer> seatNos,
		long amount,
		String confirmedAt // ISO-8601 string (예: 2025-12-09T12:34:56)
	) {
	}
}
