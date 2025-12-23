package kr.hhplus.be.server.infrastructure.kafka;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Kafka로 전송할 예약 확정 이벤트 DTO
 *
 * 도메인 이벤트(ReservationConfirmedEvent)를 Kafka 메시지로 변환한 형태
 * 직렬화/역직렬화가 용이하도록 설계
 */
public class ReservationConfirmedKafkaEvent {

	private Long reservationId;
	private Long userId;
	private Long showId;
	private List<Integer> seatNos;
	private Long amountPaid;  // Money 객체를 Long으로 변환

	@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
	private LocalDateTime confirmedAt;

	@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
	private LocalDateTime publishedAt;  // Kafka 발행 시각

	// 기본 생성자 (Jackson 역직렬화용)
	public ReservationConfirmedKafkaEvent() {}

	public ReservationConfirmedKafkaEvent(
		Long reservationId,
		Long userId,
		Long showId,
		List<Integer> seatNos,
		Long amountPaid,
		LocalDateTime confirmedAt,
		LocalDateTime publishedAt) {

		this.reservationId = reservationId;
		this.userId = userId;
		this.showId = showId;
		this.seatNos = seatNos;
		this.amountPaid = amountPaid;
		this.confirmedAt = confirmedAt;
		this.publishedAt = publishedAt;
	}

	// Getters and Setters
	public Long getReservationId() {
		return reservationId;
	}

	public void setReservationId(Long reservationId) {
		this.reservationId = reservationId;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public Long getShowId() {
		return showId;
	}

	public void setShowId(Long showId) {
		this.showId = showId;
	}

	public List<Integer> getSeatNos() {
		return seatNos;
	}

	public void setSeatNos(List<Integer> seatNos) {
		this.seatNos = seatNos;
	}

	public Long getAmountPaid() {
		return amountPaid;
	}

	public void setAmountPaid(Long amountPaid) {
		this.amountPaid = amountPaid;
	}

	public LocalDateTime getConfirmedAt() {
		return confirmedAt;
	}

	public void setConfirmedAt(LocalDateTime confirmedAt) {
		this.confirmedAt = confirmedAt;
	}

	public LocalDateTime getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(LocalDateTime publishedAt) {
		this.publishedAt = publishedAt;
	}

	@Override
	public String toString() {
		return "ReservationConfirmedKafkaEvent{" +
			"reservationId=" + reservationId +
			", userId=" + userId +
			", showId=" + showId +
			", seatNos=" + seatNos +
			", amountPaid=" + amountPaid +
			", confirmedAt=" + confirmedAt +
			", publishedAt=" + publishedAt +
			'}';
	}
}
