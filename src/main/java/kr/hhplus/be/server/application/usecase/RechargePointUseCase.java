package kr.hhplus.be.server.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.domain.port.WalletRepository;

@Service
public class RechargePointUseCase {
	private final WalletRepository walletRepository;

	public RechargePointUseCase(WalletRepository walletRepository) {
		this.walletRepository = walletRepository;
	}

	/** 포인트 충전 요청 처리 **/
	@Transactional
	public Result handle(Long userId, Money amount, String requestId) {
		// 이미 처리된 요청이라면, 중복 처리를 방지하기 위해 현재 지갑 잔액을 조회하여 바로 반환
		if (walletRepository.hasProcessed(userId, requestId)) {
			PointWallet wallet = walletRepository.findByUserIdForUpdate(userId);
			return new Result(wallet.getBalance());
		}

		// 사용자 지갑 정보 조회 후 update
		PointWallet wallet = walletRepository.findByUserIdForUpdate(userId);
		wallet.charge(amount, requestId);
		walletRepository.save(wallet);
		return new Result(wallet.getBalance());
	}

	// Result 객체를 반환하기 위한 record 객체
	public record Result(Money walletBalance) {}
}
