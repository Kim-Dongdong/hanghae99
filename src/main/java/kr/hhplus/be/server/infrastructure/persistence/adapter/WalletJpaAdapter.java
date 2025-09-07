package kr.hhplus.be.server.infrastructure.persistence.adapter;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.domain.port.WalletRepository;
import kr.hhplus.be.server.infrastructure.persistence.entity.MoneyEmbeddable;
import kr.hhplus.be.server.infrastructure.persistence.entity.PointHistoryEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.WalletEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringPointHistoryJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringWalletJpa;

@Component
public class WalletJpaAdapter implements WalletRepository {
	private final SpringWalletJpa walletJpa;
	private final SpringPointHistoryJpa historyJpa;

	public WalletJpaAdapter(SpringWalletJpa walletJpa, SpringPointHistoryJpa historyJpa) {
		this.walletJpa = walletJpa;
		this.historyJpa = historyJpa;
	}

	@Override
	@Transactional
	public PointWallet findByUserIdForUpdate(Long userId) {
		WalletEntity e = walletJpa.findByUserIdForUpdate(userId)
			.orElseGet(() -> {
				WalletEntity ne = new WalletEntity();
				ne.userId = userId;
				ne.balance = new MoneyEmbeddable(0);
				ne.updatedAt = LocalDateTime.now();
				return walletJpa.save(ne);
			});
		return PointWallet.rehydrate(e.userId, Money.of(e.balance.amount));
	}

	@Override
	@Transactional
	public void save(PointWallet wallet) {
		WalletEntity e = walletJpa.findById(wallet.getUserId())
			.orElseGet(() -> {
				WalletEntity ne = new WalletEntity();
				ne.userId = wallet.getUserId();
				return ne;
			});
		e.balance = new MoneyEmbeddable(wallet.getBalance().asLong());
		e.updatedAt = LocalDateTime.now();
		walletJpa.save(e);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hasProcessed(Long userId, String requestId) {
		return historyJpa.existsByUserIdAndRequestId(userId, requestId);
	}

	// 기록 메서드
	@Transactional
	public void recordHistory(Long userId, String type, Money amount, Money balanceAfter, String requestId, String paymentId) {
		PointHistoryEntity h = new PointHistoryEntity();
		h.userId = userId;
		h.type = type;
		h.amount = new MoneyEmbeddable(amount.asLong());
		h.balanceAfter = new MoneyEmbeddable(balanceAfter.asLong());
		h.requestId = requestId;
		h.paymentId = paymentId;
		h.createdAt = LocalDateTime.now();
		historyJpa.save(h);
	}
}
