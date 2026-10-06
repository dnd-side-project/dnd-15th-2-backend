/*
 * Created at: 2026-10-06T14:26:52+09:00
 * Source scenario: TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-002 through UNIT-006
 */
package com.dnd.qello.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

	private static final Instant START = Instant.parse("2026-10-06T05:00:00Z");
	private static final Duration WINDOW = Duration.ofHours(1);

	@Test
	@DisplayName("UNIT-002: 한도 3이면 같은 키의 1~3번째 요청은 허용하고 4번째는 거절한다")
	void allowsUpToLimitAndRejectsNext() {
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(
				new RateLimitPolicy(3, WINDOW), Clock.fixed(START, ZoneOffset.UTC));

		assertThat(limiter.tryAcquire("a")).isTrue();
		assertThat(limiter.tryAcquire("a")).isTrue();
		assertThat(limiter.tryAcquire("a")).isTrue();
		assertThat(limiter.tryAcquire("a")).isFalse();
		assertThat(limiter.tryAcquire("a")).isFalse();
	}

	@Test
	@DisplayName("UNIT-003: 한 키의 한도를 다 써도 다른 키는 따로 센다")
	void countsKeysIndependently() {
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(
				new RateLimitPolicy(1, WINDOW), Clock.fixed(START, ZoneOffset.UTC));

		assertThat(limiter.tryAcquire("a")).isTrue();
		assertThat(limiter.tryAcquire("a")).isFalse();

		assertThat(limiter.tryAcquire("b")).isTrue();
	}

	@Test
	@DisplayName("UNIT-004: window 직전까지는 거절이 유지되고 window가 지나면 새 윈도로 1부터 다시 센다")
	void startsNewWindowExactlyAfterWindowElapses() {
		MutableClock clock = new MutableClock(START);
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(new RateLimitPolicy(2, WINDOW), clock);
		limiter.tryAcquire("a");
		limiter.tryAcquire("a");

		clock.set(START.plus(WINDOW).minusNanos(1));
		assertThat(limiter.tryAcquire("a")).isFalse();

		clock.set(START.plus(WINDOW));
		assertThat(limiter.tryAcquire("a")).isTrue();
		assertThat(limiter.tryAcquire("a")).isTrue();
		assertThat(limiter.tryAcquire("a")).isFalse();
	}

	@Test
	@DisplayName("UNIT-005: 32개 스레드가 같은 키로 동시에 200번 요청해도 정확히 한도만큼만 허용한다")
	void allowsExactlyLimitUnderConcurrency() throws Exception {
		int limit = 10;
		int threads = 32;
		int requests = 200;
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(
				new RateLimitPolicy(limit, WINDOW), Clock.fixed(START, ZoneOffset.UTC));
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				Callable<Boolean> attempt = () -> {
					start.await();
					return limiter.tryAcquire("shared");
				};
				results.add(executor.submit(attempt));
			}
			start.countDown();

			int allowed = 0;
			for (Future<Boolean> result : results) {
				if (result.get(10, TimeUnit.SECONDS)) {
					allowed++;
				}
			}
			assertThat(allowed).isEqualTo(limit);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("UNIT-006: window가 지난 뒤 다음 요청이 만료된 키를 정리해 추적 키 수가 줄어든다")
	void sweepsExpiredKeysAfterWindow() {
		MutableClock clock = new MutableClock(START);
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(new RateLimitPolicy(5, WINDOW), clock);
		for (int i = 0; i < 1_000; i++) {
			limiter.tryAcquire("key-" + i);
		}
		assertThat(limiter.trackedKeyCount()).isEqualTo(1_000);

		clock.set(START.plus(WINDOW));
		limiter.tryAcquire("fresh");

		assertThat(limiter.trackedKeyCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("UNIT-006: window가 지나지 않은 키는 정리 대상이 아니다")
	void keepsKeysStillInsideWindow() {
		MutableClock clock = new MutableClock(START);
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(new RateLimitPolicy(5, WINDOW), clock);
		limiter.tryAcquire("old");
		clock.set(START.plus(Duration.ofMinutes(30)));
		limiter.tryAcquire("recent");

		clock.set(START.plus(WINDOW));
		limiter.tryAcquire("fresh");

		assertThat(limiter.trackedKeyCount()).isEqualTo(2);
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		private MutableClock(Instant now) {
			this.now = now;
		}

		void set(Instant instant) {
			this.now = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
