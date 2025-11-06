package kr.hhplus.be.server.infrastructure.persistence.springdata;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.hhplus.be.server.infrastructure.persistence.entity.PointHistoryEntity;

public interface SpringPointHistoryJpa extends JpaRepository<PointHistoryEntity, Long> {
	boolean existsByUserIdAndRequestId(Long userId, String requestId);

	long countByUserIdAndRequestId(Long userId, String requestId);

	Optional<PointHistoryEntity> findTopByUserIdAndRequestIdOrderByCreatedAtDesc(Long userId, String requestId);

	long countByUserId(Long userId);

	long countByUserIdAndType(Long userId, String type);
}

