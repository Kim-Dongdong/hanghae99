package kr.hhplus.be.server.domain.port;

import java.time.LocalDateTime;
import java.util.List;

import kr.hhplus.be.server.domain.model.Money;

public interface ConcertQueryPort {
	record ConcertDto(Long id, String title, String venue) {}
	record ScheduleDto(Long id, Long concertId, LocalDateTime dateTime) {}
	record SeatDto(Integer seatNo, String tier, Money price, boolean available) {}

	List<ConcertDto> listConcerts();
	List<ScheduleDto> listSchedules(Long concertId);
	List<SeatDto> listAvailableSeats(Long scheduleId);
}
