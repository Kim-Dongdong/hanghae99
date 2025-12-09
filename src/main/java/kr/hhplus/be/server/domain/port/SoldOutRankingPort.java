package kr.hhplus.be.server.domain.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SoldOutRankingPort {


	 // 매진 기록 저장
	 // param: showId 콘서트 ID
	 // param: soldOutDurationSeconds 매진까지 걸린 시간(초)
	void recordSoldOut(Long showId, long soldOutDurationSeconds);

	// 이미 매진 기록이 있는지 확인
	boolean hasSoldOutRecord(Long showId);

	// 빠른 매진 Top N 조회
	// param: limit 조회할 개수
	// showId와 매진 시간 리스트 리턴
	List<RankingEntry> getFastestSoldOut(int limit);

	// 특정 콘서트의 랭킹 조회
    // 랭킹, 없으면 empty 반환
	Optional<Long> getRank(Long showId);

    // 특정 콘서트의 매진 시간 조회
	// 매진까지 걸린 시간(초), 없으면 empty 반환
	Optional<Long> getSoldOutDuration(Long showId);

	record RankingEntry(Long showId, long soldOutDurationSeconds) {}
}
