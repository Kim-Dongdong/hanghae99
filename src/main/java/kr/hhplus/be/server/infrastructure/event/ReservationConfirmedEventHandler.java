package kr.hhplus.be.server.infrastructure.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import kr.hhplus.be.server.domain.event.ReservationConfirmedEvent;
import kr.hhplus.be.server.domain.port.DataPlatformPort;

@Component
public class ReservationConfirmedEventHandler {

	private final DataPlatformPort dataPlatformPort;

	public ReservationConfirmedEventHandler(DataPlatformPort dataPlatformPort) {
		this.dataPlatformPort = dataPlatformPort;
	}

	/**
	 * 예약 확정 트랜잭션이 커밋된 이후(AFTER_COMMIT)에만 동작
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onReservationConfirmed(ReservationConfirmedEvent event) {
		var payload = new DataPlatformPort.ReservationConfirmedPayload(
			event.reservationId(),
			event.userId(),
			event.showId(),
			event.seatNos(),
			event.amount().asLong(),
			event.confirmedAt().toString()
		);

		dataPlatformPort.sendReservationConfirmed(payload);
	}
}
