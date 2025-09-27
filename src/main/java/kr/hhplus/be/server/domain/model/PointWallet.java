package kr.hhplus.be.server.domain.model;

import static java.util.Objects.*;

import java.util.HashSet;
import java.util.Set;

import kr.hhplus.be.server.domain.model.exceptions.InsufficientBalanceException;

public class PointWallet {
	private final Long userId;
	private Money balance;
	private final Set<String> processedRequestIds = new HashSet<>(); // 간단 멱등 추적(인프라에선 히스토리로 검증)

	public PointWallet(Long userId, Money initial) {
		this.userId = requireNonNull(userId);
		this.balance = requireNonNull(initial);
		if (balance.isNegative()) throw new IllegalArgumentException("negative balance");
	}

	public void charge(Money amount, String requestId) {
		requireNonNull(amount);
		requireNonNull(requestId);
		if (processedRequestIds.contains(requestId)) return; // 멱등
		if (amount.isNegative()) throw new IllegalArgumentException("amount negative");
		this.balance = this.balance.plus(amount);
		processedRequestIds.add(requestId);
	}

	public void deduct(Money amount, String requestId) {
		requireNonNull(amount);
		requireNonNull(requestId);
		if (processedRequestIds.contains(requestId)) return; // 멱등
		if (amount.isNegative()) throw new IllegalArgumentException("amount negative");
		if (this.balance.isLessThan(amount)) throw new InsufficientBalanceException();
		this.balance = this.balance.minus(amount);
		processedRequestIds.add(requestId);
	}

	public void recharge(Money amount) {
		if (amount == null) throw new IllegalArgumentException("amount is null");
		long delta = amount.asLong();
		if (delta <= 0) throw new IllegalArgumentException("recharge amount must be positive");
		this.balance = Money.of(this.balance.asLong() + delta);
	}

	public Long getUserId() { return userId; }
	public Money getBalance() { return balance; }

	// 재조립(인프라)
	public static PointWallet rehydrate(Long userId, Money balance) {
		return new PointWallet(userId, balance);
	}
}

