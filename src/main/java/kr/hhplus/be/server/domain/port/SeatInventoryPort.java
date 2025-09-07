package kr.hhplus.be.server.domain.port;

import kr.hhplus.be.server.domain.model.Money;

public interface SeatInventoryPort {
	boolean tryHold(Long scheduleId, Integer seatNo, long ttlSeconds);
	boolean markConfirmed(Long scheduleId, Integer seatNo);
	Money seatPriceOf(Long scheduleId, Integer seatNo);
}
