package kr.hhplus.be.server.infrastructure.persistence.springdata;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.hhplus.be.server.infrastructure.persistence.entity.ReservationEntity;

public interface SpringReservationJpa extends JpaRepository<ReservationEntity, Long> {
	@Query(value = "select * from reservations where reservation_id = :id for update", nativeQuery = true)
	Optional<ReservationEntity> findByIdForUpdate(@Param("id") Long id);

	boolean existsByUserIdAndShowIdAndExpiresAtBefore(long userId, long showId, LocalDateTime now);

	Optional<ReservationEntity> findTopByUserIdAndShowIdAndStatusOrderByCreatedAtDesc(
		Long userId, Long showId, String status
	);
}
