package com.dnd.qello.filtering.moderation;

import java.time.Clock;
import java.time.Instant;

import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;

// 닉네임 동기 경로와 답변 비동기 경로가 공유하는 유일한 moderation 판정 진입점
// (GitHub #105). 정규화 → 고신뢰 로컬 규칙 → (미확정 시) 공급자 호출 → 내부 정책
// 결합 순서를 강제한다.
//
// 의도적으로 Spring 빈(@Service)이 아니다. 실제 TextNormalizer·LocalRuleEngine·
// PolicyEngine 구현체(정규화 규칙, 고신뢰 사전, category mapping·threshold)는
// 아직 결정되지 않았다 — 이 서비스를 컴포넌트 스캔 대상으로 등록하면 그 구현체
// 빈이 없어 애플리케이션 컨텍스트 전체가 기동하지 못한다. 닉네임(#106)·답변(#107)
// 경로가 실제 구현체를 확정한 뒤 각자 원하는 실행 자원으로 이 클래스를 직접
// 생성해 사용한다 — 그 결과 인스턴스 간 실행 자원이 자동으로 격리된다
// (INV-RES-001, INV-RES-002).
//
// 이 서비스는 트랜잭션을 직접 열지 않는다 — 공급자 호출(외부 I/O)을 DB 트랜잭션
// 안에 가두지 않기 위해서다. FilterDecision 저장은 FilterDecisionRepository
// 구현체가 자신의 트랜잭션 경계 안에서 처리한다.
public class ModerationPipelineService {

	private final TextNormalizer textNormalizer;
	private final LocalRuleEngine localRuleEngine;
	private final ModerationProviderClient providerClient;
	private final PolicyEngine policyEngine;
	private final FilterDecisionRepository filterDecisionRepository;
	private final Clock clock;

	public ModerationPipelineService(
		TextNormalizer textNormalizer,
		LocalRuleEngine localRuleEngine,
		ModerationProviderClient providerClient,
		PolicyEngine policyEngine,
		FilterDecisionRepository filterDecisionRepository,
		Clock clock
	) {
		this.textNormalizer = textNormalizer;
		this.localRuleEngine = localRuleEngine;
		this.providerClient = providerClient;
		this.policyEngine = policyEngine;
		this.filterDecisionRepository = filterDecisionRepository;
		this.clock = clock;
	}

	public ModerationPipelineResult execute(ModerationPipelineRequest request) {
		String normalized = textNormalizer.normalize(request.rawContent(), request.release().normalizationRef());
		LocalRuleVerdict ruleVerdict = localRuleEngine.evaluate(normalized, request.release().localRulesetRef());

		ModerationPipelineResult result = ruleVerdict.blocked()
			? shortCircuit(request, ruleVerdict)
			: evaluateWithProvider(request, normalized, ruleVerdict);

		if (request.filterJobId() != null) {
			persist(request, result);
		}
		return result;
	}

	private ModerationPipelineResult shortCircuit(ModerationPipelineRequest request, LocalRuleVerdict ruleVerdict) {
		return new ModerationPipelineResult(FilterVerdict.BLOCK, ruleVerdict, null, request.release().id(), null);
	}

	// 공급자 호출은 여기서만 발생한다. timeout/error는 providerClient 구현체가
	// FilteringException(MODERATION_PROVIDER_UNAVAILABLE)으로 던지며, 이 메서드는
	// 그 예외를 잡지 않고 그대로 전파한다 — 판정 불가를 임의의 verdict로 바꾸지 않는다.
	private ModerationPipelineResult evaluateWithProvider(
		ModerationPipelineRequest request, String normalized, LocalRuleVerdict ruleVerdict
	) {
		ModerationProviderResult providerResult =
			providerClient.moderate(normalized, request.release().modelSnapshot());
		FilterVerdict verdict = policyEngine.decide(
			providerResult, request.contentType(), request.language(), request.release().categoryMappingRef());
		return new ModerationPipelineResult(
			verdict, ruleVerdict, providerResult, request.release().id(), providerResult.actualModel());
	}

	private void persist(ModerationPipelineRequest request, ModerationPipelineResult result) {
		FilterDecision decision = FilterDecision.of(
			request.filterJobId(), request.attemptGeneration(), result.verdict(),
			request.release().id(), result.actualModel(), Instant.now(clock));
		filterDecisionRepository.save(decision);
	}
}
