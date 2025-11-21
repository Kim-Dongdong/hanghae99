package kr.hhplus.be.server.application;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import kr.hhplus.be.server.ServerApplication;
import kr.hhplus.be.server.application.usecase.ConfirmReservationUseCase;
import kr.hhplus.be.server.domain.model.exceptions.InsufficientBalanceException;
import kr.hhplus.be.server.domain.model.exceptions.ReservationExpiredException;
import kr.hhplus.be.server.infrastructure.persistence.entity.MoneyEmbeddable;
import kr.hhplus.be.server.infrastructure.persistence.entity.ReservationEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.SeatStateEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.ShowSeatEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.WalletEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringReservationJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringSeatStateJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringShowSeatJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringWalletJpa;

@SpringBootTest(classes = ServerApplication.class)
@ActiveProfiles("test")
@EntityScan(basePackages = "kr.hhplus.be.server.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "kr.hhplus.be.server.infrastructure.persistence.springdata")
class ConfirmReservationUseCaseTestDB {

	@Autowired ConfirmReservationUseCase confirmReservationUseCase;

	// --- Spring Data JPA repositories (프로젝트에 있는 인터페이스 이름과 패키지로 맞춰야 함)
	@Autowired SpringWalletJpa walletJpa;
	@Autowired SpringShowJpa showJpa;
	@Autowired SpringShowSeatJpa showSeatJpa;
	@Autowired SpringSeatStateJpa seatStateJpa;
	@Autowired SpringReservationJpa reservationJpa;

	// --- 테스트용 상수
	private static final long USER_ID_LOW = 11L;  // 잔액 부족 사용자
	private static final long USER_ID_OK  = 12L;  // 정상 사용자
	private static final long SHOW_ID     = 101L;

	@BeforeEach
	void seed() {
		// 정리(참조 순서 고려: 좌석상태 -> 예약 -> 쇼좌석 -> 쇼 -> 지갑)
		seatStateJpa.deleteAllInBatch();
		reservationJpa.deleteAllInBatch();
		showSeatJpa.deleteAllInBatch();
		showJpa.deleteAllInBatch();
		walletJpa.deleteAllInBatch();

		// 1) 쇼
		ShowEntity show = new ShowEntity();
		show.id = SHOW_ID;
		show.title = "테스트 쇼";
		show.startsAt = LocalDateTime.now().plusDays(1);
		show.salesOpenAt = LocalDateTime.now().minusDays(1);
		show.salesCloseAt = LocalDateTime.now().plusDays(2);
		show.timezone = "Asia/Seoul";
		show.venue = "테스트홀";
		show.seatCount = 100;
		show.holdMinutes = 5;
		showJpa.save(show);

		// 2) 쇼 좌석(1,2,3) + 좌석상태(기본 AVAILABLE)
		for (int seatNo : List.of(1, 2, 3)) {
			ShowSeatEntity seat = new ShowSeatEntity();
			seat.id = SHOW_ID * 1000 + seatNo; // PK
			seat.showId = SHOW_ID;      // 너 엔티티가 String showId 임
			seat.seatNo = seatNo;
			seat.seatLabel = "R-" + seatNo;
			seat.seatTier = "R";
			seat.basePrice = 20_000L;
			seat.isActive = true;
			showSeatJpa.save(seat);

			SeatStateEntity ss = new SeatStateEntity();
			ss.id = null; // identity
			ss.showId = SHOW_ID;
			ss.seatNo = seatNo;
			ss.status = "AVAILABLE"; // 프로젝트의 상태값(enum/문자열)에 맞춰 조정
			ss.version = null;
			seatStateJpa.save(ss);
		}

		// 3) 지갑 — 부족(10,000), 충분(100,000)
		WalletEntity wLow = new WalletEntity();
		wLow.id = USER_ID_LOW;
		wLow.balance = new MoneyEmbeddable(10_000L); // amount_krw=10,000
		wLow.version = null;
		wLow.updatedAt = LocalDateTime.now();
		walletJpa.save(wLow);

		WalletEntity wOk = new WalletEntity();
		wOk.id = USER_ID_OK;
		wOk.balance = new MoneyEmbeddable(100_000L);
		wOk.version = null;
		wOk.updatedAt = LocalDateTime.now();
		walletJpa.save(wOk);

		// 4) "예약 만료" 시나리오를 위한 만료 예약(좌석 3)
		ReservationEntity expired = new ReservationEntity();
		expired.id = null; // identity
		expired.userId = USER_ID_OK;
		expired.showId = SHOW_ID;
		expired.payableAmount = new MoneyEmbeddable(20_000L);
		expired.status = "PENDING"; // 확정 전 상태(프로젝트 값으로 조정)
		expired.createdAt = LocalDateTime.now().minusMinutes(10);
		expired.expiresAt = LocalDateTime.now().minusMinutes(1); // 이미 만료
		reservationJpa.save(expired);

		// 좌석 3을 예약 중 상태로 표시(로직이 seat 상태도 본다면)
		SeatStateEntity seat3 = seatStateJpa.findByShowIdAndSeatNo(SHOW_ID, 3).orElseThrow();
		seat3.status = "HELD"; // 프로젝트 상태값으로 조정
		seatStateJpa.save(seat3);
	}

	@Test
	@DisplayName("예매 확정 - 실패(잔액 부족)")
	void confirm_insufficientBalance() {
		int seatNo = 2; // 가격 20,000원
		assertThatThrownBy(() ->
			confirmReservationUseCase.confirm(USER_ID_LOW, SHOW_ID, List.of(seatNo))
		)
			.as("잔액이 모자라면 예매 확정 시 InsufficientBalanceException이 발생해야 한다")
			.isInstanceOf(InsufficientBalanceException.class);
	}

	@Test
	@DisplayName("예매 확정 - 실패(예약 만료)")
	void confirm_expired() {
		int seatNo = 3; // 만료 예약으로 세팅된 좌석
		assertThatThrownBy(() ->
			confirmReservationUseCase.confirm(USER_ID_OK, SHOW_ID, List.of(seatNo))
		)
			.as("만료된 예약이면 ReservationExpiredException(또는 프로젝트 정의 예외)이 발생해야 한다")
			.isInstanceOf(ReservationExpiredException.class);
	}
}
