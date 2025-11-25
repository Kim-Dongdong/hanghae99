package kr.hhplus.be.server.application.usecase;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.config.manager.DistributedLockManager;
import kr.hhplus.be.server.domain.port.ConcertQueryPort;
import kr.hhplus.be.server.domain.port.SoldOutRankingPort;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowEntity;

@Service
public class RecordSoldOutUseCase {

	private final ConcertQueryPort showPort;
	private final SoldOutRankingPort rankingPort;
	private final DistributedLockManager lockManager;

	public RecordSoldOutUseCase(
		ConcertQueryPort showPort,
		SoldOutRankingPort rankingPort,
		DistributedLockManager lockManager) {
		this.showPort = showPort;
		this.rankingPort = rankingPort;
		this.lockManager = lockManager;
	}

	/**
	 * 콘서트 매진 시 호출 - 랭킹에 기록
	 */
	@Transactional
	public Result handle(Long showId) {
		String lockKey = "show:soldout:record:" + showId;

		return lockManager.executeWithLock(
			lockKey,
			3,  // waitTime: 3초
			5,  // leaseTime: 5초
			TimeUnit.SECONDS,
			() -> {
				// 이미 기록되어 있는지 확인 (중복 방지)
				if (rankingPort.hasSoldOutRecord(showId)) {
					long duration = rankingPort.getSoldOutDuration(showId).orElse(0L);
					Long rank = rankingPort.getRank(showId).orElse(null);
					return new Result(showId, duration, rank, false);
				}

				// Show 정보 조회
				ShowEntity show = showPort.findById(showId);

				// 매진까지 걸린 시간 계산
				LocalDateTime soldOutTime = LocalDateTime.now();
				long durationSeconds = Duration.between(
					show.salesOpenAt,
					soldOutTime
				).getSeconds();

				// Redis에 기록
				rankingPort.recordSoldOut(showId, durationSeconds);

				// DB 상태 업데이트 (선택적)
				showPort.updateStatus(showId, "SOLD_OUT");

				// 랭킹 조회
				Long rank = rankingPort.getRank(showId).orElse(null);

				return new Result(showId, durationSeconds, rank, true);
			}
		);
	}

	public record Result(
		Long showId,
		long soldOutDurationSeconds,
		Long rank,
		boolean newRecord
	) {}
}
