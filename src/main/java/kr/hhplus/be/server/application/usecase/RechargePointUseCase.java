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

	@Transactional
	public Result handle(Long userId, Money amount, String requestId) {
		if (walletRepository.hasProcessed(userId, requestId)) {
			PointWallet wallet = walletRepository.findByUserIdForUpdate(userId);
			return new Result(wallet.getBalance());
		}

		PointWallet wallet = walletRepository.findByUserIdForUpdate(userId);
		wallet.charge(amount, requestId);
		walletRepository.save(wallet);
		return new Result(wallet.getBalance());
	}

	public record Result(Money walletBalance) {}
}
