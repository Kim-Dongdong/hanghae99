package kr.hhplus.be.server.domain.model.exceptions;

public class ReservationExpiredException extends RuntimeException {
	public ReservationExpiredException(Long id) { super("Reservation expired: " + id); }
}
