package kr.hhplus.be.server.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.domain.model.exceptions.InsufficientBalanceException;

public class WalletTest {
	@Test
	@DisplayName("생성 시 음수 잔액이면 IllegalArgumentException")
	void constructor_negativeBalance() {
		assertThrows(IllegalArgumentException.class,
			() -> new PointWallet(1L, Money.of(-100)));
	}

	@Test
	@DisplayName("charge: 정상 충전 시 잔액 증가")
	void charge_success() {
		PointWallet wallet = new PointWallet(1L, Money.of(1000));

		wallet.charge(Money.of(500), "REQ-1");

		assertEquals(Money.of(1500), wallet.getBalance());
	}

	@Test
	@DisplayName("charge: 동일 requestId로 두 번 호출 시 잔액 증가 방지")
	void charge_idempotent() {
		PointWallet wallet = new PointWallet(1L, Money.of(1000));

		wallet.charge(Money.of(500), "REQ-1");
		wallet.charge(Money.of(500), "REQ-1"); // 동일 requestId

		assertEquals(Money.of(1500), wallet.getBalance()); // 2000 아님
	}

	@Test
	@DisplayName("charge: 음수 금액이면 IllegalArgumentException")
	void charge_negativeAmount() {
		PointWallet wallet = new PointWallet(1L, Money.of(1000));

		assertThrows(IllegalArgumentException.class,
			() -> wallet.charge(Money.of(-1), "REQ-NEG"));
	}

	@Test
	@DisplayName("deduct: 정상 차감 시 잔액 감소")
	void deduct_success() {
		PointWallet wallet = new PointWallet(1L, Money.of(1000));

		wallet.deduct(Money.of(400), "REQ-2");

		assertEquals(Money.of(600), wallet.getBalance());
	}

	@Test
	@DisplayName("deduct: 동일 requestId로 두 번 호출 시 잔액 감소 방지")
	void deduct_idempotent() {
		PointWallet wallet = new PointWallet(1L, Money.of(1000));

		wallet.deduct(Money.of(300), "REQ-3");
		wallet.deduct(Money.of(300), "REQ-3");

		assertEquals(Money.of(700), wallet.getBalance()); // 400 아님
	}

	@Test
	@DisplayName("deduct: 음수 금액이면 IllegalArgumentException")
	void deduct_negativeAmount() {
		PointWallet wallet = new PointWallet(1L, Money.of(1000));

		assertThrows(IllegalArgumentException.class,
			() -> wallet.deduct(Money.of(-10), "REQ-NEG"));
	}

	@Test
	@DisplayName("deduct: 잔액보다 큰 금액 차감 시 InsufficientBalanceException")
	void deduct_insufficientBalance() {
		PointWallet wallet = new PointWallet(1L, Money.of(500));

		assertThrows(InsufficientBalanceException.class,
			() -> wallet.deduct(Money.of(1000), "REQ-4"));
	}

	@Test
	@DisplayName("rehydrate: 주어진 balance로 정상 복원")
	void rehydrate_success() {
		PointWallet wallet = PointWallet.rehydrate(99L, Money.of(777));

		assertEquals(99L, wallet.getUserId());
		assertEquals(Money.of(777), wallet.getBalance());
	}
}
