package kr.hhplus.be.server.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
// unique 제약조건 추가로 중복된 요청 처리 방지
@Table(name = "point_history",
	uniqueConstraints = {
		@UniqueConstraint(name="uk_wallet_req", columnNames={"user_id","request_id"})
	})
public class PointHistoryEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "wallet_txn_id")
	public Long id;

	@Column(name = "user_id", nullable = false)
	public Long userId;

	@Column(name = "type", nullable = false, length = 16) // CHARGE/DEDUCT
	public String type;

	@Embedded
	public MoneyEmbeddable amount;

	@Embedded
	@AttributeOverrides(@AttributeOverride(name="amount", column=@Column(name="balance_after")))
	public MoneyEmbeddable balanceAfter;

	@Column(name = "request_id", nullable = false, length = 64)
	public String requestId;

	@Column(name = "payment_id", length = 64)
	public String paymentId;

	@Column(name = "created_at", nullable = false)
	public LocalDateTime createdAt;

	public PointHistoryEntity() {}
}
