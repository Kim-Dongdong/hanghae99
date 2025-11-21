package kr.hhplus.be.server.domain.port;

import java.util.Optional;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.infrastructure.persistence.entity.WalletEntity;

public interface WalletPort {
	PointWallet findByUserIdForUpdate(Long userId);
	void save(PointWallet wallet);
	boolean hasProcessed(Long userId, String requestId);

	Optional<WalletEntity> findById(long userId);
	void recordProcessed(Long userId, String requestId, Money amount, Money balanceAfter);

	// 분산락 사용시 일반 조회
	PointWallet findByUserId(Long userId);
}
