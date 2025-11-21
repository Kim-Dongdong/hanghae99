package kr.hhplus.be.server.domain.model;

import java.util.Objects;

public class Money {

	private final long amount; // KRW 정수 가정

	public Money(long amount) { this.amount = amount; }

	public Money plus(Money other) { return new Money(this.amount + other.amount); }
	public Money minus(Money other) { return new Money(this.amount - other.amount); }
	public boolean isNegative() { return amount < 0; }
	public boolean isLessThan(Money other) { return this.amount < other.amount; }
	public boolean equalsAmount(Money other) { return this.amount == other.amount; }
	public long asLong() { return amount; }

	public Money multiply(int multiplier) {
		return new Money(this.amount * multiplier);
	}

	@Override public String toString() { return "KRW " + amount; }
	@Override public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Money m)) return false;
		return amount == m.amount;
	}
	@Override public int hashCode() { return Objects.hash(amount); }

	public static Money zero() { return new Money(0); }
	public static Money of(long v) { return new Money(v); }
}
