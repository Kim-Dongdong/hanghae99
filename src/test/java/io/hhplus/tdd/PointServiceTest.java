package io.hhplus.tdd;


import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

	@Mock
	PointHistoryTable pointHistoryTable;

	@Mock
	UserPointTable userPointTable;

	@InjectMocks
	PointService pointService;

	@Test
	@DisplayName("getPoint: 특정 유저의 현재 포인트 조회")
	void getPoint_returnsCurrentPoint() {
		long userId = 1L;
		UserPoint expected = new UserPoint(userId, 500L, 1700000000000L);
		when(userPointTable.selectById(userId)).thenReturn(expected);

		UserPoint actual = pointService.getPoint(userId);

		assertThat(actual).isEqualTo(expected);
		verify(userPointTable).selectById(userId);
		verifyNoMoreInteractions(userPointTable, pointHistoryTable);
	}

	@Test
	@DisplayName("getHistories: 특정 유저의 포인트 내역 조회")
	void getHistories_returnsList() {
		long userId = 7L;
		List<PointHistory> histories = List.of(
			new PointHistory(1L, userId, 300L, TransactionType.CHARGE, 1700000000001L),
			new PointHistory(2L, userId, 120L, TransactionType.USE,    1700000001001L)
		);
		when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(histories);

		List<PointHistory> actual = pointService.getHistories(userId);

		assertThat(actual).hasSize(2).containsExactlyElementsOf(histories);
		verify(pointHistoryTable).selectAllByUserId(userId);
		verifyNoMoreInteractions(pointHistoryTable, userPointTable);
	}

	@Test
	@DisplayName("charge: 충전 시 히스토리 → 잔액 업데이트 순서로 처리")
	void charge_recordsHistoryThenUpdatesBalance() {
		long userId = 10L;
		long chargeAmount = 200L;

		when(userPointTable.selectById(userId))
			.thenReturn(new UserPoint(userId, 700L, 1700000000000L));
		when(pointHistoryTable.insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong()))
			.thenReturn(new PointHistory(100L, userId, chargeAmount, TransactionType.CHARGE, 1700000000500L));
		when(userPointTable.insertOrUpdate(userId, 900L))
			.thenReturn(new UserPoint(userId, 900L, 1700000000600L));

		UserPoint result = pointService.charge(userId, chargeAmount);

		assertThat(result.point()).isEqualTo(900L);

		// 실행 순서 검증 / 조회 → 히스토리 → 업데이트
		InOrder inOrder = inOrder(userPointTable, pointHistoryTable);
		inOrder.verify(userPointTable).selectById(userId);
		ArgumentCaptor<Long> tsCaptor = ArgumentCaptor.forClass(Long.class);
		inOrder.verify(pointHistoryTable).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), tsCaptor.capture());
		inOrder.verify(userPointTable).insertOrUpdate(userId, 900L);

		assertThat(tsCaptor.getValue()).isPositive(); // timestamp 검증
	}

	@Test
	@DisplayName("charge: 0 이하 금액 충전 시 예외 발생")
	void charge_nonPositive_throws() {
		long userId = 10L;

		assertThatThrownBy(() -> pointService.charge(userId, 0))
			.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> pointService.charge(userId, -1))
			.isInstanceOf(IllegalArgumentException.class);

		// userPointTable와 pointHistoryTable 이외의 상호작용 x
		verifyNoInteractions(pointHistoryTable, userPointTable);
	}

	@Test
	@DisplayName("usePoint: 사용 시 히스토리 → 잔액 차감 순서로 처리")
	void use_recordsHistoryThenUpdatesBalance() {
		long userId = 20L;
		long useAmount = 300L;

		when(userPointTable.selectById(userId))
			.thenReturn(new UserPoint(userId, 1000L, 1700000000000L));
		when(pointHistoryTable.insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong()))
			.thenReturn(new PointHistory(200L, userId, useAmount, TransactionType.USE, 1700000000700L));
		when(userPointTable.insertOrUpdate(userId, 700L))
			.thenReturn(new UserPoint(userId, 700L, 1700000000800L));

		UserPoint result = pointService.usePoint(userId, useAmount);

		assertThat(result.point()).isEqualTo(700L);

		// 순서 확인 inOrder
		InOrder inOrder = inOrder(userPointTable, pointHistoryTable);
		inOrder.verify(userPointTable).selectById(userId);
		inOrder.verify(pointHistoryTable).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());
		inOrder.verify(userPointTable).insertOrUpdate(userId, 700L);
	}

	@Test
	@DisplayName("usePoint: 잔액 부족 시 예외 발생, 히스토리 기록 안함")
	void use_insufficientBalance_throws() {
		long userId = 20L;
		long useAmount = 300L;

		when(userPointTable.selectById(userId))
			.thenReturn(new UserPoint(userId, 100L, 1700000000000L));

		assertThatThrownBy(() -> pointService.usePoint(userId, useAmount))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("잔액이 부족");

		verify(userPointTable).selectById(userId);
		verifyNoInteractions(pointHistoryTable);
	}

	@Test
	@DisplayName("usePoint: 0 이하 금액 사용 시 예외 발생")
	void use_nonPositive_throws() {
		long userId = 30L;

		assertThatThrownBy(() -> pointService.usePoint(userId, 0))
			.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> pointService.usePoint(userId, -10))
			.isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(pointHistoryTable, userPointTable);
	}
}
