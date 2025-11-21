package kr.hhplus.be.server.config.manager;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class DistributedLockManager {
	private final RedissonClient redissonClient;

	public DistributedLockManager(RedissonClient redissonClient) {
		this.redissonClient = redissonClient;
	}

	/**
	 lockKey: redis에 저장될 락의 키
	 waitTime: 락을 얻기 위해 최대 몇 초를 기다릴 지
	 leaseTime: 락을 획득한 후 자동으로 해제될 시간 (데드락 방지)
	 timeUnit: 시간 단위task: 락을 획득한 후 실행할 비즈니스 로직
	**/
	public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime,
		TimeUnit timeUnit, Supplier<T> task) {
		RLock lock = redissonClient.getLock(lockKey);
		try {
			boolean acquired = lock.tryLock(waitTime, leaseTime, timeUnit);
			if (!acquired) {
				throw new IllegalStateException("락 획득 실패: " + lockKey);
			}
			return task.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("락 대기 중 인터럽트 발생", e);
		} finally {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}
}
