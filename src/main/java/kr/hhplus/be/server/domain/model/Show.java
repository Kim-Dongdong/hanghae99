package kr.hhplus.be.server.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Show {
	public enum Status {
		DRAFT,    // 준비중
		OPEN,     // 판매중
		CLOSED,   // 판매 마감
		CANCELLED // 공연 취소
	}

	private final String showId;          // 공연 식별자
	private final String title;           // 공연 제목
	private final LocalDateTime startsAt; // 공연 시작 시각
	private final String venue;           // 공연 장소
	private final int seatCount;          // 전체 좌석 수
	private final LocalDateTime createdAt;

	private final LocalDateTime salesOpenAt;
	private final LocalDateTime salesCloseAt;
	private final int holdMinutes;        // 좌석 HOLD TTL 분 단위
	private final String timezone;

	private Status status;

	public Show(String showId,
		String title,
		LocalDateTime startsAt,
		String venue,
		int seatCount,
		LocalDateTime createdAt,
		LocalDateTime salesOpenAt,
		LocalDateTime salesCloseAt,
		Status status,
		int holdMinutes,
		String timezone) {

		this.showId = Objects.requireNonNull(showId);
		this.title = Objects.requireNonNull(title);
		this.startsAt = Objects.requireNonNull(startsAt);
		this.venue = Objects.requireNonNull(venue);
		this.seatCount = seatCount;
		this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
		this.salesOpenAt = salesOpenAt;
		this.salesCloseAt = salesCloseAt;
		this.status = status != null ? status : Status.DRAFT;
		this.holdMinutes = holdMinutes;
		this.timezone = timezone;
	}

	// === 비즈니스 규칙 ===

	/** 현재 시점에 판매 가능한지 */
	public boolean isSalesOpen(LocalDateTime now) {
		if (status != Status.OPEN) return false;
		if (salesOpenAt != null && now.isBefore(salesOpenAt)) return false;
		if (salesCloseAt != null && now.isAfter(salesCloseAt)) return false;
		return true;
	}

	/** 공연이 이미 종료됐는지 */
	public boolean isEnded(LocalDateTime now) {
		return now.isAfter(startsAt);
	}

	/** 상태 전이 */
	public void openSales(LocalDateTime now) {
		if (status != Status.DRAFT && status != Status.CLOSED) {
			throw new IllegalStateException("Only DRAFT or CLOSED can be reopened.");
		}
		if (salesOpenAt != null && now.isBefore(salesOpenAt)) {
			throw new IllegalStateException("Too early to open sales.");
		}
		this.status = Status.OPEN;
	}

	public void closeSales(LocalDateTime now) {
		if (status != Status.OPEN) {
			throw new IllegalStateException("Only OPEN show can be closed.");
		}
		if (salesCloseAt != null && now.isBefore(salesCloseAt)) {
			throw new IllegalStateException("Cannot close before salesCloseAt.");
		}
		this.status = Status.CLOSED;
	}

	public void cancel() {
		this.status = Status.CANCELLED;
	}
}
