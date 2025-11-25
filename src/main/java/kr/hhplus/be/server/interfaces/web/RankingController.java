package kr.hhplus.be.server.interfaces.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.application.usecase.GetFastestSoldOutRankingUseCase;
import kr.hhplus.be.server.application.usecase.RecordSoldOutUseCase;
import kr.hhplus.be.server.domain.port.SoldOutRankingPort;

@RestController
@RequestMapping("/api/rankings")
public class RankingController {

	private final RecordSoldOutUseCase recordSoldOutUseCase;
	private final GetFastestSoldOutRankingUseCase getRankingUseCase;
	private final SoldOutRankingPort rankingPort;

	public RankingController(
		RecordSoldOutUseCase recordSoldOutUseCase,
		GetFastestSoldOutRankingUseCase getRankingUseCase,
		SoldOutRankingPort rankingPort) {
		this.recordSoldOutUseCase = recordSoldOutUseCase;
		this.getRankingUseCase = getRankingUseCase;
		this.rankingPort = rankingPort;
	}


	// Top N 빠른 매진 랭킹 조회
	@GetMapping("/fastest-soldout")
	public ResponseEntity<GetFastestSoldOutRankingUseCase.Result> getFastestSoldOut(
		@RequestParam(defaultValue = "10") int limit) {
		GetFastestSoldOutRankingUseCase.Result result = getRankingUseCase.handle(limit);
		return ResponseEntity.ok(result);
	}

	// 특정 콘서트의 랭킹 조회
	@GetMapping("/shows/{showId}/rank")
	public ResponseEntity<RankResponse> getShowRank(@PathVariable Long showId) {
		Long rank = rankingPort.getRank(showId).orElse(null);
		Long duration = rankingPort.getSoldOutDuration(showId).orElse(null);

		if (rank == null) {
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok(new RankResponse(showId, rank, duration));
	}

	// 매진 기록 (내부 API - 실제로는 예약 로직에서 자동 호출)
	@PostMapping("/shows/{showId}/record-soldout")
	public ResponseEntity<RecordSoldOutUseCase.Result> recordSoldOut(
		@PathVariable Long showId) {
		RecordSoldOutUseCase.Result result = recordSoldOutUseCase.handle(showId);
		return ResponseEntity.ok(result);
	}

	record RankResponse(Long showId, Long rank, Long soldOutDurationSeconds) {}
}
