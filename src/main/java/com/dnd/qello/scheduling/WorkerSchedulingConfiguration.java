package com.dnd.qello.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.observability.WorkerMetrics;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(WorkerSchedulingProperties.class)
@ConditionalOnProperty(prefix = "qello.worker.scheduling", name = "enabled", havingValue = "true")
public class WorkerSchedulingConfiguration {

	@Bean
	WorkerInstanceIdentity workerInstanceIdentity() {
		return WorkerInstanceIdentity.random();
	}

	@Bean(name = "taskScheduler")
	ThreadPoolTaskScheduler taskScheduler(WorkerSchedulingProperties properties) {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(properties.poolSize());
		scheduler.setThreadNamePrefix("qello-worker-");
		scheduler.setRemoveOnCancelPolicy(true);
		return scheduler;
	}

	@Bean
	WorkerMetrics workerMetrics(MeterRegistry registry) {
		return new WorkerMetrics(registry);
	}
}
