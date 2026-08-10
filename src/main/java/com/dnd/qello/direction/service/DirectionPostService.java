package com.dnd.qello.direction.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.direction.config.DirectionReceiveProperties;
import com.dnd.qello.direction.config.DirectionRecipientSelectionProperties;
import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionCandidate;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.domain.PostAudience;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.repository.PostAudienceRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;
import com.dnd.qello.feed.config.DistanceBandPolicy;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;

import lombok.RequiredArgsConstructor;

/**
 * 방향 글 발송과 질문자 측 읽음 표시를 소유한다.
 * 방향 preview는 참고값으로만 사용하고 send transaction에서 다시 계산한다.
 */
@Service
@RequiredArgsConstructor
public class DirectionPostService {

	private final DirectionSchemeRepository schemeRepository;
	private final ActiveUserPresenceRepository presenceRepository;
	private final RecipientReceiveStateRepository receiveStateRepository;
	private final DirectionRecipientSelectionProperties recipientSelectionProperties;
	private final DirectionPostRepository postRepository;
	private final PostAudienceRepository audienceRepository;
	private final PostRecipientRepository recipientRepository;
	private final ApprovedQuestionRepository approvedQuestionRepository;
	private final DirectionReceiveProperties receiveProperties;
	private final DistanceBandPolicy distanceBandPolicy;

	@Transactional(readOnly = true)
	public List<DirectionCandidate> preview(PreviewCommand command) {
		requireValue(command, "command");
		ActiveUserPresence sender = activeSender(command.senderId(), command.at());
		DirectionSegment segment = segment(command.schemeId(), command.segmentKey());
		return candidates(command, sender, segment);
	}

	@Transactional
	public SendResult send(SendCommand command) {
		requireValue(command, "command");
		var existing = postRepository.findBySenderAndIdempotencyKey(command.senderId(), command.idempotencyKey());
		if (existing.isPresent()) return new SendResult(existing.get(), audienceRepository.findByPostId(existing.get().getId()).orElse(null), recipientRepository.findAllByPostId(existing.get().getId()));

		if (approvedQuestionRepository.findAssignableAt(command.submittedAt()).stream()
			.noneMatch(question -> question.getId().equals(command.approvedQuestionId()))) {
			throw new DirectionException(
				DirectionErrorCode.QUESTION_NOT_ACTIVE, "approvedQuestionId", "전송 시각에 활성인 질문이 아닙니다");
		}
		ActiveUserPresence sender = activeSender(command.senderId(), command.submittedAt());
		DirectionSegment segment = segment(command.schemeId(), command.segmentKey());
		DirectionPost post = postRepository.save(DirectionPost.submit(command.senderId(), command.approvedQuestionId(),
			command.idempotencyKey(), command.bodyText(), command.coarseRegionCode(), command.submittedAt(), command.expiresAt()));
		PostAudience audience = audienceRepository.save(PostAudience.create(post.getId(), command.schemeId(), segment.getSegmentKey(),
			segment.getCenterBearingDegrees(), segment.getAngularWidthDegrees(), command.minDistanceMeters(), command.maxDistanceMeters(),
			sender.getLatitude(), sender.getLongitude(), sender.getCoarseCellId(), command.submittedAt()));

		PreviewCommand candidateCommand = new PreviewCommand(command.senderId(), command.schemeId(), command.segmentKey(),
			command.minDistanceMeters(), command.maxDistanceMeters(), command.coarseRegionCode(), command.submittedAt());
		List<PostRecipient> recipients = selectRecipients(post.getId(), candidates(candidateCommand, sender, segment), command.submittedAt());
		return new SendResult(post, audience, recipients);
	}

	private List<PostRecipient> selectRecipients(long postId, List<DirectionCandidate> candidates, Instant matchedAt) {
		List<PostRecipient> recipients = new ArrayList<>();
		for (DirectionCandidate candidate : candidates) {
			if (recipients.size() >= recipientSelectionProperties.maxRecipientsPerPost()) break;
			if (!reserve(candidate.userId(), matchedAt)) continue;
			recipients.add(recipientRepository.save(PostRecipient.available(postId, candidate.userId(),
				distanceBandPolicy.forDistance(candidate.distanceMeters().longValue()),
				candidate.bearingDegrees(), candidate.matchedRegionCode(), matchedAt,
				candidate.inboundBearingDegrees(), candidate.distanceMeters().longValue())));
		}
		return List.copyOf(recipients);
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

	/**
	 * 초기 행 생성은 reserve()가 한 문장으로 함께 처리한다. 조회해서 없으면 만들고
	 * 다시 예약하는 방식은 두 발송이 같은 신규 수신자를 동시에 잡을 때 서로의
	 * 예약을 덮어썼다.
	 */
	private boolean reserve(long userId, Instant at) {
		return receiveStateRepository.reserve(userId, at, receiveProperties.receiveCapacity());
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

	public record SendCommand(Long senderId, Long approvedQuestionId, Long schemeId, String segmentKey,
		long minDistanceMeters, long maxDistanceMeters, String coarseRegionCode, String idempotencyKey,
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
		public SendResult { recipients = List.copyOf(recipients); }
	}
}
