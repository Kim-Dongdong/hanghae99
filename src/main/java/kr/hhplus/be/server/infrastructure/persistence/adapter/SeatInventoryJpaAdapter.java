package kr.hhplus.be.server.infrastructure.persistence.adapter;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.port.SeatInventoryPort;
import kr.hhplus.be.server.infrastructure.persistence.entity.SeatStateEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringSeatStateJpa;

@Component
public class SeatInventoryJpaAdapter implements SeatInventoryPort {
	private final SpringSeatStateJpa seatJpa;

	public SeatInventoryJpaAdapter(SpringSeatStateJpa seatJpa) {
		this.seatJpa = seatJpa;
	}

	@Override
	@Transactional
	public boolean tryHold(Long scheduleId, Integer seatNo, long ttlSeconds) {

		// 활성행 1개 유지 모델: 존재하면 상태/만료 갱신 시도, 없으면 insert
		LocalDateTime expires = LocalDateTime.now().plusSeconds(ttlSeconds);
		return seatJpa.findByShowIdAndSeatNo(scheduleId, seatNo).map(e -> {
			if ("CONFIRMED".equals(e.status)) return false;

			// 이미 상태가 HELD인데 만료 전이면 경합 실패
			if ("HELD".equals(e.status) && e.expiresAt != null && e.expiresAt.isAfter(LocalDateTime.now()))
				return false;
			e.status = "HELD";
			e.expiresAt = expires;
			seatJpa.save(e);
			return true;
		}).orElseGet(() -> {
			try {
				SeatStateEntity n = new SeatStateEntity();
				n.showId = scheduleId;
				n.seatNo = seatNo;
				n.status = "HELD";
				n.expiresAt = expires;
				seatJpa.save(n);
				return true;
			} catch (DataIntegrityViolationException dup) {
				return false;
			}
		});
	}

	@Override
	@Transactional
	public boolean markConfirmed(Long scheduleId, Integer seatNo) {
		return seatJpa.findByShowIdAndSeatNo(scheduleId, seatNo).map(e -> {
			if ("CONFIRMED".equals(e.status)) return true;
			if (!"HELD".equals(e.status)) return false;
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
