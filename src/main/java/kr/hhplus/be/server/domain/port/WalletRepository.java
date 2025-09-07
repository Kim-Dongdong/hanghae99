package kr.hhplus.be.server.domain.port;

import kr.hhplus.be.server.domain.model.PointWallet;

public interface WalletRepository {
	PointWallet findByUserIdForUpdate(Long userId);
	void save(PointWallet wallet);
	boolean hasProcessed(Long userId, String requestId);
}
