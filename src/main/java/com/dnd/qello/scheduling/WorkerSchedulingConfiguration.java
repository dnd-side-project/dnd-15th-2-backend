package com.dnd.qello.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
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

	// Spring이 관례로 찾는 taskScheduler 이름을 점유하지 않는다. 이 pool은 worker
	// fixedDelay trigger 수에 맞춰 크기를 정하므로, 이름만 보고 연결되는 @Async나
	// 다른 @Scheduled 작업이 같은 pool을 잠식하면 worker 주기가 밀린다.
	@Bean
	ThreadPoolTaskScheduler workerTaskScheduler(WorkerSchedulingProperties properties) {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(properties.poolSize());
		scheduler.setThreadNamePrefix("qello-worker-");
		scheduler.setRemoveOnCancelPolicy(true);
		return scheduler;
	}

	// worker adapter의 @Scheduled를 이름 관례가 아니라 이 pool에 명시적으로 묶는다.
	@Bean
	SchedulingConfigurer workerSchedulingConfigurer(ThreadPoolTaskScheduler workerTaskScheduler) {
		return registrar -> registrar.setScheduler(workerTaskScheduler);
	}

	@Bean
	WorkerMetrics workerMetrics(MeterRegistry registry) {
		return new WorkerMetrics(registry);
	}
}
