package kr.hhplus.be.server.infrastructure.persistence.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservations",
	indexes = {
		@Index(name="idx_res_show_status", columnList = "show_id,status"),
		@Index(name="idx_res_expires", columnList = "expires_at")
	})
public class ReservationEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "reservation_id")
	public Long id;

	@Column(name = "user_id", nullable = false)
	public Long userId;

	@Column(name = "show_id", nullable = false)
	public Long showId;

	@Column(name = "status", nullable = false, length = 16)
	public String status; // HELD/CONFIRMED/CANCELLED

	@Column(name = "expires_at", nullable = false)
	public LocalDateTime expiresAt;

	@Embedded
	public MoneyEmbeddable payableAmount;

	@Column(name = "created_at", nullable = false)
	public LocalDateTime createdAt;

	@OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
	public List<ReservationSeatEntity> seats = new ArrayList<>();

	protected ReservationEntity() {}
}

