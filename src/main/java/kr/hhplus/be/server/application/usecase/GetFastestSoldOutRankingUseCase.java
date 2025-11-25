package kr.hhplus.be.server.application.usecase;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.port.ConcertQueryPort;
import kr.hhplus.be.server.domain.port.SoldOutRankingPort;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowEntity;

@Service
public class GetFastestSoldOutRankingUseCase {

	private final SoldOutRankingPort rankingPort;
	private final ConcertQueryPort showPort;

	public GetFastestSoldOutRankingUseCase(
		SoldOutRankingPort rankingPort,
		ConcertQueryPort showPort) {
		this.rankingPort = rankingPort;
		this.showPort = showPort;
	}

	@Transactional(readOnly = true)
	public Result handle(int limit) {
		// Redis에서 랭킹 조회
		List<SoldOutRankingPort.RankingEntry> rankings =
			rankingPort.getFastestSoldOut(limit);

		if (rankings.isEmpty()) {
			return new Result(List.of());
		}

		// DB에서 Show 정보 일괄 조회
		List<Long> showIds = rankings.stream()
			.map(SoldOutRankingPort.RankingEntry::showId)
			.collect(Collectors.toList());

		Map<Long, ShowEntity> showMap = showPort.findAllByIds(showIds).stream()
			.collect(Collectors.toMap(show -> show.id, show -> show));

		// 결과 조합
		List<RankingItem> items = rankings.stream()
			.map(entry -> {
				ShowEntity show = showMap.get(entry.showId());
				return new RankingItem(
					entry.showId(),
					show != null ? show.title : "Unknown",
					show != null ? show.venue : null,
					entry.soldOutDurationSeconds(),
					formatDuration(entry.soldOutDurationSeconds())
				);
			})
			.collect(Collectors.toList());

		return new Result(items);
	}

	private String formatDuration(long seconds) {
		long minutes = seconds / 60;
		long remainingSeconds = seconds % 60;

		if (minutes == 0) {
			return remainingSeconds + "초";
		} else if (remainingSeconds == 0) {
			return minutes + "분";
		} else {
			return minutes + "분 " + remainingSeconds + "초";
		}
	}

	public record Result(List<RankingItem> rankings) {}

	public record RankingItem(
		Long showId,
		String title,
		String venue,
		long soldOutDurationSeconds,
		String durationFormatted
	) {}
}
