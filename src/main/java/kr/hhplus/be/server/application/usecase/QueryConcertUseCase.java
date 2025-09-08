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

	/** 모든 콘서트 목록 조회 **/
	public List<ConcertQueryPort.ConcertDto> listConcerts() {
		return concertQueryPort.listConcerts();
	}

	/** 특정 콘서트의 스케줄 목록 조회 **/
	public List<ConcertQueryPort.ScheduleDto> listSchedules(Long concertId) {
		return concertQueryPort.listSchedules(concertId);
	}

	/** 특정 스케줄에 대한 예약 가능한 죄석 조회 **/
	public List<ConcertQueryPort.SeatDto> listAvailableSeats(Long scheduleId) {
		return concertQueryPort.listAvailableSeats(scheduleId);
	}
}
