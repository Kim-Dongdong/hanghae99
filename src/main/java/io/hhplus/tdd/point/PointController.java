package io.hhplus.tdd.point;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;

@RestController
@RequestMapping("/point")
public class PointController {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);
    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;

    public PointController(PointHistoryTable pointHistoryTable, UserPointTable userPointTable) {
        this.pointHistoryTable = pointHistoryTable;
        this.userPointTable = userPointTable;
    }

    /**
     * TODO - 특정 유저의 포인트를 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}")
    public UserPoint point(
            @PathVariable long id
    ) {
        return userPointTable.selectById(id);
    }

    /**
     * TODO - 특정 유저의 포인트 충전/이용 내역을 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}/histories")
    public List<PointHistory> history(
            @PathVariable long id
    ) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    /**
     * TODO - 특정 유저의 포인트를 충전하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/charge")
    public UserPoint charge(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        UserPoint currentPoint = userPointTable.selectById(id); // 현재 유저의 포인트 조회

        pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis()); // 포인트 기록 생성

        long newAmount = currentPoint.point() + amount; // 업데이트된 포인트 양

        return userPointTable.insertOrUpdate(id, newAmount);// 포인트 업데이트 및 반환
    }

    /**
     * TODO - 특정 유저의 포인트를 사용하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/use")
    public UserPoint use(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        // 계산 금액 검증
        if (amount <= 0) {
            throw new IllegalArgumentException("계산 금액은 0원 초과이어야 합니다.");
        }

        UserPoint currentPoint = userPointTable.selectById(id);

        // 잔액 조회
        if (currentPoint.point() < amount) {
            throw new IllegalArgumentException("현재 잔액이 부족합니다.");
        }

        // 포인트 사용 기록(USE)
        pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis());

        // 잔액 차감
        long newAmount = currentPoint.point() - amount;

        // 사용자 포인트 업데이트 및 반환
        return userPointTable.insertOrUpdate(id, newAmount);
    }
}
