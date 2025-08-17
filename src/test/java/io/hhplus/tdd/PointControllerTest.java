package io.hhplus.tdd;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.web.servlet.function.RequestPredicates.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointController;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;

@WebMvcTest(PointController.class)
public class PointControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean // 외부 의존성(Mock) 처리
	private UserPointTable userPointTable;

	@MockBean // 외부 의존성(Mock) 처리
	private PointHistoryTable pointHistoryTable;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("GET /point/{id} : 특정 유저의 현재 포인트 조회")
	void getPoint_returnsCurrentUserPoint() throws Exception {

		// given
		long userId = 1;
		long pointAmount = 500L;

		UserPoint userPoint = new UserPoint(userId, pointAmount, 1700000000000L);

		// 조건 설정
		when(userPointTable.selectById(userId)).thenReturn(userPoint);

		// when, then
		mockMvc.perform(get("/point/{id}", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(userId))
			.andExpect(jsonPath("$.point").value(500))
			.andExpect(jsonPath("$.updateMillis").value(1700000000000L));

		// userPointTable이 userId로 1번 호출됬는지
		verify(userPointTable, times(1)).selectById(userId);

		// pointHistoryTable와 userPointTable 이외의 상호작용은 없어야함
		verifyNoMoreInteractions(userPointTable, pointHistoryTable);
	}

	@Test
	@DisplayName("GET /point/{id}/histories : 특정 유저의 충전/사용 내역 조회")
	void getHistories_returnsList() throws Exception {
		// given
		// userId = 7
		long userId = 7L;

		// 두 개의 PointHistory 생성
		List<PointHistory> histories = List.of(
			new PointHistory(1L, userId, 300L, TransactionType.CHARGE, 1700000000001L),
			new PointHistory(2L, userId, 120L, TransactionType.USE,    1700000001001L)
		);

		// 조건 설정
		when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(histories);

		// when & then
		mockMvc.perform(get("/point/{id}/histories", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2))) // 총 2개의 PointHistory 검증
			.andExpect(jsonPath("$[0].id").value(1)) // 첫 번째 value
			.andExpect(jsonPath("$[0].userId").value(userId)) // userId = 7 검증
			.andExpect(jsonPath("$[0].amount").value(300)) // 가격 검증
			.andExpect(jsonPath("$[0].type").value("CHARGE")) // status 검증
			.andExpect(jsonPath("$[1].type").value("USE")); // status 검증

		// pointHistoryTable이 1번 userId(7)에 의해 호출되었는지
		verify(pointHistoryTable, times(1)).selectAllByUserId(userId);

		// pointHistoryTable 이외의 상호작용은 없어야함
		verifyNoMoreInteractions(pointHistoryTable);

		// userPointTable과는 상호작용하지 않아야함
		verifyNoInteractions(userPointTable);
	}

	@Test
	@DisplayName("PATCH /point/{id}/charge : 포인트 충전")
	void charge_createsHistory_andIncreasesBalance() throws Exception {

		// 1. 히스토리 기록이 먼저 생기고
		// 2. 잔액이 기존+충전액으로 갱신되어
		// 3. 갱신 결과가 응답으로 내려오는지 검증

		// given
		long userId = 10L;
		long chargeAmount = 200L;

		// 현재 잔액 700 조회 설정
		when(userPointTable.selectById(userId))
			.thenReturn(new UserPoint(userId, 700L, 1700000000000L));

		// pointHistoryTable.insert로 200L을 충전하면(thenReturn) 200L을 CHARGE한 PointHistory 반환 검증
		when(pointHistoryTable.insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong()))
			.thenReturn(new PointHistory(100L, userId, chargeAmount, TransactionType.CHARGE, 1700000000500L));

		// 업데이트 후 잔액 900 반환
		when(userPointTable.insertOrUpdate(userId, 900L))
			.thenReturn(new UserPoint(userId, 900L, 1700000000600L));

		// when, then
		mockMvc.perform(
				patch("/point/{id}/charge", userId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(String.valueOf(chargeAmount))
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(userId))
			.andExpect(jsonPath("$.point").value(900));

		// verify
		verify(userPointTable).selectById(userId);

		// 히스토리 기록이 먼저인것을 확인하기 위해 ArgumentCaptor 사용
		ArgumentCaptor<Long> tsCaptor = ArgumentCaptor.forClass(Long.class);
		verify(pointHistoryTable).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), tsCaptor.capture());

		// 잔액 반영 검증
		verify(userPointTable).insertOrUpdate(userId, 900L);

		// userPointTable와 pointHistoryTable 이외의 상호작용 x
		verifyNoMoreInteractions(userPointTable, pointHistoryTable);

		// 타임스탬프가 호출 시점의 숫자인지만 대략 체크(양수)
		assertThat(tsCaptor.getValue()).isPositive();
	}

	@Test
	@DisplayName("PATCH /point/{id}/use : 포인트 사용")
	void use_createsHistory_andDecreasesBalance() throws Exception {

		// 1. 현재 잔액 검증
		// 2. 히스토리(USE) 기록
		// 3. 잔액 차감 저장
		// 4. 업데이트 결과 반환

		// given
		long userId = 20L;
		long useAmount = 300L;

		// 현재 잔액 1000
		when(userPointTable.selectById(userId))
			.thenReturn(new UserPoint(userId, 1000L, 1700000000000L));

		// pointHistoryTable.insert 조건 추가
		when(pointHistoryTable.insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong()))
			.thenReturn(new PointHistory(200L, userId, useAmount, TransactionType.USE, 1700000000700L));

		// 차감 후 700
		when(userPointTable.insertOrUpdate(userId, 700L))
			.thenReturn(new UserPoint(userId, 700L, 1700000000800L));

		// when & then
		mockMvc.perform(
				patch("/point/{id}/use", userId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(String.valueOf(useAmount))
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(userId))
			.andExpect(jsonPath("$.point").value(700));

		// UserPoint(userId, 1000L, 1700000000000L) 반환 확인
		verify(userPointTable).selectById(userId);

		// PointHistory(200L, userId, useAmount, TransactionType.USE, 1700000000700L) 반환 확인
		verify(pointHistoryTable).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());

		// UserPoint(userId, 700L, 1700000000800L) 반환 확인, 차감되었는지 확인
		verify(userPointTable).insertOrUpdate(userId, 700L);

		// userPointTable, pointHistoryTable 이외의 상호작용 X
		verifyNoMoreInteractions(userPointTable, pointHistoryTable);
	}
}
