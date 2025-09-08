package kr.hhplus.be.server.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.hhplus.be.server.application.usecase.HoldSeatUseCase;
import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.exceptions.SeatAlreadyHeldException;
import kr.hhplus.be.server.domain.port.ReservationRepository;
import kr.hhplus.be.server.domain.port.SeatInventoryPort;

/** 좌석 홀드 유스케이스 단위 테스트 **/
@ExtendWith(MockitoExtension.class)
public class HoldSeatUseCaseTest {

	@Mock
	SeatInventoryPort seatInventory;
	@Mock
	ReservationRepository reservationRepository;

	@Test
	@DisplayName("좌석 홀드 성공 시 Reserviation 저장 및 결과 반환")
	public void hold_success() {
		// given
		long ttlSeconds = 120;
		HoldSeatUseCase sut = new HoldSeatUseCase(seatInventory, reservationRepository, ttlSeconds);

		Long userId = 10L;
		Long scheduleId = 100L;
		Integer seatNo = 17;

		// 좌석 점유가 성공한 상황을 가정
		when(seatInventory.tryHold(scheduleId, seatNo, ttlSeconds)).thenReturn(true);
		// 좌석 가격을 17000원으로 가정
		when(seatInventory.seatPriceOf(scheduleId, seatNo)).thenReturn(Money.of(17000));

		// ArgumentCaptor를 사용해 reservationRepository.save() 메서드가 호출될 때
		// 전달되는 Reservation 객체를 캡처, cap 객체를 save해서 검증하는 느낌
		ArgumentCaptor<Reservation> cap = ArgumentCaptor.forClass(Reservation.class);
		when(reservationRepository.save(cap.capture()))
			// thenAnswer를 사용해 save 메서드 호출 시, 데이터베이스에 저장된 후 ID가 부여된 상황을 시뮬레이션
			.thenAnswer(inv -> {
				// 저장을 가정: reservationId를 부여한 도메인 객체 반환
				Reservation r = cap.getValue();
				// rehydrate로 새로 만듦
				return Reservation.rehydrate(
					1L, r.getUserId(), r.getShowId(), r.getSeatNos(),
					r.getStatus(), r.getExpiresAt(), r.getPayableAmount(), r.getCreatedAt()
				);
			});

		// when
		HoldSeatUseCase.Result result = sut.handle(userId, scheduleId, seatNo);

		// then
		assertEquals(1L, result.reservationId());
		assertEquals(17000, result.amount().asLong());
		assertTrue(result.expiresAt().isAfter(LocalDateTime.now()));

		// 호출되었는지 확인
		verify(seatInventory).tryHold(scheduleId, seatNo, ttlSeconds);
		verify(seatInventory).seatPriceOf(scheduleId, seatNo);
		verify(reservationRepository).save(any());
		verifyNoMoreInteractions(seatInventory, reservationRepository);
	}

	@Test
	@DisplayName("좌석이 이미 점유 중이면 SeatAlreadyHeldException 발생")
	public void hold_conflict() {
		// given
		long ttlSeconds = 60;
		HoldSeatUseCase sut = new HoldSeatUseCase(seatInventory, reservationRepository, ttlSeconds);

		Long userId = 10L;
		Long scheduleId = 100L;
		Integer seatNo = 17;

		when(seatInventory.tryHold(scheduleId, seatNo, ttlSeconds)).thenReturn(false);

		// when & then
		assertThrows(SeatAlreadyHeldException.class,
			() -> sut.handle(userId, scheduleId, seatNo));

		// price 조회/저장은 시도 안 함
		verify(seatInventory).tryHold(scheduleId, seatNo, ttlSeconds);
		verifyNoMoreInteractions(seatInventory, reservationRepository);
	}
}
