package com.dnd.qello.direction.matching;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.direction.config.DirectionReceiveProperties;
import com.dnd.qello.direction.config.DirectionRecipientSelectionProperties;
import com.dnd.qello.direction.domain.DirectionCandidate;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostModerationStatus;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.PostAudience;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.PostAudienceRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;
import com.dnd.qello.feed.config.DistanceBandPolicy;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxFailureKind;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@code RECIPIENT_MATCH_REQUESTED} 하나를 수신자 확정 transaction으로 변환한다.
 * scheduler/polling은 이 클래스의 책임이 아니며, 호출자는 {@link BatchCommand}로
 * lease와 운영 정책을 명시해야 한다.
 */
@Service
@RequiredArgsConstructor
public class DirectionMatchingWorker {

    private static final int FIRST_MATCHING_ROUND = 1;
    private static final int CANDIDATE_SCAN_MULTIPLIER = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final DirectionPostRepository postRepository;
    private final PostAudienceRepository audienceRepository;
    private final ActiveUserPresenceRepository presenceRepository;
    private final PostRecipientRepository recipientRepository;
    private final RecipientReceiveStateRepository receiveStateRepository;
    private final DirectionRecipientSelectionProperties selectionProperties;
    private final DirectionReceiveProperties receiveProperties;
    private final DistanceBandPolicy distanceBandPolicy;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    /**
     * due matching event만 점유하고 event별로 독립 transaction을 실행한다.
     */
    public BatchResult processBatch(BatchCommand command) {
        requireCommand(command);
        Instant at = resolveProcessingTime(command);
        List<OutboxEvent> claimed = claimMatchingEvents(command, at);
        List<Outcome> outcomes = claimed.stream()
                .map(event -> processClaimed(event, command, at))
                .toList();
        return new BatchResult(claimed.size(), outcomes);
    }

    private Outcome processClaimed(OutboxEvent event, BatchCommand command, Instant at) {
        try {
            executeMatchingTransaction(event, at);
            return Outcome.PROCESSED;
        } catch (StaleLeaseException staleLease) {
            return Outcome.STALE_LEASE;
        } catch (RetryableMatchingException | DataAccessException retryable) {
            return fail(event, command, at, OutboxFailureKind.RETRYABLE);
        } catch (RuntimeException unexpected) {
            return fail(event, command, at, OutboxFailureKind.PERMANENT);
        }
    }

    private void executeMatchingTransaction(OutboxEvent event, Instant at) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            processEventInTransaction(event, at);
            completeClaimOrThrow(event, at);
        });
    }

    private void completeClaimOrThrow(OutboxEvent event, Instant at) {
        if (!outboxEventRepository.complete(event.id(), event.leaseOwner(), event.leaseGeneration(), at)) {
            throw new StaleLeaseException();
        }
    }

    private Outcome fail(OutboxEvent event, BatchCommand command, Instant at, OutboxFailureKind failureKind) {
        OutboxRetryDecision decision = command.retryPolicy().decide(event, failureKind, at);
        boolean updated = outboxEventRepository.fail(event.id(), event.leaseOwner(), event.leaseGeneration(), at, decision);
        if (!updated) return Outcome.STALE_LEASE;
        return decision.dead() ? Outcome.DEAD : Outcome.RETRYABLE;
    }

    private void processEventInTransaction(OutboxEvent event, Instant at) {
        validateMatchingEvent(event);
        DirectionPost post = lockPost(event.aggregateId());
        if (expireIfDeadlinePassed(post, at)) return;
        if (!isEligibleForMatching(post, at)) return;

        PostAudience audience = loadPreciseAudience(post.getId());
        MatchingSelection selection = prepareSelection(post, audience, at);
        persistRecipients(post, selection, at);
        postRepository.save(post.activate(at));
    }

    private void validateMatchingEvent(OutboxEvent event) {
        if (event.id() == null || event.aggregateType() != OutboxAggregateType.DIRECTION_POST
                || event.eventType() != OutboxEventType.RECIPIENT_MATCH_REQUESTED
                || !Integer.valueOf(FIRST_MATCHING_ROUND).equals(event.matchRound())) {
            throw new PermanentMatchingException("matching event contract is invalid");
        }
    }

    private DirectionPost lockPost(long postId) {
        return postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new PermanentMatchingException("matching post does not exist"));
    }

    private boolean expireIfDeadlinePassed(DirectionPost post, Instant at) {
        if (post.getExpiresAt().isAfter(at)) return false;
        if (isExpirablePostStatus(post.getStatus())) {
            postRepository.save(post.expire(at));
        }
        return true;
    }

    private boolean isExpirablePostStatus(DirectionPostStatus status) {
        return status == DirectionPostStatus.MATCHING
                || status == DirectionPostStatus.SAFETY_CHECKING
                || status == DirectionPostStatus.ACTIVE;
    }

    private boolean isEligibleForMatching(DirectionPost post, Instant at) {
        if (post.getStatus() != DirectionPostStatus.MATCHING) return false;
        if (post.getModerationStatus() == DirectionPostModerationStatus.PENDING
                || post.getModerationStatus() == DirectionPostModerationStatus.REVIEW_HELD) {
            throw new RetryableMatchingException("moderation is not ready");
        }
        if (post.getModerationStatus() == DirectionPostModerationStatus.REJECTED) return false;
        if (!post.canMatchAt(at)) throw new PermanentMatchingException("post is not eligible for matching");
        return true;
    }

    private PostAudience loadPreciseAudience(long postId) {
        PostAudience audience = audienceRepository.findByPostId(postId)
                .orElseThrow(() -> new PermanentMatchingException("post audience does not exist"));
        if (audience.getOriginLatitude() == null || audience.getOriginLongitude() == null) {
            throw new PermanentMatchingException("matching requires a precise audience origin");
        }
        return audience;
    }

    private MatchingSelection prepareSelection(DirectionPost post, PostAudience audience, Instant at) {
        int maxRecipients = selectionProperties.maxRecipientsPerPost();
        validateRecipientLimit(maxRecipients);

        DirectionBounds bounds = boundsOf(audience);
        List<DirectionCandidate> candidates = findCandidates(post, audience, bounds, at);
        List<DirectionCandidate> scanned = scanCandidates(candidates, maxRecipients);
        List<Long> candidateIds = scanned.stream().map(DirectionCandidate::userId).toList();
        receiveStateRepository.ensureForUsers(candidateIds, at);
        Set<Long> lockedUserIds = new HashSet<>(receiveStateRepository.lockAvailableUserIds(candidateIds, scanned.size(),
                receiveProperties.receiveCapacity()));
        return new MatchingSelection(scanned, lockedUserIds, maxRecipients);
    }

    private void validateRecipientLimit(int maxRecipients) {
        if (maxRecipients <= 0) {
            throw new PermanentMatchingException("max recipients per post must be positive");
        }
    }

    private DirectionBounds boundsOf(PostAudience audience) {
        double center = audience.getCenterBearingDegrees().doubleValue();
        double halfWidth = audience.getAngularWidthDegrees().doubleValue() / 2.0;
        return new DirectionBounds(DirectionScheme.normalize(center - halfWidth),
                DirectionScheme.normalize(center + halfWidth));
    }

    private List<DirectionCandidate> findCandidates(DirectionPost post, PostAudience audience,
            DirectionBounds bounds, Instant at) {
        return presenceRepository.findCandidates(post.getSenderId(), audience.getOriginLatitude().doubleValue(),
                audience.getOriginLongitude().doubleValue(), audience.getMinDistanceMeters(),
                audience.getMaxDistanceMeters(), bounds.start(), bounds.end(), at, post.getCoarseRegionCode());
    }

    private List<DirectionCandidate> scanCandidates(List<DirectionCandidate> candidates, int maxRecipients) {
        int scanLimit = Math.min(maxRecipients * CANDIDATE_SCAN_MULTIPLIER, candidates.size());
        return candidates.subList(0, scanLimit);
    }

    private void persistRecipients(DirectionPost post, MatchingSelection selection, Instant at) {
        int created = 0;
        for (DirectionCandidate candidate : selection.candidates()) {
            if (created >= selection.maxRecipients() || !selection.lockedUserIds().contains(candidate.userId())) continue;
            if (recipientRepository.findByPostIdAndRecipientId(post.getId(), candidate.userId()).isPresent()) continue;
            if (!reserveSlot(candidate.userId(), at)) continue;

            persistRecipient(post, candidate, at);
            created++;
        }
    }

    private boolean reserveSlot(long candidateId, Instant at) {
        return receiveStateRepository.reserve(candidateId, at, receiveProperties.receiveCapacity());
    }

    private void persistRecipient(DirectionPost post, DirectionCandidate candidate, Instant at) {
        long distanceM = roundedDistance(candidate);
        PostRecipient recipient = recipientRepository.save(PostRecipient.available(post.getId(), candidate.userId(),
                distanceBandPolicy.forDistance(distanceM), candidate.bearingDegrees(), candidate.matchedRegionCode(),
                at, candidate.inboundBearingDegrees(), distanceM));
        outboxEventRepository.save(confirmedEvent(post.getId(), recipient.getId(), candidate.userId(), at));
    }

    private long roundedDistance(DirectionCandidate candidate) {
        return candidate.distanceMeters().setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private void requireCommand(BatchCommand command) {
        if (command == null) {
            throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, "command", "command는 필수입니다");
        }
    }

    private Instant resolveProcessingTime(BatchCommand command) {
        return command.at() == null ? clock.instant() : command.at();
    }

    private List<OutboxEvent> claimMatchingEvents(BatchCommand command, Instant at) {
        return outboxEventRepository.claimDue(Set.of(OutboxEventType.RECIPIENT_MATCH_REQUESTED), command.limit(),
                command.leaseOwner(), at, command.leaseExpiresAt());
    }

    private OutboxEvent confirmedEvent(long postId, long postRecipientId, long recipientId, Instant at) {
        String dedupKey = "direction-recipient:" + postRecipientId + ":RECIPIENTS_CONFIRMED";
        String payload = "{\"postId\":" + postId + ",\"postRecipientId\":" + postRecipientId
                + ",\"recipientId\":" + recipientId + "}";
        return OutboxEvent.pending(OutboxAggregateType.POST_RECIPIENT, postRecipientId,
                OutboxEventType.RECIPIENTS_CONFIRMED, dedupKey, payload, at);
    }

    public enum Outcome {PROCESSED, RETRYABLE, DEAD, STALE_LEASE}

    public record BatchResult(int claimed, List<Outcome> outcomes) {
        public BatchResult {
            if (claimed < 0 || outcomes == null) throw new IllegalArgumentException("batch result is invalid");
            outcomes = List.copyOf(outcomes);
        }
    }

    public record BatchCommand(int limit, String leaseOwner, Instant at, Instant leaseExpiresAt,
                               OutboxRetryPolicy retryPolicy) {
        public BatchCommand {
            if (limit <= 0 || leaseOwner == null || leaseOwner.isBlank() || at == null || leaseExpiresAt == null
                    || !leaseExpiresAt.isAfter(at) || retryPolicy == null) {
                throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, "batch", "matching batch 입력이 유효하지 않습니다");
            }
        }
    }

    private static final class RetryableMatchingException extends RuntimeException {
        private RetryableMatchingException(String message) {
            super(message);
        }
    }

    private static final class PermanentMatchingException extends RuntimeException {
        private PermanentMatchingException(String message) {
            super(message);
        }
    }

    private static final class StaleLeaseException extends RuntimeException {
    }

    private record DirectionBounds(double start, double end) {
    }

    private record MatchingSelection(List<DirectionCandidate> candidates, Set<Long> lockedUserIds,
                                     int maxRecipients) {
    }
}
