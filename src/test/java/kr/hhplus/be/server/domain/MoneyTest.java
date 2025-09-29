package kr.hhplus.be.server.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.hhplus.be.server.domain.model.Money;

public class MoneyTest {
	@Test
	@DisplayName("plus: 두 금액을 더한 Money 반환")
	void plus_success() {
		Money m1 = Money.of(1000);
		Money m2 = Money.of(500);

		Money result = m1.plus(m2);

		assertEquals(Money.of(1500), result);
		assertEquals(1500, result.asLong());
	}

	@Test
	@DisplayName("minus: 두 금액을 뺀 Money 반환 (음수 가능)")
	void minus_success() {
		Money m1 = Money.of(1000);
		Money m2 = Money.of(1500);

		Money result = m1.minus(m2);

		assertEquals(Money.of(-500), result);
		assertTrue(result.isNegative());
	}

	@Test
	@DisplayName("isNegative: 음수/양수/0 판별")
	void isNegative() {
		assertTrue(Money.of(-1).isNegative());
		assertFalse(Money.zero().isNegative());
		assertFalse(Money.of(100).isNegative());
	}

	@Test
	@DisplayName("isLessThan: 비교 기능")
	void isLessThan() {
		Money small = Money.of(100);
		Money big = Money.of(200);

		assertTrue(small.isLessThan(big));
		assertFalse(big.isLessThan(small));
		assertFalse(small.isLessThan(Money.of(100))); // 같으면 false
	}

	@Test
	@DisplayName("equalsAmount: 값이 같으면 true")
	void equalsAmount() {
		Money m1 = Money.of(500);
		Money m2 = Money.of(500);
		Money m3 = Money.of(1000);

		assertTrue(m1.equalsAmount(m2));
		assertFalse(m1.equalsAmount(m3));
	}

	@Test
	@DisplayName("equals/hashCode: amount가 같으면 동일")
	void equalsAndHashCode() {
		Money a = Money.of(1234);
		Money b = Money.of(1234);

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}
}
