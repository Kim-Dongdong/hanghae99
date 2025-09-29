package kr.hhplus.be.server.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.PointWallet;
import kr.hhplus.be.server.domain.port.WalletPort;
import kr.hhplus.be.server.infrastructure.persistence.entity.MoneyEmbeddable;
import kr.hhplus.be.server.infrastructure.persistence.entity.PointHistoryEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.WalletEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringPointHistoryJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringWalletJpa;

@Component
public class WalletJpaAdapter implements WalletPort {
	private final SpringWalletJpa walletJpa;
	private final SpringPointHistoryJpa historyJpa;

	public WalletJpaAdapter(SpringWalletJpa walletJpa, SpringPointHistoryJpa historyJpa) {
		this.walletJpa = walletJpa;
		this.historyJpa = historyJpa;
	}

	/** 특정 사용자의 지갑 정보를 수정하기 위해 조회 **/
	@Override
	@Transactional
	public PointWallet findByUserIdForUpdate(Long userId) {
		WalletEntity e = walletJpa.findByUserIdForUpdate(userId)
			.orElseGet(() -> {
				WalletEntity ne = new WalletEntity();
				ne.id = userId;
				ne.balance = new MoneyEmbeddable(0);
				ne.updatedAt = LocalDateTime.now();
				return walletJpa.save(ne);
			});
		return PointWallet.rehydrate(e.id, Money.of(e.balance.amount));
	}

	/** 저장 **/
	@Override
	@Transactional
	public void save(PointWallet wallet) {
		WalletEntity e = walletJpa.findByUserIdForUpdate(wallet.getUserId())
			.orElseThrow(() -> new IllegalStateException("Wallet not found: " + wallet.getUserId()));
		e.balance = new MoneyEmbeddable(wallet.getBalance().asLong());
		e.updatedAt = LocalDateTime.now();
		walletJpa.save(e); // @Version 있어도 무방
	}

	/**  특정 요청이 이미 처리되었는지 확인,
	 * 주어진 userId와 requestId를 가진 포인트 기록(PointHistoryEntity)이 데이터베이스에 존재하는지 확인**/
	@Override
	@Transactional(readOnly = true)
	public boolean hasProcessed(Long userId, String requestId) {
		return historyJpa.existsByUserIdAndRequestId(userId, requestId);
	}


	/** 포인트 거래 기록 저장 **/
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

	@Override
	public Optional<WalletEntity> findById(long userId) {
		return walletJpa.findById(userId);
	}

	@Override
	@Transactional
	public void recordProcessed(Long userId, String requestId, Money amount, Money balanceAfter) {
		recordHistory(userId, "RECHARGE", amount, balanceAfter, requestId, null);
	}
}
