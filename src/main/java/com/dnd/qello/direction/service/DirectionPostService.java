package com.dnd.qello.direction.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionCandidate;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionRequestFingerprint;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSchemeStatus;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.domain.PostAudience;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.repository.PostAudienceRepository;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository.DirectionSegmentCandidateCount;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * 방향 글 제출과 질문자 측 읽음 표시를 소유한다.
 * 방향 preview는 참고값으로만 사용하고 제출 transaction은 수신자 확정 worker에
 * MatchRequested 작업을 위임한다.
 */
@Service
@RequiredArgsConstructor
public class DirectionPostService {

	private final DirectionSchemeRepository schemeRepository;
	private final ActiveUserPresenceRepository presenceRepository;
	private final DirectionPostRepository postRepository;
	private final PostAudienceRepository audienceRepository;
	private final ApprovedQuestionRepository approvedQuestionRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final PlatformTransactionManager transactionManager;

	@Transactional(readOnly = true)
	public List<DirectionCandidate> preview(PreviewCommand command) {
		requireValue(command, "command");
		ActiveUserPresence sender = activeSender(command.senderId(), command.at());
		DirectionSegment segment = segment(command.schemeId(), command.segmentKey());
		return candidates(command, sender, segment);
	}

	@Transactional(readOnly = true)
	public DirectionPreviewResult previewAll(PreviewAllCommand command) {
		requireValue(command, "command");
		ActiveUserPresence sender = activeSender(command.senderId(), command.at());
		DirectionScheme scheme = activeScheme(command.schemeId());
		List<DirectionSegment> segments = schemeRepository.findSegments(command.schemeId()).stream()
				.sorted(java.util.Comparator.comparingInt(DirectionSegment::getSortOrder))
				.toList();
		scheme.validateCoverage(segments);
		if (sender.getLatitude() == null || sender.getLongitude() == null) {
			throw new DirectionException(
					DirectionErrorCode.PRESENCE_LOCATION_MISSING,
					"senderId",
					"정확 위치가 없는 presence는 후보를 계산할 수 없습니다"
			);
		}

		List<DirectionSegmentCandidateCount> rows = presenceRepository.findCandidateCountsBySegment(
				command.schemeId(), command.senderId(), sender.getLatitude().doubleValue(), sender.getLongitude().doubleValue(),
				command.minDistanceMeters(), command.maxDistanceMeters(), command.at(), command.coarseRegionCode());
		Map<String, Long> counts = new HashMap<>();
		for (DirectionSegmentCandidateCount row : rows) {
			if (counts.put(row.segmentKey(), row.count()) != null) {
				throw new DirectionException(DirectionErrorCode.INVALID_SCHEME_CONFIGURATION, "segments",
						"preview 집계 결과에 같은 segment가 중복되었습니다");
			}
		}
		List<DirectionPreviewResult.SegmentCount> result = segments.stream()
				.map(segment -> new DirectionPreviewResult.SegmentCount(segment.getSegmentKey(), segment.getDisplayName(),
						segment.getSortOrder(), counts.getOrDefault(segment.getSegmentKey(), 0L)))
				.toList();
		return new DirectionPreviewResult(scheme.getId(), scheme.getCode(), scheme.getVersion(), result);
	}

	public SendResult send(SendCommand command) {
		requireValue(command, "command");
		DirectionRequestFingerprint requestFingerprint = DirectionRequestFingerprint.create(command.approvedQuestionId(),
				command.schemeId(), command.segmentKey(), command.minDistanceMeters(), command.maxDistanceMeters(),
				command.coarseRegionCode(), command.bodyText());
		try {
			TransactionTemplate transaction = new TransactionTemplate(transactionManager);
			return transaction.execute(status -> sendInTransaction(command, requestFingerprint));
		} catch (DataIntegrityViolationException race) {
			return recoverConcurrentRequest(command, requestFingerprint, race);
		}
	}

	private SendResult sendInTransaction(SendCommand command, DirectionRequestFingerprint requestFingerprint) {
		var existing = postRepository.findBySenderAndIdempotencyKey(command.senderId(), command.idempotencyKey());
		if (existing.isPresent()) {
			DirectionPost existingPost = existing.get();
			if (existingPost.getRequestFingerprint() != null && !existingPost.getRequestFingerprint().equals(requestFingerprint)) {
				throw new DirectionException(DirectionErrorCode.IDEMPOTENCY_KEY_REUSED, "idempotencyKey",
						"같은 멱등키로 다른 요청을 재사용할 수 없습니다");
			}
			return existingResult(existingPost, requestFingerprint);
		}

		if (approvedQuestionRepository.findAssignableAt(command.submittedAt()).stream()
				.noneMatch(question -> question.getId().equals(command.approvedQuestionId()))) {
			throw new DirectionException(
					DirectionErrorCode.QUESTION_NOT_ACTIVE, "approvedQuestionId", "전송 시각에 활성인 질문이 아닙니다");
		}
		ActiveUserPresence sender = activeSender(command.senderId(), command.submittedAt());
		DirectionSegment segment = segment(command.schemeId(), command.segmentKey());
		DirectionPost post = postRepository.save(DirectionPost.submit(command.senderId(), command.approvedQuestionId(),
				requestFingerprint, command.idempotencyKey(), command.bodyText(), command.coarseRegionCode(),
				command.submittedAt(), command.expiresAt()));
		PostAudience audience = audienceRepository.save(PostAudience.create(post.getId(), command.schemeId(), segment.getSegmentKey(),
				segment.getCenterBearingDegrees(), segment.getAngularWidthDegrees(), command.minDistanceMeters(), command.maxDistanceMeters(),
				sender.getLatitude(), sender.getLongitude(), sender.getCoarseCellId(), command.submittedAt()));

		enqueueMatchingEvent(post, audience, requestFingerprint, command);
		return new SendResult(post, audience, List.of());
	}

	private SendResult recoverConcurrentRequest(SendCommand command,
												DirectionRequestFingerprint requestFingerprint, DataIntegrityViolationException race) {
		DirectionPost existing = postRepository.findBySenderAndIdempotencyKey(command.senderId(), command.idempotencyKey())
				.orElseThrow(() -> race);
		if (existing.getRequestFingerprint() != null && !existing.getRequestFingerprint().equals(requestFingerprint)) {
			throw new DirectionException(DirectionErrorCode.IDEMPOTENCY_KEY_REUSED, "idempotencyKey",
					"같은 멱등키로 다른 요청을 재사용할 수 없습니다");
		}
		return existingResult(existing, requestFingerprint);
	}

	private SendResult existingResult(DirectionPost post, DirectionRequestFingerprint requestFingerprint) {
		PostAudience audience = audienceRepository.findByPostId(post.getId()).orElse(null);
		DirectionPost restored = post;
		if (post.getRequestFingerprint() == null && audience != null) {
			try {
				DirectionRequestFingerprint legacyFingerprint = DirectionRequestFingerprint.create(post.getApprovedQuestionId(),
						audience.getDirectionSchemeId(), audience.getSelectedSegmentKey(), audience.getMinDistanceMeters(),
						audience.getMaxDistanceMeters(), post.getCoarseRegionCode(), post.getBodyText());
				if (!legacyFingerprint.equals(requestFingerprint)) {
					throw new DirectionException(DirectionErrorCode.IDEMPOTENCY_KEY_REUSED, "idempotencyKey",
							"같은 멱등키로 다른 요청을 재사용할 수 없습니다");
				}
				restored = postRepository.updateRequestFingerprintIfNull(post.getId(), legacyFingerprint)
						.orElseGet(() -> postRepository.findById(post.getId()).orElse(post));
				if (restored.getRequestFingerprint() != null
						&& !restored.getRequestFingerprint().equals(requestFingerprint)) {
					throw new DirectionException(DirectionErrorCode.IDEMPOTENCY_KEY_REUSED, "idempotencyKey",
							"같은 멱등키로 다른 요청을 재사용할 수 없습니다");
				}
			} catch (DirectionException ignored) {
				if (ignored.getErrorCode() == DirectionErrorCode.IDEMPOTENCY_KEY_REUSED) throw ignored;
				// legacy 행의 저장 의도를 복원할 수 없으면 기존 결과를 보존한다.
			}
		}
		return new SendResult(restored, audience, List.of());
	}

	private void enqueueMatchingEvent(DirectionPost post, PostAudience audience,
									  DirectionRequestFingerprint requestFingerprint, SendCommand command) {
		int matchRound = 1;
		String eventType = "RECIPIENT_MATCH_REQUESTED";
		String dedupKey = "direction-match:" + post.getId() + ":" + matchRound + ":" + eventType;
		String payload = "{\"postId\":" + post.getId()
				+ ",\"matchRound\":" + matchRound
				+ ",\"eventType\":\"" + eventType + "\""
				+ ",\"requestFingerprint\":\"" + jsonEscape(requestFingerprint.value()) + "\""
				+ ",\"schemeId\":" + audience.getDirectionSchemeId()
				+ ",\"segmentKey\":\"" + jsonEscape(audience.getSelectedSegmentKey()) + "\""
				+ ",\"minDistanceMeters\":" + audience.getMinDistanceMeters()
				+ ",\"maxDistanceMeters\":" + audience.getMaxDistanceMeters()
				+ ",\"coarseRegionCode\":\"" + jsonEscape(command.coarseRegionCode()) + "\"}";
		outboxEventRepository.findByDedupKey(dedupKey).orElseGet(() ->
				outboxEventRepository.save(OutboxEvent.matchingPending(post.getId(), matchRound, dedupKey,
						payload, command.submittedAt())));
	}

	private static String jsonEscape(String value) {
		StringBuilder escaped = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '"' -> escaped.append("\\\"");
				case '\\' -> escaped.append("\\\\");
				case '\b' -> escaped.append("\\b");
				case '\f' -> escaped.append("\\f");
				case '\n' -> escaped.append("\\n");
				case '\r' -> escaped.append("\\r");
				case '\t' -> escaped.append("\\t");
				default -> {
					if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
					else escaped.append(character);
				}
			}
		}
		return escaped.toString();
	}

	/**
	 * 질문자가 답변 목록을 읽었음을 기록한다. `새로운 답변 n개` 배지가 이 값으로 계산된다.
	 * post.markAnswersRead(at)는 유효성만 검증하고 결과는 버린다 — 실제 반영은
	 * advanceAnswersReadAt()의 DB 단일 UPDATE(max 비교)로 위임해, 순서가 뒤바뀌어
	 * 도착한 요청이 이미 기록된 더 늦은 시각을 덮어쓰지 않게 한다.
	 */
	@Transactional
	public DirectionPost markAnswersRead(long senderId, long postId, Instant at) {
		DirectionPost post = postRepository.findByIdAndSenderId(postId, senderId)
				.orElseThrow(() -> new DirectionException(
						DirectionErrorCode.POST_NOT_FOUND, "postId", "질문글을 찾을 수 없습니다"));
		post.markAnswersRead(at);
		return postRepository.advanceAnswersReadAt(postId, at);
	}

	private List<DirectionCandidate> candidates(PreviewCommand command, ActiveUserPresence sender, DirectionSegment segment) {
		double center = segment.getCenterBearingDegrees().doubleValue();
		double half = segment.getAngularWidthDegrees().doubleValue() / 2.0;
		double start = DirectionScheme.normalize(center - half);
		double end = DirectionScheme.normalize(center + half);
		if (sender.getLatitude() == null || sender.getLongitude() == null) {
			throw new DirectionException(
					DirectionErrorCode.PRESENCE_LOCATION_MISSING,
					"senderId",
					"정확 위치가 없는 presence는 후보를 계산할 수 없습니다"
			);
		}
		return presenceRepository.findCandidates(command.senderId(), sender.getLatitude().doubleValue(), sender.getLongitude().doubleValue(),
				command.minDistanceMeters(), command.maxDistanceMeters(), start, end, command.at(), command.coarseRegionCode());
	}

	private DirectionSegment segment(long schemeId, String segmentKey) {
		DirectionScheme scheme = schemeRepository.findById(schemeId)
				.orElseThrow(() -> new DirectionException(
						DirectionErrorCode.SCHEME_NOT_FOUND, "schemeId", "direction scheme을 찾을 수 없습니다"));
		List<DirectionSegment> segments = schemeRepository.findSegments(schemeId);
		scheme.validateCoverage(segments);
		return segments.stream().filter(candidate -> candidate.getSegmentKey().equals(segmentKey)).findFirst()
				.orElseThrow(() -> new DirectionException(
						DirectionErrorCode.SEGMENT_NOT_FOUND, "segmentKey", "direction segment을 찾을 수 없습니다"));
	}

	private DirectionScheme activeScheme(long schemeId) {
		DirectionScheme scheme = schemeRepository.findById(schemeId)
				.orElseThrow(() -> new DirectionException(
						DirectionErrorCode.SCHEME_NOT_FOUND, "schemeId", "direction scheme을 찾을 수 없습니다"));
		if (scheme.getStatus() != DirectionSchemeStatus.ACTIVE) {
			throw new DirectionException(
					DirectionErrorCode.SCHEME_NOT_FOUND, "schemeId", "활성 direction scheme을 찾을 수 없습니다");
		}
		return scheme;
	}

	private ActiveUserPresence activeSender(long senderId, Instant at) {
		ActiveUserPresence sender = presenceRepository.findByUserId(senderId)
				.orElseThrow(() -> new DirectionException(
						DirectionErrorCode.PRESENCE_NOT_FOUND, "senderId", "sender presence를 찾을 수 없습니다"));
		if (!sender.isCurrentAt(at)) {
			throw new DirectionException(
					DirectionErrorCode.PRESENCE_NOT_CURRENT, "senderId", "sender presence가 만료되었거나 수신 허용이 아닙니다");
		}
		return sender;
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new DirectionException(
					DirectionErrorCode.REQUIRED_VALUE_MISSING, field, field + "는 필수입니다");
		}
		return value;
	}

	public record PreviewCommand(Long senderId, Long schemeId, String segmentKey, long minDistanceMeters,
								 long maxDistanceMeters, String coarseRegionCode, Instant at) {
		public PreviewCommand {
			if (senderId == null || senderId <= 0 || schemeId == null || schemeId <= 0) {
				throw new DirectionException(DirectionErrorCode.INVALID_ID, null, "ID가 유효하지 않습니다");
			}
			if (minDistanceMeters < 0 || maxDistanceMeters <= minDistanceMeters) {
				throw new DirectionException(
						DirectionErrorCode.INVALID_DISTANCE_RANGE, "maxDistanceMeters", "거리 범위가 유효하지 않습니다");
			}
			requireValue(segmentKey, "segmentKey");
			requireValue(at, "at");
		}
	}

	public record PreviewAllCommand(Long senderId, Long schemeId, long minDistanceMeters,
									long maxDistanceMeters, String coarseRegionCode, Instant at) {
		public PreviewAllCommand {
			if (senderId == null || senderId <= 0 || schemeId == null || schemeId <= 0) {
				throw new DirectionException(DirectionErrorCode.INVALID_ID, null, "ID가 유효하지 않습니다");
			}
			if (minDistanceMeters < 0 || maxDistanceMeters <= minDistanceMeters) {
				throw new DirectionException(
						DirectionErrorCode.INVALID_DISTANCE_RANGE, "maxDistanceMeters", "거리 범위가 유효하지 않습니다");
			}
			requireValue(at, "at");
		}
	}

	public record SendCommand(Long senderId, Long approvedQuestionId, Long schemeId, String segmentKey,
							  long minDistanceMeters, long maxDistanceMeters, String coarseRegionCode,
							  String idempotencyKey,
							  String bodyText, Instant submittedAt, Instant expiresAt) {
		public SendCommand {
			if (senderId == null || senderId <= 0 || approvedQuestionId == null || approvedQuestionId <= 0 || schemeId == null || schemeId <= 0) {
				throw new DirectionException(DirectionErrorCode.INVALID_ID, null, "ID가 유효하지 않습니다");
			}
			if (minDistanceMeters < 0 || maxDistanceMeters <= minDistanceMeters) {
				throw new DirectionException(
						DirectionErrorCode.INVALID_DISTANCE_RANGE, "maxDistanceMeters", "거리 범위가 유효하지 않습니다");
			}
			if (segmentKey == null || segmentKey.isBlank() || coarseRegionCode == null || coarseRegionCode.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) {
				throw new DirectionException(
						DirectionErrorCode.REQUIRED_VALUE_MISSING, null, "필수 command 값이 없습니다");
			}
			requireValue(submittedAt, "submittedAt");
			requireValue(expiresAt, "expiresAt");
			if (!expiresAt.isAfter(submittedAt)) {
				throw new DirectionException(
						DirectionErrorCode.INVALID_TIME_ORDER, "expiresAt", "expiresAt은 submittedAt보다 늦어야 합니다");
			}
		}
	}

	public record SendResult(DirectionPost post, PostAudience audience, List<PostRecipient> recipients) {
		/**
		 * 제출 단계에서는 수신자 확정을 수행하지 않는다. 이 목록은 후속 매칭 워커가
		 * 결과를 연결할 때까지 비어 있으며, 기존 호출자와의 source compatibility를
		 * 위해 결과 모델에 남겨 둔다.
		 */
		public SendResult {
			recipients = List.copyOf(recipients);
		}
	}
}
