package kr.hhplus.be.server.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.List;
import kr.hhplus.be.server.domain.event.ReservationConfirmedEvent;
import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.port.DataPlatformPort;
import kr.hhplus.be.server.infrastructure.event.ReservationConfirmedEventHandler;

@ExtendWith(MockitoExtension.class)
public class ReservationConfirmedEventHandlerTest {

	private static final Logger logger = LoggerFactory.getLogger(ReservationConfirmedEventHandlerTest.class); // 로거 설정

	@Mock
	private DataPlatformPort dataPlatformPort;

	private ReservationConfirmedEventHandler handler;

	@BeforeEach
	void setup() {
		handler = new ReservationConfirmedEventHandler(dataPlatformPort);
	}

	@Test
	void testEventHandling() {
		// Given
		ReservationConfirmedEvent event = new ReservationConfirmedEvent(
			1L, 11L, 101L, List.of(5), Money.of(15000), LocalDateTime.now());

		// When
		logger.info("Handling event: {}", event);  // 이벤트 처리 시작 로그
		handler.onReservationConfirmed(event);

		// Then
		// 데이터 플랫폼 포트로 예약 정보가 전송되었는지 확인
		verify(dataPlatformPort).sendReservationConfirmed(any(DataPlatformPort.ReservationConfirmedPayload.class));

		// 이벤트 처리 후 확인 로그
		logger.info("Event handled and reservation sent to platform successfully.");
	}
}
