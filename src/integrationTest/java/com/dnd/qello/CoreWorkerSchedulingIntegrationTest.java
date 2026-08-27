/**
 * Created at: 2026-08-27T15:28:15+09:00
 * Source scenario: TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-INT-001, INT-002
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.scheduling.WorkerInstanceIdentity;
import com.dnd.qello.scheduling.adapter.DirectionMatchingScheduledAdapter;
import com.dnd.qello.scheduling.adapter.NotificationFanOutScheduledAdapter;
import com.dnd.qello.scheduling.adapter.PushDeliveryDispatchScheduledAdapter;
import com.dnd.qello.scheduling.adapter.RecipientExpirationSweepScheduledAdapter;
import com.dnd.qello.scheduling.adapter.RecipientNotificationFanOutScheduledAdapter;
import com.dnd.qello.scheduling.adapter.ReportResolutionFanOutScheduledAdapter;
import com.dnd.qello.scheduling.adapter.SkipConfirmationSweepScheduledAdapter;
import com.dnd.qello.scheduling.observability.WorkerMetrics;

@SpringBootTest
@ActiveProfiles("test")
class CoreWorkerSchedulingIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private Environment environment;

	@Test
	@DisplayName("INT-001: test profile의 기본 OFF는 scheduling infrastructure와 adapter를 등록하지 않는다")
	void testProfileDoesNotRegisterSchedulingBeans() {
		assertThat(environment.matchesProfiles("test")).isTrue();
		assertThat(applicationContext.getBeansOfType(WorkerInstanceIdentity.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(WorkerMetrics.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(ThreadPoolTaskScheduler.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(DirectionMatchingScheduledAdapter.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(RecipientNotificationFanOutScheduledAdapter.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(NotificationFanOutScheduledAdapter.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(ReportResolutionFanOutScheduledAdapter.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(RecipientExpirationSweepScheduledAdapter.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(SkipConfirmationSweepScheduledAdapter.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(PushDeliveryDispatchScheduledAdapter.class)).isEmpty();
	}
}

@SpringBootTest(properties = {
	"qello.notification.push.policy.bundle-window=PT10M",
	"qello.notification.push.policy.max-delay=PT8H",
	"qello.notification.push.policy.daily-limit=5",
	"qello.notification.push.policy.direction-reserved=2",
	"qello.notification.push.policy.recommendation-min-interval=PT24H"
})
@ActiveProfiles("local")
class CoreWorkerSchedulingLocalProfileIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private Environment environment;

	@Test
	@DisplayName("INT-002: local profile의 기본 OFF는 scheduling infrastructure와 adapter를 등록하지 않는다")
	void localProfileDoesNotRegisterSchedulingBeans() {
		assertThat(environment.matchesProfiles("local")).isTrue();
		assertThat(applicationContext.getBeansOfType(WorkerInstanceIdentity.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(WorkerMetrics.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(ThreadPoolTaskScheduler.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(DirectionMatchingScheduledAdapter.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(RecipientNotificationFanOutScheduledAdapter.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(NotificationFanOutScheduledAdapter.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(ReportResolutionFanOutScheduledAdapter.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(RecipientExpirationSweepScheduledAdapter.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(SkipConfirmationSweepScheduledAdapter.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(PushDeliveryDispatchScheduledAdapter.class)).isEmpty();
	}
}
