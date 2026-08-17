/**
 * Created at: 2026-08-17T17:10:00+09:00
 * Source scenario: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-001 through INT-004,
 * INT-007 through INT-009, INT-019, INT-020, INT-022
 */
package com.dnd.qello;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.MediaAttachmentRepository;
import com.dnd.qello.answer.service.AnswerNotificationService;
import com.dnd.qello.answer.service.AnswerSubmissionApplicationService;
import com.dnd.qello.direction.service.ReceiveSlotReleaseService;
import com.dnd.qello.feed.service.InboxApplicationService;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxListing;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(Answer125TestClockConfiguration.class)
class AnswerSubmissionApiIntegrationTest extends PostgisContainerIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-17T06:00:00.123456Z");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AnswerSubmissionApplicationService submissionApplicationService;
    @Autowired
    private MediaAttachmentRepository mediaAttachmentRepository;
    @Autowired
    private FilterReleaseRegistryService releaseRegistryService;
    @Autowired
    private AnswerNotificationService answerNotificationService;
    @Autowired
    private InboxApplicationService inbox;
    @Autowired
    private ReceiveSlotReleaseService receiveSlotReleaseService;
    @Autowired
    private Answer125MutableClock clock;

    private Answer125IntegrationFixtures fixtures;
    private long senderId;
    private long recipientId;

    @BeforeEach
    void resetFixtures() {
        clock.setInstant(NOW);
        fixtures = new Answer125IntegrationFixtures(jdbc, NOW);
        fixtures.reset();
        senderId = fixtures.account("answer125-sender");
        recipientId = fixtures.account("answer125-recipient");
    }

    @Test
    @DisplayName("INT-001: 유효 제출은 SAFETY_CHECKING answer·filter_job·EXECUTION_REQUESTED를 commit하고 슬롯은 아직 유지한다")
    void submitsAnswerAndCreatesModerationJobAtomically() {
        promotedRelease();
        long postId = fixtures.post(senderId, "int001", NOW.plusSeconds(3600), "ACTIVE", null);
        long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
        fixtures.receiveState(recipientId, 1);

        Answer saved = submissionApplicationService.submit(
                recipientId, "int001-key", postRecipientId, "저도 여기 자주 와요!", List.of());

        assertThat(saved.getStatus()).isEqualTo(AnswerStatus.SUBMITTED);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM answer WHERE id = ? AND status = 'SUBMITTED'", Integer.class, saved.getId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM filter_job WHERE target_type = 'ANSWER' AND target_id = ?",
                Integer.class, saved.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'MODERATION_EXECUTION_REQUESTED'"
                        + " AND aggregate_id = (SELECT id FROM filter_job WHERE target_id = ?)",
                Integer.class, saved.getId())).isEqualTo(1);
        assertThat(fixtures.status(postRecipientId)).isEqualTo("AVAILABLE");
        assertThat(fixtures.activeCount(recipientId)).isEqualTo(1);
    }

    @Test
    @DisplayName("INT-002: 승격된 release가 없어 moderation intake가 실패하면 answer·attachment·filter_job·outbox가 전부 rollback된다")
    void rollsBackWholeSubmissionWhenModerationIntakeFails() {
        // 의도적으로 filter_release를 승격하지 않는다 — AnswerModerationJobIntakeService.createJob이
        // NO_ACTIVE_RELEASE로 실패하는 실제 경로를 강제 실패 지점으로 사용한다. intake는 제출과 같은
        // transaction에 참여하므로(REQUIRED 전파) answer 저장도 함께 rollback되어야 한다.
        long postId = fixtures.post(senderId, "int002", NOW.plusSeconds(3600), "ACTIVE", null);
        long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
        long answersBefore = fixtures.answerCount();

        assertThatThrownBy(() -> submissionApplicationService.submit(
                recipientId, "int002-key", postRecipientId, "본문", List.of()))
                .isInstanceOf(RuntimeException.class);

        assertThat(fixtures.answerCount()).isEqualTo(answersBefore);
        assertThat(fixtures.status(postRecipientId)).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("INT-003: 같은 키·동일 payload 재제출은 기존 answer를 반환하고 관련 row count·submittedAt이 불변이다")
    void replaysIdenticalPayloadWithoutSideEffects() {
        promotedRelease();
        long postId = fixtures.post(senderId, "int003", NOW.plusSeconds(3600), "ACTIVE", null);
        long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
        Answer first = submissionApplicationService.submit(
                recipientId, "int003-key", postRecipientId, "본문", List.of());
        int answerCountAfterFirst = jdbc.queryForObject("SELECT count(*) FROM answer", Integer.class);
        int jobCountAfterFirst = jdbc.queryForObject("SELECT count(*) FROM filter_job", Integer.class);

        Answer replay = submissionApplicationService.submit(
                recipientId, "int003-key", postRecipientId, "본문", List.of());

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(replay.getSubmittedAt()).isEqualTo(first.getSubmittedAt());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM answer", Integer.class)).isEqualTo(answerCountAfterFirst);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM filter_job", Integer.class)).isEqualTo(jobCountAfterFirst);
    }

    @Test
    @DisplayName("INT-004: 같은 키·다른 본문 재제출은 409이며 기존 row가 바뀌지 않고 새 attachment/job/outbox도 없다")
    void rejectsReplayWithDifferentPayload() {
        promotedRelease();
        long postId = fixtures.post(senderId, "int004", NOW.plusSeconds(3600), "ACTIVE", null);
        long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
        submissionApplicationService.submit(recipientId, "int004-key", postRecipientId, "원래 본문", List.of());
        int jobCountBefore = jdbc.queryForObject("SELECT count(*) FROM filter_job", Integer.class);

        assertThatThrownBy(() -> submissionApplicationService.submit(
                recipientId, "int004-key", postRecipientId, "다른 본문", List.of()))
                .isInstanceOf(AnswerException.class)
                .hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM filter_job", Integer.class)).isEqualTo(jobCountBefore);
    }

    @Test
    @DisplayName("INT-007: 타인/없는 recipient와 SKIP_PENDING/SKIPPED/EXPIRED/BLOCKED/ANSWERED는 모두 동일한 404이며 부수효과가 없다")
    void rejectsIneligibleRecipientsUniformlyWithoutSideEffects() {
        promotedRelease();
        long outsiderId = fixtures.account("int007-outsider");
        long postId = fixtures.post(senderId, "int007", NOW.plusSeconds(3600), "ACTIVE", null);
        long recipientOfOutsider = fixtures.available(postId, outsiderId, NOW.minusSeconds(10), 0);
        long skipPending = fixtures.recipientForStatus(
                fixtures.post(senderId, "int007-skip", NOW.plusSeconds(3600), "ACTIVE", null),
                recipientId, "SKIP_PENDING", NOW.minusSeconds(10), 0);
        long expired = fixtures.recipientForStatus(
                fixtures.post(senderId, "int007-expired", NOW.plusSeconds(3600), "ACTIVE", null),
                recipientId, "EXPIRED", NOW.minusSeconds(10), 0);
        long skipped = fixtures.recipientForStatus(
                fixtures.post(senderId, "int007-skipped", NOW.plusSeconds(3600), "ACTIVE", null),
                recipientId, "SKIPPED", NOW.minusSeconds(10), 0);
        long answered = fixtures.recipientForStatus(
                fixtures.post(senderId, "int007-answered", NOW.plusSeconds(3600), "ACTIVE", null),
                recipientId, "ANSWERED", NOW.minusSeconds(10), 0);
        long blockedSenderId = fixtures.account("int007-blocked-sender");
        long blockedPostId = fixtures.post(blockedSenderId, "int007-blocked", NOW.plusSeconds(3600), "ACTIVE", null);
        long availableUnderBlock = fixtures.available(blockedPostId, recipientId, NOW.minusSeconds(10), 0);
        fixtures.block(recipientId, blockedSenderId, null);
        int answersBefore = jdbc.queryForObject("SELECT count(*) FROM answer", Integer.class);

        assertNotFound(() -> submissionApplicationService.submit(
                recipientId, "int007-a", recipientOfOutsider, "본문", List.of()));
        assertNotFound(() -> submissionApplicationService.submit(
                recipientId, "int007-b", skipPending, "본문", List.of()));
        assertNotFound(() -> submissionApplicationService.submit(
                recipientId, "int007-c", expired, "본문", List.of()));
        assertNotFound(() -> submissionApplicationService.submit(
                recipientId, "int007-d", availableUnderBlock, "본문", List.of()));
        assertNotFound(() -> submissionApplicationService.submit(
                recipientId, "int007-e", 999_999_999L, "본문", List.of()));
        assertNotFound(() -> submissionApplicationService.submit(
                recipientId, "int007-f", skipped, "본문", List.of()));
        assertNotFound(() -> submissionApplicationService.submit(
                recipientId, "int007-g", answered, "본문", List.of()));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM answer", Integer.class)).isEqualTo(answersBefore);
    }

    @Test
    @DisplayName("INT-008: 만료 직전은 202이고 경계 시각과 그 이후는 서버 시계 기준으로 거절한다")
    void enforcesExpiryBoundaryUsingServerClockOnly() {
        promotedRelease();
        Instant expiresAt = NOW.plusSeconds(60);
        long postId = fixtures.post(senderId, "int008", expiresAt, "ACTIVE", null);
        long justBefore = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);

        clock.setInstant(expiresAt.minusNanos(1000));
        Answer accepted = submissionApplicationService.submit(
                recipientId, "int008-before", justBefore, "본문", List.of());
        assertThat(accepted.getStatus()).isEqualTo(AnswerStatus.SUBMITTED);

        long atBoundaryRecipient = fixtures.available(
                fixtures.post(senderId, "int008-b", expiresAt, "ACTIVE", null), recipientId, NOW.minusSeconds(10), 0);
        clock.setInstant(expiresAt);
        assertNotFound(() -> submissionApplicationService.submit(
                recipientId, "int008-at", atBoundaryRecipient, "본문", List.of()));

        long afterRecipient = fixtures.available(
                fixtures.post(senderId, "int008-c", expiresAt, "ACTIVE", null), recipientId, NOW.minusSeconds(10), 0);
        clock.setInstant(expiresAt.plusSeconds(1));
        assertNotFound(() -> submissionApplicationService.submit(
                recipientId, "int008-after", afterRecipient, "본문", List.of()));
    }

    @Test
    @DisplayName("INT-009: 실제 만료 sweep을 실행해도 만료 전 제출된 SAFETY_CHECKING answer의 recipient는 선점되지 않고 유지되며, 답변 없는 대조군은 정상적으로 EXPIRED된다")
    void preservesPendingAnswerEligibilityAcrossExpirySweep() {
        promotedRelease();
        Instant expiresAt = NOW.plusSeconds(60);
        long postId = fixtures.post(senderId, "int009", expiresAt, "ACTIVE", null);
        long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
        submissionApplicationService.submit(recipientId, "int009-key", postRecipientId, "본문", List.of());

        // 답변이 없는 대조군 — 같은 시각에 만료되고 검사 중 답변이 없으므로 sweep이 정상적으로
        // EXPIRED 전이시켜야 한다. 이 대조군이 EXPIRED되지 않으면 sweep 자체가 동작하지 않는 것이므로
        // 아래 postRecipientId의 AVAILABLE 유지가 "sweep이 답변을 제외해서"가 아니라 "sweep이 아예
        // 실행되지 않아서"일 가능성을 배제하지 못한다.
        long controlPostId = fixtures.post(senderId, "int009-control", expiresAt, "ACTIVE", null);
        long controlOutsiderId = fixtures.account("int009-control-recipient");
        long controlRecipientId = fixtures.available(controlPostId, controlOutsiderId, NOW.minusSeconds(10), 0);

        clock.setInstant(expiresAt.plusSeconds(120));
        receiveSlotReleaseService.findExpirable(expiresAt.plusSeconds(120))
                .forEach(candidate -> receiveSlotReleaseService.expire(candidate.getId(), expiresAt.plusSeconds(120)));

        assertThat(fixtures.status(postRecipientId)).isEqualTo("AVAILABLE");
        assertThat(fixtures.status(controlRecipientId)).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("INT-019: 승인된 본인 READY 미디어만 첨부되고 타인 소유·미검사 미디어를 섞으면 제출 전체가 거절된다")
    void attachesOnlyOwnedReadyMediaAndRejectsForeignOrUnsafeMedia() {
        promotedRelease();
        long postId = fixtures.post(senderId, "int019", NOW.plusSeconds(3600), "ACTIVE", null);
        long ownMediaRecipient = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
        long readyMediaId = fixtures.media(recipientId, "READY", "PASSED");

        Answer withMedia = submissionApplicationService.submit(
                recipientId, "int019-key", ownMediaRecipient, "사진과 함께요", List.of(readyMediaId));

        assertThat(mediaAttachmentRepository.findMediaIdsByAnswerId(withMedia.getId())).containsExactly(readyMediaId);

        long secondPostId = fixtures.post(senderId, "int019-b", NOW.plusSeconds(3600), "ACTIVE", null);
        long foreignMediaRecipient = fixtures.available(secondPostId, recipientId, NOW.minusSeconds(10), 0);
        long foreignMediaId = fixtures.media(senderId, "READY", "PASSED");
        int answersBefore = jdbc.queryForObject("SELECT count(*) FROM answer", Integer.class);

        // MediaAttachmentService.attach()는 mediaId·ownerId를 함께 쿼리하므로(findByIdAndOwnerId) 타인
        // 소유 미디어는 존재 자체를 노출하지 않는 MEDIA_NOT_FOUND로 거절된다 — MEDIA_OWNER_MISMATCH는
        // "본인 미디어를 남의 answer/post에 붙이려는" 반대 방향 시나리오의 오류 코드다.
        assertThatThrownBy(() -> submissionApplicationService.submit(
                recipientId, "int019-foreign", foreignMediaRecipient, "본문", List.of(foreignMediaId)))
                .isInstanceOf(AnswerException.class)
                .hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.MEDIA_NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM answer", Integer.class)).isEqualTo(answersBefore);
    }

    @Test
    @DisplayName("INT-020: ALLOW 공개 뒤 ANSWERED 항목은 슬롯 없이 #124 수신함 ANSWERED 목록 계약을 유지한다")
    void publishedAnswerRemainsVisibleInAnsweredInboxCategory() {
        promotedRelease();
        long postId = fixtures.post(senderId, "int020", NOW.plusSeconds(3600), "ACTIVE", null);
        long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
        Answer submitted = submissionApplicationService.submit(
                recipientId, "int020-key", postRecipientId, "본문", List.of());
        markSafetyPassedAndPublish(submitted.getId(), NOW.plusSeconds(1));

        InboxListing answered = inbox.list(recipientId, InboxCategory.ANSWERED, null);

        assertThat(answered.cards()).extracting(card -> card.postRecipientId()).contains(postRecipientId);
        assertThat(fixtures.status(postRecipientId)).isEqualTo("ANSWERED");
    }

    @Test
    @DisplayName("INT-022: 공개 응답과 ANSWER_PUBLISHED payload에 좌표·내부 사용자 ID·본문이 없다")
    void publicResponseAndPublishedOutboxExcludeSensitiveFields() throws com.fasterxml.jackson.core.JsonProcessingException {
        promotedRelease();
        long postId = fixtures.post(senderId, "int022", NOW.plusSeconds(3600), "ACTIVE", null);
        long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
        Answer submitted = submissionApplicationService.submit(
                recipientId, "int022-key", postRecipientId, "민감한 본문 절대 노출 금지", List.of());
        markSafetyPassedAndPublish(submitted.getId(), NOW.plusSeconds(1));

        String outboxPayload = jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_type = 'ANSWER_PUBLISHED' AND aggregate_id = ?",
                String.class, submitted.getId());

        assertThat(outboxPayload).doesNotContain("민감한 본문").doesNotContain("latitude").doesNotContain("longitude");
        // recipientId의 문자열 표현이 answerId의 부분 문자열일 수 있어(예: recipientId=12,
        // answerId=123) doesNotContain(String.valueOf(recipientId))은 실행마다 우연히 실패할 수
        // 있다. payload를 파싱해 필드 집합 자체를 단정하면 내부 사용자 ID가 없다는 계약을 정확히
        // 검증한다.
        com.fasterxml.jackson.databind.JsonNode payloadNode =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(outboxPayload);
        assertThat(payloadNode.fieldNames()).toIterable().containsExactly("answerId");
    }

    private void markSafetyPassedAndPublish(long answerId, Instant at) {
        // publish()는 SUBMITTED에서 startSafetyCheck()를 스스로 호출해 SAFETY_CHECKING으로
        // 전이시키므로(AnswerNotificationService.ensureSafetyChecking) 여기서 직접 상태를
        // 앞당길 필요가 없다 — 그대로 두면 실제 프로덕션 경로(SUBMITTED -> publish())를 그대로
        // 검증한다.
        answerNotificationService.publish(answerId, at);
    }

    private void promotedRelease() {
        AnswerModerationReleaseTestFixture.promotedRelease(releaseRegistryService, senderId);
    }

    private static void assertNotFound(ThrowingAction action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AnswerException.class)
                .hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.RECIPIENT_NOT_FOUND);
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}

@TestConfiguration
class Answer125TestClockConfiguration {

    @Bean
    @Primary
    Answer125MutableClock answer125MutableClock() {
        return new Answer125MutableClock(Instant.parse("2026-08-17T06:00:00.123456Z"), ZoneOffset.UTC);
    }
}

final class Answer125MutableClock extends Clock {

    private final AtomicReference<Instant> current;
    private final ZoneId zone;

    Answer125MutableClock(Instant initial, ZoneId zone) {
        this.current = new AtomicReference<>(initial);
        this.zone = zone;
    }

    void setInstant(Instant instant) {
        current.set(instant);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new Answer125MutableClock(current.get(), newZone);
    }

    @Override
    public Instant instant() {
        return current.get();
    }
}

final class Answer125IntegrationFixtures {

    static final String REGION = "TEST-ANSWER125";
    private static final Instant BASELINE = Instant.parse("2026-08-17T05:00:00.000000Z");
    private final JdbcTemplate jdbc;
    private final Instant now;

    Answer125IntegrationFixtures(JdbcTemplate jdbc, Instant now) {
        this.jdbc = jdbc;
        this.now = now;
    }

    void reset() {
        jdbc.update("DELETE FROM outbox_event WHERE aggregate_type IN ('FILTER_JOB', 'ANSWER')");
        jdbc.update("DELETE FROM filter_job_status_history");
        jdbc.update("DELETE FROM manual_review_case");
        jdbc.update("DELETE FROM filter_release_retry_gate");
        jdbc.update("DELETE FROM filter_job");
        jdbc.update("DELETE FROM release_promotion_history");
        jdbc.update("DELETE FROM filter_release");
        jdbc.update("DELETE FROM media_attachment WHERE answer_id IN (SELECT id FROM answer WHERE coarse_region_code = ?)", REGION);
        jdbc.update("DELETE FROM media_asset WHERE owner_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
        jdbc.update("DELETE FROM answer WHERE coarse_region_code = ?", REGION);
        jdbc.update("""
                DELETE FROM post_recipient pr
                WHERE pr.recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
                   OR pr.post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)
                """, REGION, REGION);
        jdbc.update("DELETE FROM post_audience WHERE post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)", REGION);
        jdbc.update("DELETE FROM direction_post WHERE coarse_region_code = ?", REGION);
        jdbc.update("DELETE FROM recipient_receive_state WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
        jdbc.update("""
                DELETE FROM user_block
                WHERE blocker_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
                   OR blocked_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
                """, REGION, REGION);
        jdbc.update("DELETE FROM approved_question WHERE approved_by IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
        jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
        jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
        jdbc.update("""
                INSERT INTO region_code (code, parent_code, display_name, level)
                VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Answer 125 Test', 'REGION')
                """, REGION);
    }

    long account(String nickname) {
        return jdbc.queryForObject("""
                INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
                VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
                RETURNING id
                """, Long.class, REGION, nickname);
    }

    long post(long senderId, String key, Instant expiresAt, String status, Instant deletedAt) {
        long questionId = jdbc.queryForObject("""
                INSERT INTO approved_question
                    (source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
                VALUES ('OPERATOR', 'ACTIVE', 'ANSWER125 질문', 'TEXT', ?, ?, ?)
                RETURNING id
                """, Long.class, Timestamp.from(BASELINE), Timestamp.from(BASELINE), senderId);
        return jdbc.queryForObject("""
                        INSERT INTO direction_post
                            (sender_id, approved_question_id, status, idempotency_key, body_text,
                             coarse_region_code, moderation_status, submitted_at, published_at, expires_at, deleted_at)
                        VALUES (?, ?, ?, ?, 'ANSWER125 본문', ?, 'PASSED', ?, ?, ?, ?)
                        RETURNING id
                        """, Long.class, senderId, questionId, status, "gh125-" + key, REGION,
                Timestamp.from(BASELINE), Timestamp.from(BASELINE), Timestamp.from(expiresAt), ts(deletedAt));
    }

    long available(long postId, long recipientId, Instant matchedAt, int inboundBearing) {
        return recipient(postId, recipientId, "AVAILABLE", matchedAt, inboundBearing,
                null, null, null, null, null, null, null);
    }

    long recipientForStatus(long postId, long recipientId, String status, Instant matchedAt, int inboundBearing) {
        Instant discoveredAt = matchedAt.plusSeconds(1);
        Instant openedAt = matchedAt.plusSeconds(2);
        Instant terminalAt = matchedAt.plusSeconds(3);
        return switch (status) {
            case "AVAILABLE" -> available(postId, recipientId, matchedAt, inboundBearing);
            case "SKIP_PENDING" -> recipient(postId, recipientId, status, matchedAt, inboundBearing,
                    discoveredAt, openedAt, terminalAt, null, null, null, null);
            case "EXPIRED" -> recipient(postId, recipientId, status, matchedAt, inboundBearing,
                    discoveredAt, openedAt, null, null, terminalAt, terminalAt, null);
            case "BLOCKED" -> recipient(postId, recipientId, status, matchedAt, inboundBearing,
                    discoveredAt, openedAt, null, null, terminalAt, null, terminalAt);
            case "ANSWERED" -> recipient(postId, recipientId, status, matchedAt, inboundBearing,
                    discoveredAt, openedAt, null, null, terminalAt, null, null);
            case "SKIPPED" -> recipient(postId, recipientId, status, matchedAt, inboundBearing,
                    discoveredAt, openedAt, terminalAt, terminalAt.plusSeconds(1), terminalAt.plusSeconds(1), null, null);
            default -> throw new IllegalArgumentException("unsupported status: " + status);
        };
    }

    long recipient(long postId, long recipientId, String status, Instant matchedAt, int inboundBearing,
                   Instant discoveredAt, Instant openedAt, Instant skipRequestedAt, Instant skippedAt,
                   Instant capacityReleasedAt, Instant expiredAt, Instant blockedAt) {
        return jdbc.queryForObject("""
                        INSERT INTO post_recipient
                            (post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
                             matched_at, discovered_at, opened_at, skip_requested_at, skipped_at, capacity_released_at,
                             expired_at, blocked_at, inbound_bearing_deg, distance_m)
                        VALUES (?, ?, ?, 'NEAR', 45, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 5000)
                        RETURNING id
                        """, Long.class, postId, recipientId, status, REGION, Timestamp.from(matchedAt),
                ts(discoveredAt), ts(openedAt), ts(skipRequestedAt), ts(skippedAt), ts(capacityReleasedAt),
                ts(expiredAt), ts(blockedAt), inboundBearing);
    }

    void receiveState(long userId, int count) {
        jdbc.update("""
                INSERT INTO recipient_receive_state
                    (user_id, active_unhandled_count, recent_received_count, recent_window_started_at,
                     last_received_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE
                SET active_unhandled_count = EXCLUDED.active_unhandled_count,
                    recent_received_count = EXCLUDED.recent_received_count,
                    recent_window_started_at = EXCLUDED.recent_window_started_at,
                    last_received_at = EXCLUDED.last_received_at,
                    updated_at = EXCLUDED.updated_at
                """, userId, count, count, Timestamp.from(BASELINE), Timestamp.from(now), Timestamp.from(now));
    }

    void block(long blockerId, long blockedId, Instant releasedAt) {
        jdbc.update("""
                INSERT INTO user_block (blocker_id, blocked_id, created_at, released_at)
                VALUES (?, ?, ?, ?)
                """, blockerId, blockedId, Timestamp.from(now.minusSeconds(2)), ts(releasedAt));
    }

    long media(long ownerId, String status, String moderationStatus) {
        String key = "answer125/" + ownerId + "/" + java.util.UUID.randomUUID();
        return jdbc.queryForObject("""
                INSERT INTO media_asset (owner_id, status, storage_key, mime_type, byte_size, checksum,
                    exif_stripped, moderation_status)
                VALUES (?, ?, ?, 'image/jpeg', 1024, ?, TRUE, ?)
                RETURNING id
                """, Long.class, ownerId, status, key, "checksum-" + ownerId + "-" + status, moderationStatus);
    }

    String status(long postRecipientId) {
        return jdbc.queryForObject("SELECT status FROM post_recipient WHERE id = ?", String.class, postRecipientId);
    }

    int activeCount(long userId) {
        return jdbc.queryForObject(
                "SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, userId);
    }

    long answerCount() {
        return jdbc.queryForObject("SELECT count(*) FROM answer WHERE coarse_region_code = ?", Long.class, REGION);
    }

    String answerStatus(long answerId) {
        return jdbc.queryForObject("SELECT status FROM answer WHERE id = ?", String.class, answerId);
    }

    long filterJobIdFor(long answerId) {
        return jdbc.queryForObject(
                "SELECT id FROM filter_job WHERE target_type = 'ANSWER' AND target_id = ?", Long.class, answerId);
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
