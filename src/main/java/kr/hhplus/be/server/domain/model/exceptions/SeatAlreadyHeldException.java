package kr.hhplus.be.server.domain.model.exceptions;

public class SeatAlreadyHeldException extends RuntimeException {
  public SeatAlreadyHeldException(Long scheduleId, Integer seatNo) {
    super("Seat already held: schedule=" + scheduleId + ", seat=" + seatNo);
  }
}
