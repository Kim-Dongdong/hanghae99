package kr.hhplus.be.server.infrastructure.persistence.springdata;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.hhplus.be.server.infrastructure.persistence.entity.ReservationEntity;

public interface SpringReservationJpa extends JpaRepository<ReservationEntity, Long> {
	@Query(value = "select * from reservations where reservation_id = :id for update", nativeQuery = true)
	Optional<ReservationEntity> findByIdForUpdate(@Param("id") Long id);
}
