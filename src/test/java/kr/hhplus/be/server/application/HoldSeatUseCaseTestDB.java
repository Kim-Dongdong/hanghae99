package kr.hhplus.be.server.application;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import kr.hhplus.be.server.domain.port.SeatInventoryPort;
import kr.hhplus.be.server.infrastructure.persistence.adapter.SeatInventoryJpaAdapter;
import kr.hhplus.be.server.infrastructure.persistence.entity.SeatStateEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringSeatStateJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowSeatJpa;

@DataJpaTest
@ActiveProfiles("test")
@EntityScan(basePackages = "kr.hhplus.be.server.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "kr.hhplus.be.server.infrastructure.persistence.springdata")
@Import(SeatInventoryJpaAdapter.class) // @Component를 DataJpaTest에 주입
public class HoldSeatUseCaseTestDB {

	@Autowired SeatInventoryPort seatInventory;   // 실제로는 SeatInventoryJpaAdapter 빈 주입
	@Autowired SpringSeatStateJpa seatStateJpa;   // 검증/시드 용
	@Autowired SpringShowSeatJpa showSeatJpa;     // 어댑터 생성자 의존성(가격은 더미 로직이라 시드는 불필요)

	private static final long SHOW_ID = 100L;

	@Test
	@DisplayName("이미 HELD & 만료 전이면 홀드 실패")
	void tryHold_conflict_when_already_held_and_not_expired() {
		// given
		seatStateJpa.deleteAllInBatch();
		int seatNo = 11;
		SeatStateEntity e = new SeatStateEntity();
		e.showId = SHOW_ID;
		e.seatNo = seatNo;
		e.status = "HELD";
		e.expiresAt = LocalDateTime.now().plusMinutes(3);
		seatStateJpa.save(e);

		// when
		boolean ok = seatInventory.tryHold(SHOW_ID, seatNo, 120);

		// then
		assertThat(ok).isFalse();
		SeatStateEntity after = seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, seatNo).orElseThrow();
		assertThat(after.status).isEqualTo("HELD");
		assertThat(after.expiresAt).isAfter(LocalDateTime.now());
	}

	@Test
	@DisplayName("이미 CONFIRMED면 홀드 실패(좌석 확정 불가)")
	void tryHold_fail_when_already_confirmed() {
		// given
		seatStateJpa.deleteAllInBatch();
		int seatNo = 13;
		SeatStateEntity e = new SeatStateEntity();
		e.showId = SHOW_ID;
		e.seatNo = seatNo;
		e.status = "CONFIRMED";
		e.expiresAt = null;
		seatStateJpa.save(e);

		// when
		boolean ok = seatInventory.tryHold(SHOW_ID, seatNo, 120);

		// then
		assertThat(ok).isFalse();
		SeatStateEntity after = seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, seatNo).orElseThrow();
		assertThat(after.status).isEqualTo("CONFIRMED");
		assertThat(after.expiresAt).isNull();
	}

	@Test
	@DisplayName("만료되었거나 HELD가 아니면 CONFIRMED 실패")
	void markConfirmed_fail_when_not_held_or_expired() {
		// case1: 만료된 HELD
		seatStateJpa.deleteAllInBatch();
		int seatNo1 = 15;
		SeatStateEntity expiredHeld = new SeatStateEntity();
		expiredHeld.showId = SHOW_ID;
		expiredHeld.seatNo = seatNo1;
		expiredHeld.status = "HELD";
		expiredHeld.expiresAt = LocalDateTime.now().minusSeconds(10);
		seatStateJpa.save(expiredHeld);

		boolean ok1 = seatInventory.markConfirmed(SHOW_ID, seatNo1);
		assertThat(ok1).isFalse();
		assertThat(seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, seatNo1).orElseThrow().status)
			.isEqualTo("HELD");

		// case2: AVAILABLE(레코드 없음) → false
		int seatNo2 = 16;
		boolean ok2 = seatInventory.markConfirmed(SHOW_ID, seatNo2);
		assertThat(ok2).isFalse();
	}
}
