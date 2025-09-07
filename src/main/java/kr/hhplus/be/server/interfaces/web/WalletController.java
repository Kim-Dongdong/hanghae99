package kr.hhplus.be.server.interfaces.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.application.usecase.RechargePointUseCase;
import kr.hhplus.be.server.domain.model.Money;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {
	private final RechargePointUseCase useCase;

	public WalletController(RechargePointUseCase useCase) {
		this.useCase = useCase;
	}

	@PostMapping("/recharge")
	public RechargeResponse recharge(@RequestBody RechargeRequest req) {
		var res = useCase.handle(req.userId(), Money.of(req.amount()), req.requestId());
		return new RechargeResponse(res.walletBalance().asLong());
	}

	public record RechargeRequest(Long userId, long amount, String requestId) {}
	public record RechargeResponse(long walletBalance) {}
}
