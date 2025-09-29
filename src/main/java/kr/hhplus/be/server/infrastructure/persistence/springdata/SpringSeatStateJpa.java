package kr.hhplus.be.server.infrastructure.persistence.springdata;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.infrastructure.persistence.entity.SeatStateEntity;

public interface SpringSeatStateJpa extends JpaRepository<SeatStateEntity, Long> {
	Optional<SeatStateEntity> findByShowIdAndSeatNo(Long showId, Integer seatNo);

	/// 특정 showId와 seatNo에 해당하는 좌석을 찾아 상태를 HELD로 바꾸고, 만료 시간을 설정
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query("""
           update SeatStateEntity s
              set s.status = 'HELD',
                  s.expiresAt = :expiresAt
            where s.showId = :showId
              and s.seatNo = :seatNo
              and (s.status = 'AVAILABLE' or (s.status = 'HELD' and s.expiresAt < :now))
           """)
	int tryHold(
		@Param("showId") Long showId,
		@Param("seatNo") Integer seatNo,
		@Param("now") LocalDateTime now,
		@Param("expiresAt") LocalDateTime expiresAt
	);

	@Modifying
	@Query("""
        update SeatStateEntity s
           set s.status = 'CONFIRMED', s.expiresAt = null
         where s.showId = :showId
           and s.seatNo = :seatNo
           and s.status = 'HELD'
           and (s.expiresAt is null or s.expiresAt >= :now)
        """)
	int markConfirmed(@Param("showId") Long showId,
		@Param("seatNo") Integer seatNo,
		@Param("now") LocalDateTime now);

	@Modifying
	@Query("""
       update SeatStateEntity s
          set s.status = 'HELD', s.expiresAt = :expires
        where s.showId = :showId and s.seatNo = :seatNo
          and s.status <> 'CONFIRMED'
          and (s.status <> 'HELD' or s.expiresAt < :now)
    """)
	int updateToHoldIfFree(@Param("showId") Long showId,
		@Param("seatNo") Integer seatNo,
		@Param("now") LocalDateTime now,
		@Param("expires") LocalDateTime expires);

	@Modifying
	@Query("""
       update SeatStateEntity s
          set s.status = 'CONFIRMED', s.expiresAt = null
        where s.showId = :showId and s.seatNo = :seatNo
          and s.status = 'HELD'
          and (s.expiresAt is null or s.expiresAt > :now)
    """)
	int confirmIfHeld(@Param("showId") Long showId,
		@Param("seatNo") Integer seatNo,
		@Param("now") LocalDateTime now);
}
