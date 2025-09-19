package kr.hhplus.be.server.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@TestConfiguration
@EnableJpaRepositories(basePackages = {
	"kr.hhplus.be.server.infrastructure.persistence.springdata"
})
@EntityScan(basePackages = {
	"kr.hhplus.be.server.infrastructure.persistence.entity"
})
public class TestJpaScanConfig {}
