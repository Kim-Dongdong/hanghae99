package kr.hhplus.be.server.infrastructure.persistence.springdata;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.hhplus.be.server.infrastructure.persistence.entity.SeatStateEntity;

public interface SpringSeatStateJpa extends JpaRepository<SeatStateEntity, Long> {
	Optional<SeatStateEntity> findByShowIdAndSeatNo(Long showId, Integer seatNo);
}
