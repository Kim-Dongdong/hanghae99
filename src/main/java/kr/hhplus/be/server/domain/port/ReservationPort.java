package kr.hhplus.be.server.domain.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import kr.hhplus.be.server.domain.model.Reservation;

public interface ReservationPort {
	Optional<Reservation> findById(Long reservationId);
	Optional<Reservation> findByIdForUpdate(Long reservationId);
	Reservation save(Reservation reservation);

	boolean existsByUserIdAndShowIdAndExpiresAtBefore(long userId, long showId, LocalDateTime now);

	void markConfirmed(Long userId, Long showId, List<Integer> seatNos);
}
