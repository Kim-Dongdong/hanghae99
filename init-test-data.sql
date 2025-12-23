-- ========================================
-- 콘서트 예약 시스템 부하 테스트용 데이터 초기화
-- ========================================
-- 인코딩 알림: 이 파일은 UTF-8로 저장되어야 합니다.
-- Get-Content -Encoding UTF8 init-test-data.sql | docker exec -i mysql mysql -u application -papplication hhplus --default-character-set=utf8mb4


USE hhplus;

-- ===== 기존 데이터 삭제 (정합성 보장) =====
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE point_history;
TRUNCATE TABLE reservation_seats;
TRUNCATE TABLE reservations;
TRUNCATE TABLE seat_state;
TRUNCATE TABLE show_seat;
TRUNCATE TABLE shows;
TRUNCATE TABLE wallets;

SET FOREIGN_KEY_CHECKS = 1;

-- ===== 1. 테스트 공연 데이터 =====
INSERT INTO shows (
    show_id,
    title,
    venue,
    starts_at,
    sales_open_at,
    sales_close_at,
    seat_count,
    hold_minutes,
    status,
    timezone,
    created_at
) VALUES (
             1,
             '부하 테스트 콘서트',
             '올림픽공원 KSPO DOME',
             '2025-12-31 19:00:00',
             '2024-12-01 00:00:00',
             '2025-12-30 23:59:59',
             50,
             2,
             'OPEN',
             'Asia/Seoul',
             NOW()
         );

-- ===== 2. 좌석 데이터 (50개) =====
INSERT INTO show_seat (
    show_seat_id,
    show_id,
    seat_no,
    seat_tier,
    seat_label,
    base_price,
    is_active
)
SELECT
    NULL,
    1,
    seat_no,
    CASE
        WHEN seat_no <= 10 THEN 'VIP'
        WHEN seat_no <= 30 THEN 'R'
        ELSE 'S'
        END,
    CONCAT(CASE WHEN seat_no <= 10 THEN 'VIP-' WHEN seat_no <= 30 THEN 'R-' ELSE 'S-' END, seat_no),
    CASE
        WHEN seat_no <= 10 THEN 150000
        WHEN seat_no <= 30 THEN 100000
        ELSE 50000
        END,
    1
FROM (
         SELECT 1 as seat_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL
         SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL
         SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL
         SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL
         SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25 UNION ALL
         SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30 UNION ALL
         SELECT 31 UNION ALL SELECT 32 UNION ALL SELECT 33 UNION ALL SELECT 34 UNION ALL SELECT 35 UNION ALL
         SELECT 36 UNION ALL SELECT 37 UNION ALL SELECT 38 UNION ALL SELECT 39 UNION ALL SELECT 40 UNION ALL
         SELECT 41 UNION ALL SELECT 42 UNION ALL SELECT 43 UNION ALL SELECT 44 UNION ALL SELECT 45 UNION ALL
         SELECT 46 UNION ALL SELECT 47 UNION ALL SELECT 48 UNION ALL SELECT 49 UNION ALL SELECT 50
     ) seats;

-- ===== 3. 좌석 상태 초기화 (50개 - 모두 AVAILABLE) =====
INSERT INTO seat_state (
    id,
    show_id,
    seat_no,
    status,
    expires_at,
    version
)
SELECT
    NULL,
    1,
    seat_no,
    'AVAILABLE',
    NULL,
    0
FROM (
         SELECT 1 as seat_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL
         SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL
         SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL
         SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL
         SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25 UNION ALL
         SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30 UNION ALL
         SELECT 31 UNION ALL SELECT 32 UNION ALL SELECT 33 UNION ALL SELECT 34 UNION ALL SELECT 35 UNION ALL
         SELECT 36 UNION ALL SELECT 37 UNION ALL SELECT 38 UNION ALL SELECT 39 UNION ALL SELECT 40 UNION ALL
         SELECT 41 UNION ALL SELECT 42 UNION ALL SELECT 43 UNION ALL SELECT 44 UNION ALL SELECT 45 UNION ALL
         SELECT 46 UNION ALL SELECT 47 UNION ALL SELECT 48 UNION ALL SELECT 49 UNION ALL SELECT 50
     ) seats;

-- ===== 4. 테스트 사용자 포인트 지갑 (500명) =====
INSERT INTO wallets (
    user_id,
    amount_krw,
    version,
    updated_at
)
SELECT
    100 + seq,
    500000,
    0,
    NOW()
FROM (
         SELECT @row := @row + 1 as seq
         FROM (
             SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
             SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
             ) t1,
             (
             SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
             SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
             ) t2,
             (
             SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
             SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
             ) t3,
             (SELECT @row := -1) r
     ) numbers
WHERE seq < 500;

-- ===== 5. 인덱스 최적화 =====
-- MySQL 구버전 호환을 위해 IF NOT EXISTS 제거
-- 이미 인덱스가 생성되어 있다면 에러가 발생할 수 있으나 무시해도 됩니다.
CREATE INDEX idx_show_id ON show_seat(show_id);
CREATE INDEX idx_show_seat_no ON seat_state(show_id, seat_no);
CREATE INDEX idx_user_id ON wallets(user_id);
CREATE INDEX idx_reservation_user_show ON reservations(user_id, show_id);
CREATE INDEX idx_seat_state_status ON seat_state(show_id, seat_no, status);

-- 테이블 통계 업데이트
ANALYZE TABLE shows, show_seat, seat_state, wallets;

-- ===== 6. 데이터 확인 (최종 요약만 출력) =====
SELECT 'DONE' as 'Status', 'Database ready for load test!' as 'Message';
