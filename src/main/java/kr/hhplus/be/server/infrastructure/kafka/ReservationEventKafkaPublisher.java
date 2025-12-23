package kr.hhplus.be.server.infrastructure.kafka;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import kr.hhplus.be.server.domain.event.ReservationConfirmedEvent;

/**
 * Spring 도메인 이벤트를 Kafka 메시지로 변환하는 리스너
 *
 * 역할:
 * 1. 도메인 이벤트(ReservationConfirmedEvent) 수신
 * 2. Kafka 이벤트(ReservationConfirmedKafkaEvent)로 변환
 * 3. Kafka Producer를 통해 메시지 발행
 */
@Component
public class ReservationEventKafkaPublisher {

	private static final Logger log = LoggerFactory.getLogger(ReservationEventKafkaPublisher.class);

	private final ReservationKafkaProducer kafkaProducer;

	public ReservationEventKafkaPublisher(ReservationKafkaProducer kafkaProducer) {
		this.kafkaProducer = kafkaProducer;
	}

	/**
	 * 예약 확정 도메인 이벤트를 받아서 Kafka로 발행
	 *
	 * @TransactionalEventListener(phase = AFTER_COMMIT):
	 * - 트랜잭션이 성공적으로 커밋된 후에만 실행
	 * - 롤백되면 이벤트가 발행되지 않음
	 *
	 * @Async:
	 * - 비동기 처리
	 * - Kafka 전송 지연이 메인 트랜잭션에 영향을 주지 않음
	 * - @EnableAsync 설정 필요
	 */
	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleReservationConfirmed(ReservationConfirmedEvent domainEvent) {
		log.info("Received domain event - reservationId: {}, converting to Kafka event",
			domainEvent.reservationId());

		try {
			// 도메인 이벤트 -> Kafka 이벤트 변환
			ReservationConfirmedKafkaEvent kafkaEvent = new ReservationConfirmedKafkaEvent(
				domainEvent.reservationId(),
				domainEvent.userId(),
				domainEvent.showId(),
				domainEvent.seatNos(),
				domainEvent.amount().asLong(),  // Money를 Long으로 변환
				domainEvent.confirmedAt(),
				LocalDateTime.now()  // Kafka 발행 시각
			);

			// Kafka로 발행
			kafkaProducer.publishReservationConfirmed(kafkaEvent);

			log.info("Successfully converted and published to Kafka - reservationId: {}",
				domainEvent.reservationId());

		} catch (Exception e) {
			log.error("Failed to publish domain event to Kafka - reservationId: {}, error: {}",
				domainEvent.reservationId(), e.getMessage(), e);

			// 에러 처리 전략
			// 1. 현재는 로그만 남김
			// 2. 필요시 재시도 로직 추가
			// 3. Dead Letter Queue 또는 실패 테이블에 기록
			// 4. 모니터링 알림 전송
		}
	}

	/**
	 * 트랜잭션 커밋 전에 실행되는 버전 (선택적)
	 *
	 * BEFORE_COMMIT을 사용하면:
	 * - 트랜잭션 내에서 실행되므로 동기적
	 * - Kafka 전송 실패 시 트랜잭션 롤백 가능
	 * - 하지만 성능 영향이 큼
	 *
	 * 일반적으로는 AFTER_COMMIT을 권장
	 */
	// @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	// public void handleBeforeCommit(ReservationConfirmedEvent event) {
	//     // 트랜잭션 커밋 전 실행
	// }
}
