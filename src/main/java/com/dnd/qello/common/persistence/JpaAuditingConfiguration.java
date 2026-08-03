package com.dnd.qello.common.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "jpaDateTimeProvider")
public class JpaAuditingConfiguration {

	@Bean
	@ConditionalOnMissingBean(Clock.class)
	Clock jpaClock() {
		return Clock.systemUTC();
	}

	@Bean
	DateTimeProvider jpaDateTimeProvider(Clock clock) {
		return () -> Optional.of(Instant.now(clock));
	}

}
