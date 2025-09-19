package kr.hhplus.be.server.infrastructure.persistence.springdata;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.hhplus.be.server.infrastructure.persistence.entity.ShowSeatEntity;

public interface SpringShowSeatJpa extends JpaRepository<ShowSeatEntity, Long> {
	List<ShowSeatEntity> findByShowIdAndIsActiveTrue(Long showId);
	List<ShowSeatEntity> findAllByShowIdAndSeatNoIn(Long showId, List<Integer> seatNos);
}
