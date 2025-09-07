package kr.hhplus.be.server.interfaces.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.application.usecase.ConfirmReservationUseCase;
import kr.hhplus.be.server.application.usecase.HoldSeatUseCase;
import kr.hhplus.be.server.domain.model.Money;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
	private final HoldSeatUseCase holdUseCase;
	private final ConfirmReservationUseCase confirmUseCase;

	public ReservationController(HoldSeatUseCase holdUseCase,
		ConfirmReservationUseCase confirmUseCase) {
		this.holdUseCase = holdUseCase;
		this.confirmUseCase = confirmUseCase;
	}

	@PostMapping("/hold")
	public HoldResponse hold(@RequestBody HoldRequest req) {
		var res = holdUseCase.handle(req.userId(), req.scheduleId(), req.seatNo());
		return new HoldResponse(res.reservationId(), res.expiresAt().toString(), res.amount().asLong());
	}

	@PostMapping("/{id}/confirm")
	public ConfirmResponse confirm(@PathVariable Long id, @RequestBody ConfirmRequest req) {
		var res = confirmUseCase.handle(id, req.userId(), Money.of(req.amount()), req.idempotencyKey());
		return new ConfirmResponse(res.status(), res.walletBalance().asLong());
	}

	// ---- DTO들 ----
	public record HoldRequest(Long userId, Long scheduleId, Integer seatNo) {}
	public record HoldResponse(Long reservationId, String expiresAt, long amount) {}
	public record ConfirmRequest(Long userId, long amount, String idempotencyKey) {}
	public record ConfirmResponse(String status, long walletBalance) {}
}
