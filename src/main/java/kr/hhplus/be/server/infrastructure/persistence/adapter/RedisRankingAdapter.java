package kr.hhplus.be.server.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.domain.port.SoldOutRankingPort;

@Component
public class RedisRankingAdapter implements SoldOutRankingPort {

	private static final String RANKING_KEY = "show:soldout:ranking";
	private final RedisTemplate<String, String> redisTemplate;

	public RedisRankingAdapter(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void recordSoldOut(Long showId, long soldOutDurationSeconds) {
		redisTemplate.opsForZSet().add(
			RANKING_KEY,
			showId.toString(),
			soldOutDurationSeconds
		);
	}

	@Override
	public boolean hasSoldOutRecord(Long showId) {
		Double score = redisTemplate.opsForZSet().score(RANKING_KEY, showId.toString());
		return score != null;
	}

	@Override
	public List<RankingEntry> getFastestSoldOut(int limit) {
		Set<ZSetOperations.TypedTuple<String>> rankings =
			redisTemplate.opsForZSet().rangeWithScores(RANKING_KEY, 0, limit - 1);

		if (rankings == null) {
			return List.of();
		}

		return rankings.stream()
			.map(tuple -> new RankingEntry(
				Long.parseLong(tuple.getValue()),
				tuple.getScore().longValue()
			))
			.collect(Collectors.toList());
	}

	@Override
	public Optional<Long> getRank(Long showId) {
		Long rank = redisTemplate.opsForZSet().rank(RANKING_KEY, showId.toString());
		return Optional.ofNullable(rank).map(r -> r + 1); // 0-based -> 1-based
	}

	@Override
	public Optional<Long> getSoldOutDuration(Long showId) {
		Double score = redisTemplate.opsForZSet().score(RANKING_KEY, showId.toString());
		return Optional.ofNullable(score).map(Double::longValue);
	}
}
