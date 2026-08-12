package com.dnd.qello.filtering.moderation;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome.Reason;

// 닉네임 설정·변경 전용 동기 fail-closed moderation 훅(GitHub #106).
// ModerationPipelineService(#105, 주 판정기)를 답변 경로와 분리된 실행 자원으로
// 호출하고, timeout/error일 때만 독립 보조 판정기를 순차 호출한다.
//
// 의도적으로 Spring 빈이 아니다 — 답변 경로와 실행 자원을 공유하지 않으려면
// (INV-RES-002~004) 호출자가 닉네임 전용 ExecutorService·주 판정기 인스턴스로
// 이 클래스를 직접 생성해야 한다. 이 이슈는 그 실제 호출 지점(Account.createUser/
// updateProfile)을 연결하지 않는다 — 훅 자체만 제공한다.
//
// 주 판정기의 명시적 BLOCK은 확정 결과다(INV-NICK-002) — 보조 판정기를 호출하지
// 않는다. 주 판정기가 timeout/error일 때만 보조 판정기를 호출하며(INV-NICK-003),
// 주·보조 모두 실패하면 REJECTED(UNAVAILABLE)로 fail-closed 거부한다
// (INV-NICK-005, INV-GEN-002) — 어떤 경로도 판정 불가를 ALLOWED로 바꾸지 않는다.
//
// primaryTimeout/secondaryTimeout은 각 단계를 개별적으로 경계 짓는다 — 주
// 판정기 내부의 RestClient 자체 timeout 설정과 무관하게, 이 게이트는 자신의
// 예산을 넘기면 Future를 취소하고 다음 단계로 넘어간다(defense in depth).
public class NicknameSyncModerationGate {

	private final ModerationPipelineService primaryPipeline;
	private final SecondaryModerationClient secondaryClient;
	private final ExecutorService executor;
	private final Duration primaryTimeout;
	private final Duration secondaryTimeout;
	private final FilterRelease release;

	public NicknameSyncModerationGate(
		ModerationPipelineService primaryPipeline,
		SecondaryModerationClient secondaryClient,
		ExecutorService executor,
		Duration primaryTimeout,
		Duration secondaryTimeout,
		FilterRelease release
	) {
		this.primaryPipeline = primaryPipeline;
		this.secondaryClient = secondaryClient;
		this.executor = executor;
		this.primaryTimeout = primaryTimeout;
		this.secondaryTimeout = secondaryTimeout;
		this.release = release;
	}

	public NicknameModerationOutcome evaluate(String nickname, ModerationLanguage language) {
		Future<ModerationPipelineResult> primaryFuture = executor.submit(() -> primaryPipeline.execute(
			ModerationPipelineRequest.ephemeral(FilterTargetType.NICKNAME, language, nickname, release)));

		ModerationPipelineResult primaryResult;
		try {
			primaryResult = primaryFuture.get(primaryTimeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			primaryFuture.cancel(true);
			return evaluateWithSecondary(nickname, language);
		} catch (Exception e) {
			// ExecutionException(주 판정기가 던진 모든 예외, 대개
			// FilteringException(MODERATION_PROVIDER_UNAVAILABLE))과
			// InterruptedException을 모두 "주 판정기 error"로 취급해 보조
			// 판정기로 넘어간다 — 어떤 예외도 여기서 ALLOW/BLOCK으로 바뀌지 않는다.
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return evaluateWithSecondary(nickname, language);
		}

		return toOutcome(primaryResult.verdict(), Reason.BLOCKED_BY_PRIMARY);
	}

	private NicknameModerationOutcome evaluateWithSecondary(String nickname, ModerationLanguage language) {
		Future<FilterVerdict> secondaryFuture = executor.submit(() -> secondaryClient.moderate(nickname, language));

		FilterVerdict secondaryVerdict;
		try {
			secondaryVerdict = secondaryFuture.get(secondaryTimeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			secondaryFuture.cancel(true);
			return NicknameModerationOutcome.rejected(Reason.UNAVAILABLE);
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return NicknameModerationOutcome.rejected(Reason.UNAVAILABLE);
		}

		return toOutcome(secondaryVerdict, Reason.BLOCKED_BY_SECONDARY);
	}

	private static NicknameModerationOutcome toOutcome(FilterVerdict verdict, Reason blockedReason) {
		return switch (verdict) {
			case ALLOW -> NicknameModerationOutcome.allowed();
			case BLOCK -> NicknameModerationOutcome.rejected(blockedReason);
		};
	}
}
