package kr.hhplus.be.server.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class MoneyEmbeddable {
	@Column(name="amount_krw", nullable=false)
	public long amount;

	protected MoneyEmbeddable() {}
	public MoneyEmbeddable(long amount) { this.amount = amount; }
}
