package kr.hhplus.be.server.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.domain.port.WalletPort;

@Service
public class RechargePointUseCase {
	private final WalletPort walletPort;

	public RechargePointUseCase(WalletPort walletPort) {
		this.walletPort = walletPort;
	}

	/** 포인트 충전 요청 처리 **/
	@Transactional
	public Result handle(Long userId, Money amount, String requestId) {
		// 1) 멱등성 체크
		if (walletPort.hasProcessed(userId, requestId)) {
			PointWallet current = walletPort.findByUserIdForUpdate(userId);
			return new Result(current.getBalance());
		}

		// 2) 지갑 잠금 조회 후 충전
		PointWallet wallet = walletPort.findByUserIdForUpdate(userId);
		wallet.recharge(amount);
		walletPort.save(wallet);

		// 3) 딱 한번만 처리되도록 기록 남김 (중복 요청 차단 근거)
		walletPort.recordProcessed(userId, requestId, amount, wallet.getBalance());

		return new Result(wallet.getBalance());
	}

	// Result 객체를 반환하기 위한 record 객체
	public record Result(Money walletBalance) {}
}
