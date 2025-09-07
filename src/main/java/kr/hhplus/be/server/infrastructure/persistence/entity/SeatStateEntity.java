package kr.hhplus.be.server.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
// unique 제약조건 추가로 중복된 요청 처리 방지
@Table(name="seat_state",
	uniqueConstraints = @UniqueConstraint(name="uk_show_seat", columnNames={"show_id","seat_no"}),
	indexes = {
		@Index(name="idx_seat_status", columnList = "status"),
		@Index(name="idx_seat_expires", columnList = "expires_at")
	})
public class SeatStateEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long id;

	@Column(name = "show_id", nullable = false)
	public Long showId;

	@Column(name = "seat_no", nullable = false)
	public Integer seatNo;

	@Column(name = "status", nullable = false, length = 16) // HELD/CONFIRMED
	public String status;

	@Column(name = "expires_at")
	public LocalDateTime expiresAt;

	@Version
	public Long version;

	protected SeatStateEntity() {
	}
}
