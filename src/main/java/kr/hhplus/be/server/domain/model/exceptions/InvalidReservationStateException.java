package kr.hhplus.be.server.domain.model.exceptions;

import kr.hhplus.be.server.domain.model.Reservation;

public class InvalidReservationStateException extends RuntimeException {
	public InvalidReservationStateException(Reservation.Status state, String op) {
		super("Invalid state " + state + " for op " + op);
	}}
