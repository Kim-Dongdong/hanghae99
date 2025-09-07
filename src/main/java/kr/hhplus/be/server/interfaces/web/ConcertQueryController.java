package kr.hhplus.be.server.interfaces.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.application.usecase.QueryConcertUseCase;
import kr.hhplus.be.server.domain.port.ConcertQueryPort;

@RestController
@RequestMapping("/api")
public class ConcertQueryController {
	private final QueryConcertUseCase useCase;

	public ConcertQueryController(QueryConcertUseCase useCase) {
		this.useCase = useCase;
	}

	@GetMapping("/concerts")
	public List<ConcertQueryPort.ConcertDto> listConcerts() {
		return useCase.listConcerts();
	}

	@GetMapping("/concerts/{id}/schedules")
	public List<ConcertQueryPort.ScheduleDto> listSchedules(@PathVariable Long id) {
		return useCase.listSchedules(id);
	}

	@GetMapping("/schedules/{id}/seats")
	public List<ConcertQueryPort.SeatDto> listSeats(@PathVariable Long id) {
		return useCase.listAvailableSeats(id);
	}
}
