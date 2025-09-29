package kr.hhplus.be.server.infrastructure.persistence.springdata;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.hhplus.be.server.infrastructure.persistence.entity.ShowEntity;

public interface SpringShowJpa extends JpaRepository<ShowEntity, Long> {

}
