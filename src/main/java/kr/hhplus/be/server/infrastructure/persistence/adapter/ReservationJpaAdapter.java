package kr.hhplus.be.server.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.model.Money;
import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.exceptions.ReservationExpiredException;
import kr.hhplus.be.server.domain.port.ReservationPort;
import kr.hhplus.be.server.infrastructure.persistence.entity.MoneyEmbeddable;
import kr.hhplus.be.server.infrastructure.persistence.entity.ReservationEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.ReservationSeatEntity;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringReservationJpa;
import kr.hhplus.be.server.infrastructure.persistence.springdata.SpringReservationSeatJpa;

@Component
public class ReservationJpaAdapter implements ReservationPort {
	private final SpringReservationJpa jpa;
	private final SpringReservationSeatJpa reservationSeatJpa;

	public ReservationJpaAdapter(SpringReservationJpa reservationJpa,
		SpringReservationSeatJpa reservationSeatJpa) {
		this.jpa = reservationJpa;
		this.reservationSeatJpa = reservationSeatJpa;
	}

	/** reservationId로 특정 예약 조회 **/
	@Override
	@Transactional(readOnly = true)
	public Optional<Reservation> findById(Long reservationId) {
		return jpa.findById(reservationId).map(this::toDomain);
	}

	/** 수정을 위해 reservationId로 특정 예약 정보를 조회 **/
	@Override
	@Transactional
	public Optional<Reservation> findByIdForUpdate(Long reservationId) {
		return jpa.findByIdForUpdate(reservationId).map(this::toDomain);
	}

	/** 저장 **/
	@Override
	@Transactional
	public Reservation save(Reservation reservation) {
		ReservationEntity e = toEntity(reservation);
		ReservationEntity saved = jpa.save(e);
		// 역주입
		reservation.assignId(saved.id);
		return toDomain(saved);
	}

	// ===== 매핑 =====
	private ReservationEntity toEntity(Reservation d) {
		ReservationEntity e = new ReservationEntity();
		e.id = d.getReservationId();
		e.userId = d.getUserId();
		e.showId = d.getShowId();
		e.status = d.getStatus().name();
		e.expiresAt = d.getExpiresAt();
		e.payableAmount = new MoneyEmbeddable(d.getPayableAmount().asLong());
		e.createdAt = d.getCreatedAt();
		e.seats.clear();
		for (Integer s : d.getSeatNos()) {
			var rs = new ReservationSeatEntity(s);
			rs.reservation = e;
			e.seats.add(rs);
		}
		return e;
	}

	private Reservation toDomain(ReservationEntity e) {
		List<Integer> seats = e.seats.stream().map(rs -> rs.seatNo).toList();
		return Reservation.rehydrate(
			e.id, e.userId, e.showId, seats,
			Reservation.Status.valueOf(e.status),
			e.expiresAt,
			Money.of(e.payableAmount.amount),
			e.createdAt
		);
	}

	@Override
	public boolean existsByUserIdAndShowIdAndExpiresAtBefore(long userId, long showId, LocalDateTime now) {
		return jpa.existsByUserIdAndShowIdAndExpiresAtBefore(userId, showId, now);
	}

	@Override
	public void markConfirmed(Long userId, Long showId, List<Integer> seatNos) {
		ReservationEntity res = jpa
			.findTopByUserIdAndShowIdAndStatusOrderByCreatedAtDesc(userId, showId, "PENDING")
			.orElseThrow(() -> new ReservationExpiredException(1L));

		// 2) 만료 검사
		if (res.expiresAt != null && res.expiresAt.isBefore(LocalDateTime.now())) {
			throw new ReservationExpiredException(res.id);
		}

		// 3) 좌석 일치 검증
		List<ReservationSeatEntity> current = reservationSeatJpa.findByReservationId(res.id);
		Set<Integer> have = new HashSet<>();
		for (ReservationSeatEntity rs : current) have.add(rs.seatNo);

		if (!have.containsAll(seatNos)) {
			throw new IllegalStateException("Seat list mismatch. resId=" + res.id + ", have=" + have + ", want=" + seatNos);
		}

		// 4) 상태 변경
		res.status = "CONFIRMED";
		res.expiresAt = null;
		jpa.save(res);
	}
}
