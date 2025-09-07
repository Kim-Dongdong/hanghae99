package kr.hhplus.be.server.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "wallets")
public class WalletEntity {
	@Id
	@Column(name = "user_id")
	public Long userId;

	@Embedded
	public MoneyEmbeddable balance;

	@Version // 낙관적 락
	public Long version;

	@Column(name = "updated_at")
	public LocalDateTime updatedAt;

	protected WalletEntity() {}
}
