# 콘서트 예약 서비스

항해 플러스 Lite 백엔드 2기에서 진행한 콘서트 좌석 예약 시스템입니다.  
TDD부터 대규모 트래픽 처리까지, 각 챕터별로 기술 요소를 점진적으로 쌓아올린 프로젝트입니다.

## Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.4, Spring Data JPA
- **Database**: MySQL, H2 (테스트)
- **Cache / Lock**: Redis, Redisson (분산락)
- **Messaging**: Apache Kafka (3-broker 클러스터)
- **Infra**: Docker Compose (App + MySQL + Redis + Kafka + Zookeeper)
- **Test**: JUnit 5, Testcontainers, Spring Kafka Test

## 프로젝트 구조

```
server/
├── domain/
│   ├── model/         # 순수 도메인 객체 (Money, PointWallet, Reservation, Show)
│   ├── port/          # 포트 인터페이스 (WalletPort, ReservationPort, SeatInventoryPort ...)
│   └── event/         # 도메인 이벤트
├── application/
│   └── usecase/       # 유스케이스 (좌석 홀드, 예약 확정, 포인트 충전, 매진 랭킹 ...)
├── infrastructure/
│   ├── persistence/   # JPA 엔티티, Spring Data Repository, Port 구현체(Adapter)
│   ├── kafka/         # Kafka Producer / Consumer
│   ├── event/         # Spring ApplicationEvent 핸들러
│   └── dataplatform/  # 외부 데이터 플랫폼 연동
├── interfaces/
│   └── web/           # REST Controller
└── config/            # Redis, Kafka, JPA, 분산락 설정
```

헥사고날 아키텍처를 적용하여 도메인 로직이 인프라에 의존하지 않도록 설계했습니다.

## 챕터별 학습 과정

각 챕터는 독립 브랜치에서 개발 후 PR을 통해 머지했습니다.

### Chapter 1 — TDD `ttd`
- 포인트 충전/사용 로직을 테스트 주도로 구현
- 단위 테스트 작성 → 구현 → 리팩토링 사이클 학습

### Chapter 2 — 클린 아키텍처 `CleanArchitecture`
- 헥사고날 아키텍처 전환: domain → port → adapter 계층 분리
- 불변 값객체 `Money` 설계, `Reservation` 상태 머신 (HELD → CONFIRMED/CANCELLED)
- `rehydrate` 패턴으로 인프라 ↔ 도메인 변환
- 단위 테스트, DB 통합 테스트, 기능 플로우 통합 테스트 작성

### Chapter 3 — 동시성 제어 `feat/concurrency-control`
- 동일 좌석 동시 홀드 시 1명만 성공하도록 조건부 UPDATE 쿼리로 제어
- `CountDownLatch` + `startGate` 패턴으로 동시 진입을 보장하는 테스트 작성
- 5가지 동시성 시나리오 검증 (같은 좌석/다른 좌석/홀드+확정 동시/대량 요청/확정 좌석 방어)

### Chapter 4 — 분산락 `feat/distributed-lock`
- Redisson `RLock` 기반 `DistributedLockManager` 구현
- 포인트 충전 로직에 분산락 적용, 동시 충전 시 잔액 정합성 보장
- 락 획득 실패/인터럽트 예외 처리

### Chapter 5 — Redis 캐싱 `feat/redis-cache`
- Redis Sorted Set으로 콘서트 매진 랭킹 시스템 구현
- 매진 기록 저장, Top N 조회, 개별 랭킹 조회 기능
- `RedisRankingAdapter` 테스트

### Chapter 6 — 이벤트 드리븐 아키텍처 `feat/event-driven-architecture`
- 예약 확정 시 `ApplicationEventPublisher`로 도메인 이벤트 발행
- `DataPlatformPort` + `HttpDataPlatformAdapter`로 외부 시스템 연동 분리
- 이벤트 핸들러 단위 테스트

### Chapter 7 — Kafka `feat/kafka-event`
- Spring Event → Kafka 전환: Producer/Consumer 구현
- userId 기반 파티셔닝으로 동일 사용자 메시지 순서 보장
- 수동 커밋(Acknowledgment) + 에러 처리 전략 설계

### Chapter 8 — 대규모 트래픽 `practice/high-traffic-spike`
- Docker Compose로 전체 인프라 구성 (Spring Boot + MySQL + Redis + Kafka 3-broker)
- JVM 튜닝 옵션 (`-Xmx2g`, G1GC) 적용
- k6 부하 테스트 스크립트 작성

## 테스트

```
총 20개 테스트 클래스

단위 테스트: MoneyTest, WalletTest, ReservationTest
UseCase 테스트: HoldSeatUseCaseTest, ConfirmReservationUseCaseTest, RechargePointUseCaseTest ...
통합 테스트: ReservationFeatureFlowIntegrationTest, SoldOutRankingFullFlowTest
동시성 테스트: HoldSeatConcurrencyIT, UsePointConcurrencyIT
인프라 테스트: RedisRankingAdapterTest, ReservationConfirmedEventHandlerTest
```

## 실행 방법

```bash
docker-compose up -d
```

## 브랜치 전략

| 브랜치 | 챕터 | PR |
|---|---|---|
| `ttd` | TDD | #1 |
| `CleanArchitecture` | 클린 아키텍처 | #2 |
| `feat/concurrency-control` | 동시성 제어 | #3 |
| `feat/distributed-lock` | 분산락 | #4 |
| `feat/redis-cache` | Redis 캐싱 | #5 |
| `feat/event-driven-architecture` | 이벤트 드리븐 | #6 |
| `feat/kafka-event` | Kafka | #7 |
| `practice/high-traffic-spike` | 대규모 트래픽 | #8 |
