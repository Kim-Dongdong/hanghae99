package kr.hhplus.be.server.infrastructure.persistence.springdata;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.hhplus.be.server.infrastructure.persistence.entity.PointHistoryEntity;

public interface SpringPointHistoryJpa extends JpaRepository<PointHistoryEntity, Long> {
	boolean existsByUserIdAndRequestId(Long userId, String requestId);
}

