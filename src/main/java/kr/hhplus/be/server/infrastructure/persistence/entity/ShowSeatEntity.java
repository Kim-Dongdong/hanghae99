package kr.hhplus.be.server.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "show_seat",
	uniqueConstraints = @UniqueConstraint(name = "uk_show_seat", columnNames = {"show_id","seat_no"}))
public class ShowSeatEntity {

	@Id
	@Column(name = "show_seat_id")
	public Long showSeatId;

	@Column(name = "show_id", nullable = false)
	public String showId;

	@Column(name = "seat_no", nullable = false)
	public Integer seatNo;

	public String seatLabel;
	public String seatTier;
	public Long basePrice;
	public Boolean isActive;

	protected ShowSeatEntity() {}
}
