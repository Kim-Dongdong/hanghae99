package kr.hhplus.be.server.infrastructure.persistence.springdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.hhplus.be.server.infrastructure.persistence.entity.ShowEntity;

public interface SpringShowJpa extends JpaRepository<ShowEntity, Long> {

	@Modifying
	@Query("UPDATE ShowEntity s SET s.status = :status WHERE s.id = :showId")
	void updateStatus(@Param("showId") Long showId, @Param("status") String status);
}
