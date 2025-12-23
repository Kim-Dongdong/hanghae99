package kr.hhplus.be.server.infrastructure.kafka;


import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;


/**
 * Kafka Producer 서비스
 *
 * 예약 확정 이벤트를 Kafka 토픽으로 발행하는 책임을 가진 서비스
 *
 * 주요 기능:
 * 1. 메시지 발행
 * 2. 파티셔닝 전략 (userId 기반)
 * 3. 발행 성공/실패 로깅
 */
@Service
public class ReservationKafkaProducer {

	private static final Logger log = LoggerFactory.getLogger(ReservationKafkaProducer.class);

	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final String topicName;

	public ReservationKafkaProducer(
		KafkaTemplate<String, Object> kafkaTemplate,
		@Value("${kafka.topics.reservation-confirmed}") String topicName) {

		this.kafkaTemplate = kafkaTemplate;
		this.topicName = topicName;
	}

	/**
	 * 예약 확정 이벤트를 Kafka로 발행
	 *
	 * 파티셔닝 전략:
	 * - Key: userId를 문자열로 사용
	 * - 같은 userId의 메시지는 항상 같은 파티션으로 전송되어 순서 보장
	 *
	 * @param event 발행할 이벤트
	 */
	public void publishReservationConfirmed(ReservationConfirmedKafkaEvent event) {
		// 파티션 키: userId (같은 사용자의 예약은 같은 파티션으로)
		String key = String.valueOf(event.getUserId());

		log.info("Publishing reservation confirmed event to Kafka - " +
				"reservationId: {}, userId: {}, showId: {}, topic: {}",
			event.getReservationId(), event.getUserId(), event.getShowId(), topicName);

		// 비동기 전송
		CompletableFuture<SendResult<String, Object>> future =
			kafkaTemplate.send(topicName, key, event);

		// 전송 결과 처리
		future.whenComplete((result, ex) -> {
			if (ex == null) {
				// 성공
				var metadata = result.getRecordMetadata();
				log.info("Message sent successfully - " +
						"topic: {}, partition: {}, offset: {}, timestamp: {}, key: {}",
					metadata.topic(),
					metadata.partition(),
					metadata.offset(),
					metadata.timestamp(),
					key);
			} else {
				// 실패
				log.error("Failed to send message to Kafka - " +
						"reservationId: {}, userId: {}, error: {}",
					event.getReservationId(),
					event.getUserId(),
					ex.getMessage(),
					ex);

				// 실패 시 대응 전략
				// 1. 재시도는 Kafka Producer 설정의 retries로 자동 처리됨
				// 2. 여기서는 로그만 남기고, 필요시 별도 실패 처리 로직 추가 가능
				// 3. 예: Dead Letter Queue로 전송, DB에 실패 기록 등
			}
		});
	}

	/**
	 * 동기 방식 발행 (필요한 경우)
	 *
	 * 전송이 완료될 때까지 대기
	 * 성능은 떨어지지만 확실한 전송 보장이 필요할 때 사용
	 */
	public void publishReservationConfirmedSync(ReservationConfirmedKafkaEvent event)
		throws Exception {

		String key = String.valueOf(event.getUserId());

		log.info("Publishing reservation confirmed event to Kafka (SYNC) - " +
				"reservationId: {}, userId: {}",
			event.getReservationId(), event.getUserId());

		try {
			SendResult<String, Object> result =
				kafkaTemplate.send(topicName, key, event).get();  // 동기 대기

			var metadata = result.getRecordMetadata();
			log.info("Message sent successfully (SYNC) - " +
					"topic: {}, partition: {}, offset: {}",
				metadata.topic(), metadata.partition(), metadata.offset());

		} catch (Exception e) {
			log.error("Failed to send message to Kafka (SYNC) - " +
					"reservationId: {}, error: {}",
				event.getReservationId(), e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * 특정 파티션으로 직접 발행 (고급 사용)
	 *
	 * 파티션을 직접 지정해야 하는 특수한 경우에만 사용
	 */
	public void publishToPartition(ReservationConfirmedKafkaEvent event, int partition) {
		String key = String.valueOf(event.getUserId());

		log.info("Publishing to specific partition - " +
				"reservationId: {}, partition: {}",
			event.getReservationId(), partition);

		kafkaTemplate.send(topicName, partition, key, event)
			.whenComplete((result, ex) -> {
				if (ex == null) {
					log.info("Message sent to partition {} successfully", partition);
				} else {
					log.error("Failed to send to partition {}: {}",
						partition, ex.getMessage(), ex);
				}
			});
	}
}
