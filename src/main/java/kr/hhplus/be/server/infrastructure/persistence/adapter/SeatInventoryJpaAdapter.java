package kr.hhplus.be.server.infrastructure.persistence.adapter;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.port.SeatInventoryPort;
import kr.hhplus.be.server.infrastructure.persistence.entity.SeatStateEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringSeatStateJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowSeatJpa;

@Component
public class SeatInventoryJpaAdapter implements SeatInventoryPort {

	private final SpringSeatStateJpa seatJpa;
	private final SpringShowSeatJpa showSeatJpa;
	private static final long DEFAULT_HOLD_SECONDS = 120;

	public SeatInventoryJpaAdapter(SpringSeatStateJpa seatStateJpa,
		SpringShowSeatJpa showSeatJpa) {
		this.seatJpa = seatStateJpa;
		this.showSeatJpa = showSeatJpa;
	}


	/** 특정 좌석 HOLD **/
	@Override
	@Transactional
	public boolean tryHold(Long scheduleId, Integer seatNo, long ttlSeconds) {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime expires = now.plusSeconds(ttlSeconds);

		// 1) 기존 행이 있다면 조건부 UPDATE로 점유 시도
		int updated = seatJpa.updateToHoldIfFree(scheduleId, seatNo, now, expires);
		if (updated == 1) return true;

		// 2) 없을 수 있으니 INSERT (단순 테스트 환경용: 존재 체크 후 삽입)
		boolean exists = seatJpa.findByShowIdAndSeatNo(scheduleId, seatNo).isPresent();
		if (exists) {
			// 이미 있는데 조건을 만족 못해서 업데이트 못한 케이스 → 점유 실패
			return false;
		}

		SeatStateEntity n = new SeatStateEntity();
		n.showId = scheduleId;
		n.seatNo = seatNo;
		n.status = "HELD";
		n.expiresAt = expires;
		seatJpa.save(n); // 테스트에선 예외 없이 OK

		return true;
	}

	/**  임시 점유(HELD) 상태의 좌석을 최종 결제 완료(CONFIRMED) 상태로 변경 **/
	@Override
	@Transactional
	public boolean markConfirmed(Long scheduleId, Integer seatNo) {
		return seatJpa.findByShowIdAndSeatNo(scheduleId, seatNo).map(e -> {
			// 이미 확정이면 OK
			if ("CONFIRMED".equals(e.status)) return true;

			// HELD가 아니면 확정 불가
			if (!"HELD".equals(e.status)) return false;

			// 만료된 HELD는 확정 불가
			if (e.expiresAt != null && e.expiresAt.isBefore(LocalDateTime.now())) return false;

			e.status = "CONFIRMED";
			e.expiresAt = null;
			seatJpa.save(e);
			return true;
		}).orElse(false);
	}

	@Override
	public Money seatPriceOf(Long scheduleId, Integer seatNo) {
		// 데모: 좌석번호 * 1000원
		return Money.of(seatNo * 1000L);
	}

}
