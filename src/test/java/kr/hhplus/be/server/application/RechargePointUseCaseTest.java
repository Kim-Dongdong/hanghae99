package kr.hhplus.be.server.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.hhplus.be.server.application.usecase.RechargePointUseCase;
import kr.hhplus.be.server.config.manager.DistributedLockManager;
import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.domain.port.WalletPort;

/** 포인트 충전 유스케이스 단위 테스트 **/
@ExtendWith(MockitoExtension.class)
public class RechargePointUseCaseTest {
	@Mock
	WalletPort walletPort;
	@Mock
	DistributedLockManager lockManager;

	@Test
	@DisplayName("충전 성공")
	void recharge_success() {
		// Mock 객체로 주입된 RechargePointUseCase 생성
		RechargePointUseCase sut = new RechargePointUseCase(walletPort, lockManager);

		Long userId = 9L;
		Money amount = Money.of(30000);
		String reqId = "charge-001";

		// 충전 요청이 아직 처리되지 않았음을 가정
		// 이 요청 ID는 처음이므로, 충전 로직을 계속 진행하라는 조건 충족
		when(walletPort.hasProcessed(userId, reqId)).thenReturn(false);
		// 잔액 10000원으로 가정
		when(walletPort.findByUserIdForUpdate(userId))
			.thenReturn(new PointWallet(userId, Money.of(10000)));

		var res = sut.handle(userId, amount, reqId);

		// 충전 후 잔액이 40000원인지 확인
		assertEquals(40000, res.walletBalance().asLong());
		verify(walletPort).save(any(PointWallet.class));
	}

	@Test
	@DisplayName("동일한 요청 ID로 충전 요청이 다시 들어왔을 때, 중복 처리 방지")
	void recharge_idempotent() {
		RechargePointUseCase sut = new RechargePointUseCase(walletPort, lockManager);
		Long userId = 9L;
		Money amount = Money.of(30000);
		String reqId = "charge-001";

		// 이미 충전되었음을 가정
		when(walletPort.hasProcessed(userId, reqId)).thenReturn(true);
		when(walletPort.findByUserIdForUpdate(userId))
			.thenReturn(new PointWallet(userId, Money.of(40000)));

		var res = sut.handle(userId, amount, reqId);

		assertEquals(40000, res.walletBalance().asLong());
		// 저장 호출 없음(이미 처리됨)
		verify(walletPort, never()).save(any());
	}
}
