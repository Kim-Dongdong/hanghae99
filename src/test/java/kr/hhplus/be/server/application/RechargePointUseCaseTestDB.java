package kr.hhplus.be.server.application;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.ServerApplication;
import kr.hhplus.be.server.application.usecase.RechargePointUseCase;
import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.infrastructure.persistence.entity.PointHistoryEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.WalletEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringPointHistoryJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringWalletJpa;

@SpringBootTest(classes = ServerApplication.class)
@ActiveProfiles("test")
@EntityScan(basePackages = "kr.hhplus.be.server.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "kr.hhplus.be.server.infrastructure.persistence.springdata")
public class RechargePointUseCaseTestDB {

	@Autowired
	RechargePointUseCase rechargePointUseCase;

	@Autowired
	SpringWalletJpa walletJpa;
	@Autowired
	SpringPointHistoryJpa historyJpa;

	private static final Long USER_ID = 900L;

	@BeforeEach
	void clean() {
		historyJpa.deleteAllInBatch();
		walletJpa.deleteAllInBatch();
	}

	@Test
	@DisplayName("지갑이 없더라도 충전 요청 시 생성되고 잔액/이력 기록된다")
	@Transactional
	void recharge_creates_wallet_and_records_history() {
		// given
		Money amount = Money.of(30_000);
		String reqId = "charge-001";

		// when
		var res = rechargePointUseCase.handle(USER_ID, amount, reqId);

		// then
		// 잔액 30,000원
		assertThat(res.walletBalance().asLong()).isEqualTo(30_000);

		// DB 검증: wallet row 생김
		WalletEntity w = walletJpa.findById(USER_ID).orElseThrow();
		assertThat(w.balance.amount).isEqualTo(30_000);

		// DB 검증: 이력 1건 생성
		long cnt = historyJpa.countByUserIdAndRequestId(USER_ID, reqId);
		assertThat(cnt).isEqualTo(1L);

		PointHistoryEntity h = historyJpa.findTopByUserIdAndRequestIdOrderByCreatedAtDesc(USER_ID, reqId).orElseThrow();
		assertThat(h.type).isEqualTo("RECHARGE");
		assertThat(h.amount.amount).isEqualTo(30_000);
		assertThat(h.balanceAfter.amount).isEqualTo(30_000);
	}

	@Test
	@DisplayName("같은 requestId로 두 번 충전해도 멱등하게 한 번만 반영된다")
	@Transactional
	void recharge_is_idempotent_by_requestId() {
		// given
		Money amount = Money.of(20_000);
		String reqId = "charge-dup-001";

		// when
		var first = rechargePointUseCase.handle(USER_ID, amount, reqId);
		var second = rechargePointUseCase.handle(USER_ID, amount, reqId); // 같은 요청 재시도

		// then
		// 첫 호출로 20,000, 두 번째는 멱등 처리되어 그대로
		assertThat(first.walletBalance().asLong()).isEqualTo(20_000);
		assertThat(second.walletBalance().asLong()).isEqualTo(20_000);

		// DB: wallet 잔액 20,000 유지
		WalletEntity w = walletJpa.findById(USER_ID).orElseThrow();
		assertThat(w.balance.amount).isEqualTo(20_000);

		// DB: 동일 (userId, requestId) 이력은 1건만
		long cnt = historyJpa.countByUserIdAndRequestId(USER_ID, reqId);
		assertThat(cnt).isEqualTo(1L);
	}
}
