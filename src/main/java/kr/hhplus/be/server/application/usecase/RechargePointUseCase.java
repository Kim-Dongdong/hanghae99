package kr.hhplus.be.server.application.usecase;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.config.manager.DistributedLockManager;
import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.domain.port.WalletPort;

@Service
public class RechargePointUseCase {
	private final WalletPort walletPort;
	private final DistributedLockManager lockManager;

	public RechargePointUseCase(WalletPort walletPort, DistributedLockManager lockManager) {
		this.walletPort = walletPort;
		this.lockManager = lockManager;
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

	@Transactional
	public Result handleWithDistributedLock(Long userId, Money amount, String requestId) {
		String lockKey = "wallet:recharge:" + userId;

		return lockManager.executeWithLock(
			lockKey,
			5,  // waitTime: 5초
			3,  // leaseTime: 3초 (자동 해제)
			TimeUnit.SECONDS,
			() -> {
				// 멱등성 체크
				if (walletPort.hasProcessed(userId, requestId)) {
					PointWallet current = walletPort.findByUserIdForUpdate(userId);
					return new Result(current.getBalance());
				}

				// 충전 처리
				PointWallet wallet = walletPort.findByUserIdForUpdate(userId);
				wallet.recharge(amount);
				walletPort.save(wallet);
				walletPort.recordProcessed(userId, requestId, amount, wallet.getBalance());

				return new Result(wallet.getBalance());
			}
		);
	}

	// Result 객체를 반환하기 위한 record 객체
	public record Result(Money walletBalance) {}
}
