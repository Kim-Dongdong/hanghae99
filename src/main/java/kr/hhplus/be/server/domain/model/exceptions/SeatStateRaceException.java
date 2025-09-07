package kr.hhplus.be.server.domain.model.exceptions;

public class SeatStateRaceException extends RuntimeException {
  public SeatStateRaceException(Long scheduleId, Integer seatNo) {
    super("Seat state race: schedule=" + scheduleId + ", seat=" + seatNo);
  }
}
