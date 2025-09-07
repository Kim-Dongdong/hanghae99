package kr.hhplus.be.server.domain.port;

import java.util.Optional;

import kr.hhplus.be.server.domain.model.Reservation;

public interface ReservationRepository {
	Optional<Reservation> findById(Long reservationId);
	Optional<Reservation> findByIdForUpdate(Long reservationId);
	Reservation save(Reservation reservation);
}
