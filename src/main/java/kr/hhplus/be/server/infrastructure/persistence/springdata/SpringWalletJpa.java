package kr.hhplus.be.server.infrastructure.persistence.springdata;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.hhplus.be.server.infrastructure.persistence.entity.WalletEntity;

public interface SpringWalletJpa extends JpaRepository<WalletEntity, Long> {
	@Query(value = "select * from wallets where user_id = :uid for update", nativeQuery = true)
	Optional<WalletEntity> findByUserIdForUpdate(@Param("uid") Long userId);
}
