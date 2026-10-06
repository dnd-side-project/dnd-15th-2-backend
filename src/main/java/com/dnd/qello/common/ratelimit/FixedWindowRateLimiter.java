package com.dnd.qello.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// 키별 고정 윈도 카운터. 윈도는 키의 첫 요청 시각에서 시작하고 window가 지나면 처음부터 다시 센다.
//
// 카운터는 이 인스턴스 메모리에만 있다. 재시작하면 초기화되고 인스턴스끼리 공유하지 않는다(#315).
// 인스턴스를 늘리면 실제 한도가 인스턴스 수만큼 커지므로 그때는 공유 저장소로 옮겨야 한다.
public final class FixedWindowRateLimiter {

	private final RateLimitPolicy policy;
	private final Clock clock;
	private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

	// 만료된 키를 지울 다음 시각. 요청마다 전체를 훑지 않도록 window마다 한 번만 정리한다.
	private volatile Instant nextSweepAt;

	public FixedWindowRateLimiter(RateLimitPolicy policy, Clock clock) {
		this.policy = Objects.requireNonNull(policy, "policy");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.nextSweepAt = Instant.now(clock).plus(policy.window());
	}

	/**
	 * 요청 1회를 기록하고 한도 안이면 true를 돌려준다. 거절된 요청은 윈도를 늘리지 않는다.
	 */
	public boolean tryAcquire(String key) {
		Objects.requireNonNull(key, "key");
		Instant now = Instant.now(clock);
		sweepExpired(now);
		// compute는 키 단위로 원자적이다. 동시 요청이 같은 횟수를 읽고 함께 통과하는 일이 없다.
		Window window = windows.compute(key, (ignored, current) -> next(current, now));
		return window.count() <= policy.maxRequests();
	}

	int trackedKeyCount() {
		return windows.size();
	}

	private Window next(Window current, Instant now) {
		if (current == null || current.isExpiredAt(now, policy.window())) {
			return new Window(now, 1);
		}
		// 한도를 넘긴 뒤에는 더 세지 않는다. 거절된 요청까지 계속 더하면 int가 넘칠 수 있다.
		if (current.count() > policy.maxRequests()) {
			return current;
		}
		return new Window(current.start(), current.count() + 1);
	}

	private void sweepExpired(Instant now) {
		if (now.isBefore(nextSweepAt)) {
			return;
		}
		nextSweepAt = now.plus(policy.window());
		windows.values().removeIf(window -> window.isExpiredAt(now, policy.window()));
	}

	private record Window(Instant start, int count) {

		boolean isExpiredAt(Instant now, Duration length) {
			return !now.isBefore(start.plus(length));
		}
	}
}
