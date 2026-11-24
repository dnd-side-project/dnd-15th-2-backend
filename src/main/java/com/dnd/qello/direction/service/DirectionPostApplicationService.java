package com.dnd.qello.direction.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.direction.config.DirectionPostProperties;
import com.dnd.qello.direction.config.DirectionSchemeProperties;
import com.dnd.qello.direction.consent.LocationConsentService;
import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.gate.LocationCollectionGate;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;

import lombok.RequiredArgsConstructor;

/**
 * 인증 사용자와 서버 정책을 DirectionPostService command로 변환하는 application facade.
 *
 * <p>
 * web 입력에는 sender, 위치, 지역, 거리와 시각을 두지 않는다. 이 facade가 JWT subject에 해당하는 ACTIVE
 * USER, 현재 presence, 설정된 ACTIVE scheme과 서버 Clock을 해석한 뒤 하위 transaction service에
 * 전달한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectionPostApplicationService {

	private final AccountRepository accountRepository;
	private final ActiveUserPresenceRepository presenceRepository;
	private final DirectionSchemeRepository schemeRepository;
	private final DirectionSchemeProperties schemeProperties;
	private final DirectionPostPolicy policy;
	private final DirectionPostService postService;
	private final Clock clock;
	private final LocationCollectionGate locationCollectionGate;
	private final LocationConsentService locationConsentService;

	public DirectionPreviewResult preview(long senderId) {
		locationCollectionGate.ensureEnabled();
		locationConsentService.ensureConsented(senderId);
		Instant at = clock.instant();
		ensureActiveUser(senderId);
		currentPresence(senderId, at);
		DirectionScheme scheme = activeConfiguredScheme();
		return postService.previewAll(new DirectionPostService.PreviewAllCommand(senderId, scheme.getId(),
				policy.minDistanceMeters(), policy.maxDistanceMeters(), null, at));
	}

	// replay와 send가 각자 쓰기 트랜잭션을 연다. 클래스 read-only 트랜잭션에 합류하면 쓰기가 실패하므로
	// 진입 메서드는 트랜잭션 없이 실행한다.
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public DirectionPostService.SendResult submit(long senderId, String idempotencyKey, SubmitCommand command) {
		// 멱등 replay보다 먼저 거부한다. 게이트가 꺼진 뒤 같은 키 재요청에도 기존 결과를 돌려주지 않는다(D2).
		locationCollectionGate.ensureEnabled();
		locationConsentService.ensureConsented(senderId);
		if (command == null) {
			throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, "command",
					"질문글 제출 요청은 필수입니다");
		}
		validateIdempotencyKey(idempotencyKey);
		ensureActiveUser(senderId);
		var replay = postService.replayIfExists(senderId, idempotencyKey, command.approvedQuestionId(),
				command.schemeId(), command.segmentKey(), command.bodyText(), command.mediaIds());
		if (replay.isPresent()) {
			return replay.get();
		}
		Instant submittedAt = clock.instant();
		ActiveUserPresence presence = currentPresence(senderId, submittedAt);
		DirectionScheme scheme = activeConfiguredScheme();
		if (command.schemeId() != scheme.getId()) {
			throw new DirectionException(DirectionErrorCode.SCHEME_NOT_FOUND, "schemeId",
					"현재 활성 방향 구획 체계가 아닙니다");
		}
		DirectionPostPolicy.ValidatedContent content = policy.validateContent(command.bodyText(), command.mediaIds());
		return postService.send(new DirectionPostService.SendCommand(
				senderId,
				command.approvedQuestionId(), scheme.getId(), command.segmentKey(),
				policy.minDistanceMeters(), policy.maxDistanceMeters(), presence.getCoarseRegionCode(),
				idempotencyKey, content.bodyText(), content.mediaIds(), submittedAt, policy.expiresAt(submittedAt)));
	}

	private Account ensureActiveUser(long senderId) {
		if (senderId <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, "senderId",
					"인증 사용자 식별자가 유효하지 않습니다");
		}
		Account account = accountRepository.findById(senderId)
				.orElseThrow(() -> new DirectionException(DirectionErrorCode.PRESENCE_ACCOUNT_NOT_FOUND, "senderId",
						"방향 기능을 사용할 계정을 찾을 수 없습니다"));
		if (account.getRole() != AccountRole.USER || account.getStatus() != AccountStatus.ACTIVE) {
			throw new DirectionException(DirectionErrorCode.PRESENCE_ACCOUNT_NOT_ELIGIBLE, "senderId",
					"현재 계정은 방향 기능을 사용할 수 없습니다");
		}
		return account;
	}

	private ActiveUserPresence currentPresence(long senderId, Instant at) {
		ActiveUserPresence presence = presenceRepository.findByUserId(senderId)
				.orElseThrow(() -> new DirectionException(DirectionErrorCode.PRESENCE_NOT_FOUND, "senderId",
						"발신자의 위치 정보가 없습니다"));
		if (!presence.hasCurrentLocationAt(at)) {
			throw new DirectionException(DirectionErrorCode.PRESENCE_NOT_CURRENT, "senderId",
					"현재 위치 정보가 유효하지 않습니다");
		}
		return presence;
	}

	private DirectionScheme activeConfiguredScheme() {
		DirectionScheme scheme = schemeRepository.findActiveByCode(schemeProperties.schemeCode())
				.orElseThrow(() -> new DirectionException(DirectionErrorCode.SCHEME_NOT_FOUND, "schemeCode",
						"현재 활성 방향 구획 체계를 찾을 수 없습니다"));
		if (scheme.getId() == null) {
			throw new DirectionException(DirectionErrorCode.SCHEME_NOT_FOUND, "schemeCode",
					"현재 활성 방향 구획 체계가 유효하지 않습니다");
		}
		return scheme;
	}

	private static void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
			throw new DirectionException(DirectionErrorCode.INVALID_TEXT, "idempotencyKey",
					"Idempotency-Key는 1~200자여야 합니다");
		}
	}

	public record SubmitCommand(Long approvedQuestionId, Long schemeId, String segmentKey,
			String bodyText, List<Long> mediaIds) {
		public SubmitCommand {
			if (approvedQuestionId == null || approvedQuestionId <= 0 || schemeId == null || schemeId <= 0) {
				throw new DirectionException(DirectionErrorCode.INVALID_ID, null, "질문과 방향 식별자가 유효하지 않습니다");
			}
			if (segmentKey == null || segmentKey.isBlank()) {
				throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, "segmentKey",
						"segmentKey는 필수입니다");
			}
			mediaIds = mediaIds == null
					? List.of()
					: java.util.Collections.unmodifiableList(new ArrayList<>(mediaIds));
		}
	}
}
