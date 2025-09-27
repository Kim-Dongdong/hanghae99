package kr.hhplus.be.server.infrastructure.persistence.springdata;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.hhplus.be.server.infrastructure.persistence.entity.ReservationEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.ReservationSeatEntity;

public interface SpringReservationSeatJpa extends JpaRepository<ReservationEntity, Long> {
	@Query("select rs from ReservationSeatEntity rs where rs.reservation.id = :reservationId")
	List<ReservationSeatEntity> findByReservationId(@Param("reservationId") Long reservationId);
}
