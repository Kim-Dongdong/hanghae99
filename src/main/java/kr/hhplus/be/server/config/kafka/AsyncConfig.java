package kr.hhplus.be.server.config.kafka;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 처리 설정
 *
 * @Async 어노테이션을 사용하기 위한 설정
 * 도메인 이벤트를 Kafka로 발행할 때 비동기로 처리
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	/**
	 * 이벤트 처리용 ThreadPool 설정
	 *
	 * 설정값:
	 * - Core Pool Size: 기본 스레드 수
	 * - Max Pool Size: 최대 스레드 수
	 * - Queue Capacity: 대기 큐 크기
	 */
	@Bean(name = "eventTaskExecutor")
	public Executor eventTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

		// 기본 스레드 수
		executor.setCorePoolSize(5);

		// 최대 스레드 수
		executor.setMaxPoolSize(10);

		// 대기 큐 크기
		executor.setQueueCapacity(100);

		// 스레드 이름 prefix
		executor.setThreadNamePrefix("event-async-");

		// 거부 정책: 호출자의 스레드에서 실행
		executor.setRejectedExecutionHandler(
			new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
		);

		// 종료 대기 설정
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);

		executor.initialize();
		return executor;
	}
}
