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

		// 1) 먼저 지갑을 락 걸고 가져온다 (행 잠금)
		PointWallet wallet = walletPort.findByUserIdForUpdate(userId);

		// 2) 멱등성 체크
		if (walletPort.hasProcessed(userId, requestId)) {
			PointWallet current = walletPort.findByUserIdForUpdate(userId);
			return new Result(current.getBalance());
		}

		// 3) 충전 및 저장
		wallet.recharge(amount);
		walletPort.save(wallet);

		// 4) 멱등 기록 (유니크 충돌은 내부에서 안전 처리)
		walletPort.recordProcessed(userId, requestId, amount, wallet.getBalance());

		return new Result(wallet.getBalance());
	}

	// Result 객체를 반환하기 위한 record 객체
	public record Result(Money walletBalance) {}
}
