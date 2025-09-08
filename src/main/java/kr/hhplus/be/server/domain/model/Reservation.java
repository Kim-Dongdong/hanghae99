package kr.hhplus.be.server.domain.model;

import static java.util.Objects.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import kr.hhplus.be.server.domain.model.exceptions.InvalidReservationStateException;
import kr.hhplus.be.server.domain.model.exceptions.NotOwnerException;
import kr.hhplus.be.server.domain.model.exceptions.PriceMismatchException;
import kr.hhplus.be.server.domain.model.exceptions.ReservationExpiredException;

public class Reservation {
	public enum Status { HELD, CONFIRMED, CANCELLED }

	private Long reservationId;          // 식별자 (인프라에서 역주입)
	private final Long userId;
	private final Long showId;
	private final List<Integer> seatNos; // 단순화. 필요시 값객체로 교체
	private Status status;
	private final LocalDateTime expiresAt;
	private Money payableAmount;         // 확정 시 사용 (hold 시점 금액 스냅샷)
	private final LocalDateTime createdAt;

	private Reservation(Long userId, Long showId, List<Integer> seatNos,
		LocalDateTime expiresAt, Money amount, LocalDateTime createdAt) {
		this.userId = requireNonNull(userId);
		this.showId = requireNonNull(showId);
		if (seatNos == null || seatNos.isEmpty()) throw new IllegalArgumentException("seats required");
		this.seatNos = List.copyOf(seatNos);
		this.expiresAt = requireNonNull(expiresAt);
		this.payableAmount = requireNonNull(amount);
		this.createdAt = requireNonNull(createdAt);
		this.status = Status.HELD;
	}

	public static Reservation hold(Long userId, Long showId, List<Integer> seatNos,
		LocalDateTime expiresAt, Money amount) {
		if (expiresAt.isBefore(LocalDateTime.now())) throw new IllegalArgumentException("expiresAt in past");
		if (amount.isNegative()) throw new IllegalArgumentException("amount must be >= 0");
		return new Reservation(userId, showId, seatNos, expiresAt, amount, LocalDateTime.now());
	}

	public void confirm(LocalDateTime now, Money amountToPay, Long callerUserId) {
		assertOwner(callerUserId);
		if (now.isAfter(expiresAt)) throw new ReservationExpiredException(reservationId);
		if (status != Status.HELD) throw new InvalidReservationStateException(status, "confirm");
		if (!this.payableAmount.equalsAmount(amountToPay))
			throw new PriceMismatchException(this.payableAmount, amountToPay);
		this.status = Status.CONFIRMED;
	}

	public void cancel(String reason, Long callerUserId) {
		assertOwner(callerUserId);
		if (status != Status.HELD) throw new InvalidReservationStateException(status, "cancel");
		this.status = Status.CANCELLED;
		// 필요 시 reason을 도메인 이벤트로 발행
	}

	private void assertOwner(Long callerUserId) {
		if (!Objects.equals(this.userId, callerUserId)) throw new NotOwnerException(callerUserId);
	}

	// 데이터베이스에서 조회한 id, status, createdAt 등 이미 확정된 데이터를 기반으로 객체를 생성
	public static Reservation rehydrate(Long id, Long userId, Long showId, List<Integer> seats,
		Status status, LocalDateTime expiresAt, Money amount,
		LocalDateTime createdAt) {
		Reservation r = new Reservation(userId, showId, seats, expiresAt, amount, createdAt);
		r.reservationId = id;
		r.status = status;
		return r;
	}

	// ===== 접근자 (필요한 범위만 노출) =====
	public Long getReservationId() { return reservationId; }
	public Long getUserId() { return userId; }
	public Long getShowId() { return showId; }
	public List<Integer> getSeatNos() { return seatNos; }
	public Status getStatus() { return status; }
	public LocalDateTime getExpiresAt() { return expiresAt; }
	public Money getPayableAmount() { return payableAmount; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void assignId(Long id) { this.reservationId = id; }
}
