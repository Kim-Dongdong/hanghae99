/**
 * ========================================
 * 콘서트 예약 시스템 Spike Test (급격한 부하)
 * ========================================
 *
 * 이 스크립트는 실제 사용자처럼 HTTP 요청을 보냅니다:
 * 1. POST /api/reservations/hold - 좌석 홀드
 * 2. POST /api/reservations/{id}/confirm - 예약 확정 (Kafka 이벤트 발생!)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ========================================
// 커스텀 메트릭 (측정 항목)
// ========================================
const errorRate = new Rate('errors');                    // 에러 비율
const holdSuccessRate = new Rate('hold_success');        // 좌석 홀드 성공률
const confirmSuccessRate = new Rate('confirm_success');  // 예약 확정 성공률
const holdDuration = new Trend('hold_duration');         // 홀드 응답 시간
const confirmDuration = new Trend('confirm_duration');   // 확정 응답 시간
const kafkaEventCount = new Counter('kafka_events_triggered'); // Kafka 이벤트 발생 수

// ========================================
//  테스트 설정
// ========================================
export const options = {
    scenarios: {
        spike_test: {
            executor: 'ramping-vus',  // 가상 사용자(VU) 수를 점진적으로 증가
            startVUs: 0,
            stages: [
                { duration: '10s', target: 100 },   // 10초 동안 0 → 100명
                { duration: '5s', target: 500 },    // 5초 동안 100 → 500명 (급격한 증가!)
                { duration: '30s', target: 500 },   // 30초 동안 500명 유지
                { duration: '10s', target: 0 },     // 10초 동안 500 → 0명 (종료)
            ],
            gracefulRampDown: '10s',
        },
    },
    // 성공 기준 (thresholds)
    thresholds: {
        'http_req_duration': ['p(95)<3000'],      // 95%의 요청이 3초 이내
        'confirm_duration': ['avg<1000', 'p(95)<3000'],  // 확정: 평균 1초, 95% 3초 이내
        'hold_duration': ['avg<500', 'p(95)<1500'],      // 홀드: 평균 0.5초, 95% 1.5초 이내
        'errors': ['rate<0.05'],                  // 에러율 5% 미만
        'hold_success': ['rate>0.95'],            // 홀드 성공률 95% 이상
        'confirm_success': ['rate>0.90'],         // 확정 성공률 90% 이상
    },
};

// ========================================
// 서버 설정
// ========================================
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHOW_ID = 1;        // 테스트 대상 공연 ID
const TOTAL_SEATS = 50;   // 총 좌석 수

// ========================================
// 테스트 사용자 생성 함수
// ========================================
function generateTestUser() {
    return {
        userId: 100 + (__VU % 500),              // userId: 100-599 (500명)
        showId: SHOW_ID,                         // 공연 ID: 1
        seatNo: (__VU % TOTAL_SEATS) + 1,       // 좌석: 1-50번 (순환)
        initialBalance: 500000,                  // 초기 잔액 50만원
    };
}

// ========================================
// 메인 테스트 시나리오
// ========================================
// 이 함수가 각 가상 사용자(VU)마다 실행됩니다!
export default function (data) {
    const user = generateTestUser();
    const headers = { 'Content-Type': 'application/json' };

    // ====================================
    // 1단계: 좌석 홀드 API 호출
    // ====================================
    const holdStartTime = Date.now();

    // HTTP POST 요청 페이로드 (JSON)
    const holdPayload = JSON.stringify({
        userId: user.userId,
        scheduleId: user.showId,
        seatNo: user.seatNo,
    });

    // 실제 HTTP POST 요청!
    // 마치 Postman에서 보내는 것과 동일합니다
    const holdRes = http.post(
        `${BASE_URL}/api/reservations/hold`,
        holdPayload,
        {
            headers: headers,
            tags: { name: 'hold_seat' },  // 메트릭 태그
        }
    );

    // 응답 시간 측정
    const holdTime = Date.now() - holdStartTime;
    holdDuration.add(holdTime);

    // 응답 검증 (마치 assert처럼)
    const holdSuccess = check(holdRes, {
        'hold: status is 200': (r) => r.status === 200,
        'hold: has reservationId': (r) => {
            try {
                return r.json('reservationId') !== undefined;
            } catch (e) {
                return false;
            }
        },
        'hold: has expiresAt': (r) => {
            try {
                return r.json('expiresAt') !== undefined;
            } catch (e) {
                return false;
            }
        },
    });

    // 성공률 기록
    holdSuccessRate.add(holdSuccess ? 1 : 0);

    // 홀드 실패 시 여기서 중단
    if (!holdSuccess) {
        errorRate.add(1);
        console.error(` Hold failed - User: ${user.userId}, Seat: ${user.seatNo}, Status: ${holdRes.status}`);
        return;
    }

    // 홀드 성공 - reservationId 추출
    let reservationId;
    try {
        reservationId = holdRes.json('reservationId');
    } catch (e) {
        errorRate.add(1);
        console.error(` Failed to parse reservationId: ${holdRes.body}`);
        return;
    }

    // 실제 사용자처럼 잠깐 대기 (1-2초 랜덤)
    sleep(1 + Math.random());

    // ====================================
    // 2단계: 예약 확정 API 호출
    // ====================================
    // 여기서 Kafka 이벤트가 발생합니다!
    const confirmStartTime = Date.now();

    const confirmPayload = JSON.stringify({
        userId: user.userId,
        amount: 100000,  // 좌석 가격 10만원
        idempotencyKey: `idem_${__VU}_${__ITER}_${Date.now()}`,  // 중복 방지 키
    });

    // 실제 HTTP POST 요청!
    const confirmRes = http.post(
        `${BASE_URL}/api/reservations/${reservationId}/confirm`,
        confirmPayload,
        {
            headers: headers,
            tags: { name: 'confirm_reservation' },
        }
    );

    // 응답 시간 측정
    const confirmTime = Date.now() - confirmStartTime;
    confirmDuration.add(confirmTime);

    // 응답 검증
    const confirmSuccess = check(confirmRes, {
        'confirm: status is 200': (r) => r.status === 200,
        'confirm: has walletBalance': (r) => {
            try {
                return r.json('walletBalance') !== undefined;
            } catch (e) {
                return false;
            }
        },
        'confirm: status is OK': (r) => {
            try {
                return r.json('status') === 'OK';
            } catch (e) {
                return false;
            }
        },
        'confirm: response time < 3s': () => confirmTime < 3000,
    });

    // 성공률 기록
    confirmSuccessRate.add(confirmSuccess ? 1 : 0);

    if (confirmSuccess) {
        // 성공 시 Kafka 이벤트가 발행됨
        kafkaEventCount.add(1);
    } else {
        errorRate.add(1);
        console.error(` Confirm failed - ReservationId: ${reservationId}, Status: ${confirmRes.status}`);
    }

    // 짧은 대기
    sleep(0.5);
}

// ========================================
// 테스트 시작 전 실행 (Setup)
// ========================================
export function setup() {
    console.log('=== 테스트 환경 준비 ===');

    // 서버 헬스 체크
    const healthRes = http.get(`${BASE_URL}/actuator/health`);
    if (healthRes.status !== 200) {
        throw new Error(' 서버가 준비되지 않았습니다!');
    }
    console.log(' 서버 상태: 정상');

    return {
        startTime: Date.now(),
        baseUrl: BASE_URL,
    };
}

// ========================================
// 테스트 종료 후 실행 (Teardown)
// ========================================
export function teardown(data) {
    console.log('\n=== 테스트 종료 ===');

    // Kafka Consumer 처리 대기
    console.log(' Kafka Consumer 처리 대기 중 (30초)...');
    sleep(30);

    const endTime = Date.now();
    const duration = Math.round((endTime - data.startTime) / 1000);

    console.log(`️  총 테스트 시간: ${duration}초`);
    console.log(' 테스트 완료');
}

// ========================================
// 결과 요약 출력
// ========================================
export function handleSummary(data) {
    let summary = '\n========================================\n';
    summary += '        부하 테스트 결과 요약\n';
    summary += '========================================\n\n';

    // HTTP 요청 통계
    const httpReqs = data.metrics.http_reqs;
    if (httpReqs) {
        summary += ` 총 HTTP 요청: ${httpReqs.values.count}건\n`;
        summary += `   - 평균 응답시간: ${httpReqs.values.avg.toFixed(2)}ms\n`;
        summary += `   - 95 percentile: ${httpReqs.values['p(95)'].toFixed(2)}ms\n`;
    }

    // 좌석 홀드
    const holdSuccess = data.metrics.hold_success;
    if (holdSuccess) {
        summary += `\n 좌석 홀드:\n`;
        summary += `   - 성공률: ${(holdSuccess.values.rate * 100).toFixed(2)}%\n`;
    }

    const holdDur = data.metrics.hold_duration;
    if (holdDur) {
        summary += `   - 평균 처리시간: ${holdDur.values.avg.toFixed(2)}ms\n`;
        summary += `   - 95 percentile: ${holdDur.values['p(95)'].toFixed(2)}ms\n`;
    }

    // 예약 확정
    const confirmSuccess = data.metrics.confirm_success;
    if (confirmSuccess) {
        summary += `\n 예약 확정 (Kafka 이벤트):\n`;
        summary += `   - 성공률: ${(confirmSuccess.values.rate * 100).toFixed(2)}%\n`;
    }

    const confirmDur = data.metrics.confirm_duration;
    if (confirmDur) {
        summary += `   - 평균 처리시간: ${confirmDur.values.avg.toFixed(2)}ms\n`;
        summary += `   - 95 percentile: ${confirmDur.values['p(95)'].toFixed(2)}ms\n`;
    }

    // 에러율
    const errors = data.metrics.errors;
    if (errors) {
        summary += `\n 에러율: ${(errors.values.rate * 100).toFixed(2)}%\n`;
    }

    // Kafka 이벤트
    const kafkaEvents = data.metrics.kafka_events_triggered;
    if (kafkaEvents) {
        summary += `\n Kafka 이벤트 발행: ${kafkaEvents.values.count}건\n`;
    }

    summary += '\n========================================\n';

    console.log(summary);

    return {
        'stdout': summary,
        'summary.json': JSON.stringify(data, null, 2),
    };
}
