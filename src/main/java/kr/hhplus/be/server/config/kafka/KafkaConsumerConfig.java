package kr.hhplus.be.server.config.kafka;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Kafka Consumer 설정
 *
 * Spring Boot의 KafkaProperties를 사용하여 application.yml 설정 자동 주입
 */
@Configuration
public class KafkaConsumerConfig {

	private final KafkaProperties kafkaProperties;

	public KafkaConsumerConfig(KafkaProperties kafkaProperties) {
		this.kafkaProperties = kafkaProperties;
	}

	@Bean
	public ConsumerFactory<String, Object> consumerFactory() {
		return new DefaultKafkaConsumerFactory<>(
			kafkaProperties.buildConsumerProperties(null)
		);
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
		ConcurrentKafkaListenerContainerFactory<String, Object> factory =
			new ConcurrentKafkaListenerContainerFactory<>();

		factory.setConsumerFactory(consumerFactory());

		// Listener 설정 적용
		factory.getContainerProperties().setAckMode(
			kafkaProperties.getListener().getAckMode()
		);

		// Concurrency 설정
		Integer concurrency = kafkaProperties.getListener().getConcurrency();
		if (concurrency != null) {
			factory.setConcurrency(concurrency);
		}

		return factory;
	}
}
