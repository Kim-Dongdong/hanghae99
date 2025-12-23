package kr.hhplus.be.server.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;


/**
 * Kafka Consumer 서비스
 *
 * 예약 확정 이벤트를 소비하여 후속 처리를 수행
 *
 * 처리 예시:
 * 1. 알림 발송 (SMS, 이메일, 푸시)
 * 2. 데이터 분석 시스템으로 전달
 * 3. 외부 시스템 연동 (결제, 재고 등)
 * 4. 로그/감사 기록
 */
@Service
public class ReservationKafkaConsumer {

	private static final Logger log = LoggerFactory.getLogger(ReservationKafkaConsumer.class);

	/**
	 * 예약 확정 이벤트 소비
	 *
	 * @KafkaListener:
	 * - topics: 구독할 토픽 이름
	 * - groupId: Consumer Group ID
	 * - containerFactory: 사용할 리스너 컨테이너 팩토리
	 *
	 * @Payload: 메시지 본문
	 * @Header: 메시지 메타데이터
	 * Acknowledgment: 수동 커밋용 객체
	 */
	@KafkaListener(
		topics = "${kafka.topics.reservation-confirmed}",
		groupId = "${spring.kafka.consumer.group-id}",
		containerFactory = "kafkaListenerContainerFactory"
	)
	public void consumeReservationConfirmed(
		@Payload ReservationConfirmedKafkaEvent event,
		@Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
		@Header(KafkaHeaders.OFFSET) long offset,
		@Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
		ConsumerRecord<String, ReservationConfirmedKafkaEvent> record,
		Acknowledgment acknowledgment) {

		log.info("Received reservation confirmed event - " +
				"reservationId: {}, userId: {}, showId: {}, partition: {}, offset: {}, timestamp: {}",
			event.getReservationId(),
			event.getUserId(),
			event.getShowId(),
			partition,
			offset,
			timestamp);

		try {
			// ===== 비즈니스 로직 처리 =====

			// 1. 알림 발송
			sendNotification(event);

			// 2. 데이터 분석 시스템으로 전송
			sendToAnalytics(event);

			// 3. 외부 시스템 연동
			notifyExternalSystems(event);

			// 4. 로그/감사 기록
			logAuditTrail(event);

			// ===== 처리 완료 후 수동 커밋 =====
			acknowledgment.acknowledge();

			log.info("Successfully processed and acknowledged - " +
					"reservationId: {}, partition: {}, offset: {}",
				event.getReservationId(), partition, offset);

		} catch (Exception e) {
			log.error("Error processing reservation confirmed event - " +
					"reservationId: {}, partition: {}, offset: {}, error: {}",
				event.getReservationId(),
				partition,
				offset,
				e.getMessage(),
				e);

			// 에러 처리 전략
			// 1. 재시도: acknowledgment를 호출하지 않으면 다음 poll에서 다시 받음
			// 2. DLQ(Dead Letter Queue)로 전송
			// 3. 에러 로그 기록 후 ack (데이터 손실 방지)

			// 여기서는 일단 ack하여 무한 재시도 방지
			// 실제로는 재시도 카운트를 추적하고 DLQ로 보내는 것이 좋음
			acknowledgment.acknowledge();
		}
	}

	/**
	 * 알림 발송 처리
	 */
	private void sendNotification(ReservationConfirmedKafkaEvent event) {
		log.info("Sending notification to user {} for reservation {}",
			event.getUserId(), event.getReservationId());

		// TODO: 실제 알림 발송 로직
		// - SMS 발송
		// - 이메일 발송
		// - 푸시 알림 발송
		// - 웹소켓을 통한 실시간 알림

		// 예시:
		// notificationService.sendReservationConfirmed(
		//     event.getUserId(),
		//     event.getReservationId(),
		//     event.getShowId(),
		//     event.getSeatNos()
		// );
	}

	/**
	 * 데이터 분석 시스템으로 전송
	 */
	private void sendToAnalytics(ReservationConfirmedKafkaEvent event) {
		log.info("Sending analytics data for reservation {}", event.getReservationId());

		// TODO: 분석 시스템 연동
		// - Google Analytics 이벤트 전송
		// - 사내 데이터 웨어하우스로 전송
		// - 실시간 대시보드 업데이트

		// 예시:
		// analyticsService.trackReservationConfirmed(
		//     event.getUserId(),
		//     event.getShowId(),
		//     event.getAmountPaid()
		// );
	}

	/**
	 * 외부 시스템 연동
	 */
	private void notifyExternalSystems(ReservationConfirmedKafkaEvent event) {
		log.info("Notifying external systems for reservation {}", event.getReservationId());

		// TODO: 외부 시스템 호출
		// - 재고 관리 시스템 업데이트
		// - 결제 시스템 최종 확인
		// - CRM 시스템 업데이트
		// - 파트너사 API 호출

		// 예시:
		// inventoryService.confirmReservation(event.getShowId(), event.getSeatNos());
		// crmService.updateCustomerActivity(event.getUserId(), "RESERVATION_CONFIRMED");
	}

	/**
	 * 감사 로그 기록
	 */
	private void logAuditTrail(ReservationConfirmedKafkaEvent event) {
		log.info("Logging audit trail for reservation {}", event.getReservationId());

		// TODO: 감사 로그 저장
		// - 데이터베이스에 감사 로그 저장
		// - 로그 수집 시스템으로 전송
		// - 컴플라이언스 요구사항 충족

		// 예시:
		// auditLogRepository.save(new AuditLog(
		//     "RESERVATION_CONFIRMED",
		//     event.getUserId(),
		//     event.getReservationId(),
		//     event.getConfirmedAt()
		// ));
	}

	/**
	 * 배치 처리 Consumer (선택적)
	 *
	 * 여러 메시지를 한 번에 처리할 때 사용
	 * 처리량이 높을 때 효율적
	 */
	// @KafkaListener(
	//     topics = "${kafka.topics.reservation-confirmed}",
	//     groupId = "${spring.kafka.consumer.group-id}",
	//     containerFactory = "batchKafkaListenerContainerFactory"
	// )
	// public void consumeBatch(
	//     List<ReservationConfirmedKafkaEvent> events,
	//     Acknowledgment acknowledgment) {
	//
	//     log.info("Received batch of {} events", events.size());
	//
	//     try {
	//         for (ReservationConfirmedKafkaEvent event : events) {
	//             // 개별 이벤트 처리
	//             processEvent(event);
	//         }
	//
	//         acknowledgment.acknowledge();
	//         log.info("Successfully processed batch of {} events", events.size());
	//
	//     } catch (Exception e) {
	//         log.error("Error processing batch: {}", e.getMessage(), e);
	//         acknowledgment.acknowledge();  // 또는 부분 재시도 로직
	//     }
	// }
}
