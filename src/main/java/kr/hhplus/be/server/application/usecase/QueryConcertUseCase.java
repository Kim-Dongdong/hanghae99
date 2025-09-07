package kr.hhplus.be.server.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.domain.port.ConcertQueryPort;

@Service
public class QueryConcertUseCase {

	private final ConcertQueryPort concertQueryPort;

	public QueryConcertUseCase(ConcertQueryPort concertQueryPort) {
		this.concertQueryPort = concertQueryPort;
	}

	public List<ConcertQueryPort.ConcertDto> listConcerts() {
		return concertQueryPort.listConcerts();
	}

	public List<ConcertQueryPort.ScheduleDto> listSchedules(Long concertId) {
		return concertQueryPort.listSchedules(concertId);
	}

	public List<ConcertQueryPort.SeatDto> listAvailableSeats(Long scheduleId) {
		return concertQueryPort.listAvailableSeats(scheduleId);
	}
}
