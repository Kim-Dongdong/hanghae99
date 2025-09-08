package kr.hhplus.be.server.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shows")
public class ShowEntity {
	@Id
	@Column(name = "show_id")
	public Long showId;

	public String title;
	public LocalDateTime startsAt;
	public String venue;
	public Integer seatCount;
	public LocalDateTime createdAt;

	public LocalDateTime salesOpenAt;
	public LocalDateTime salesCloseAt;
	public String status;
	public Integer holdMinutes;
	public String timezone;

	protected ShowEntity() {}
}
