package kr.hhplus.be.server.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.port.ConcertQueryPort;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowSeatEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringSeatStateJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowSeatJpa;

@Component
public class ConcertQueryJpaAdapter implements ConcertQueryPort {
	private final SpringShowJpa showJpa;
	private final SpringShowSeatJpa showSeatJpa;
	private final SpringSeatStateJpa seatStateJpa;

	public ConcertQueryJpaAdapter(SpringShowJpa showJpa,
		SpringShowSeatJpa showSeatJpa,
		SpringSeatStateJpa seatStateJpa) {
		this.showJpa = showJpa;
		this.showSeatJpa = showSeatJpa;
		this.seatStateJpa = seatStateJpa;
	}

	@Override
	public List<ConcertDto> listConcerts() {
		return showJpa.findAll().stream()
			.map(this::toConcertDto)
			.toList();
	}

	@Override
	public List<ScheduleDto> listSchedules(Long concertId) {
		return showJpa.findById(concertId)
			.map(s -> List.of(new ScheduleDto(
				// 스케줄 1건으로 간주
				/*id*/ concertId,
				/*concertId*/ concertId,
				/*dateTime*/ s.startsAt
			)))
			.orElse(List.of());
	}

	@Override
	public List<SeatDto> listAvailableSeats(Long scheduleId) {
		// scheduleId == showId 가정
		LocalDateTime now = LocalDateTime.now();

		List<ShowSeatEntity> seats = showSeatJpa.findByShowIdAndIsActiveTrue(scheduleId);

		return seats.stream()
			.map(seat -> {
				boolean available = seatStateJpa.findByShowIdAndSeatNo(scheduleId, seat.seatNo)
					.map(state -> {
						// CONFIRMED 이면 불가
						if ("CONFIRMED".equals(state.status)) return false;
						// HELD && 만료 전이면 불가, 만료면 가용
						if ("HELD".equals(state.status) &&
							state.expiresAt != null &&
							state.expiresAt.isAfter(now)) return false;
						return true;
					})
					.orElse(true); // 상태 행이 아예 없으면 가용

				return new SeatDto(
					seat.seatNo,
					seat.seatTier,
					Money.of(seat.basePrice != null ? seat.basePrice : 0L),
					available
				);
			})
			.toList();
	}

	// ===== 매핑 =====
	private ConcertDto toConcertDto(ShowEntity s) {
		return new ConcertDto(
			s.showId,  // 포트가 Long을 요구하므로 변환
			s.title,
			s.venue
		);
	}
}
