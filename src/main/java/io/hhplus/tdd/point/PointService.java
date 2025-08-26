package io.hhplus.tdd.point;

import java.util.List;

import org.springframework.stereotype.Service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PointService {

	private final PointHistoryTable pointHistoryTable;
	private final UserPointTable userPointTable;

	// 현재 포인트 조회
	public UserPoint getPoint(long userId) {
		return userPointTable.selectById(userId);
	}

	// 포인트 히스토리 조회
	public List<PointHistory> getHistories(long userId) {
		return pointHistoryTable.selectAllByUserId(userId);
	}

	// 포인트 충전
	public UserPoint charge(long userId, long amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("금액은 0보다 커야됩니다.");
		}

		UserPoint before = userPointTable.selectById(userId);
		boolean historyInserted = false;
		long ts = System.currentTimeMillis();

		try {
			// 1) 히스토리 먼저 기록
			pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, ts); // 포인트 기록 생성

			// 2) 잔액 갱신
			long newAmount = before.point() + amount;
			historyInserted = true;
			return userPointTable.insertOrUpdate(userId, newAmount);
		} catch (RuntimeException e) {
			// --- 보상(수동 롤백) ---
			throw e;
		}
	}

	// 포인트 사용
	public UserPoint usePoint(long userId, long amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("금액은 0보다 커야됩니다.");
		}

		UserPoint before = userPointTable.selectById(userId);
		if (before.point() < amount) {
			throw new IllegalArgumentException("잔액이 부족합니다.");
		}

		long ts = System.currentTimeMillis();

		// 포인트 사용 기록
		pointHistoryTable.insert(userId, amount, TransactionType.USE, ts);

		// 잔액 차감
		long newAmount = before.point() - amount;

		// 사용자 포인트 업데이트 및 반환
		return userPointTable.insertOrUpdate(userId, newAmount);
	}
}
