package com.dnd.qello.feed.view;

import com.dnd.qello.direction.domain.PostRecipientStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * `내게 온 질문` 카드 1장.
 * 답변이 수신 자격자 전원에게 공개되면서
 * answerCount/reactionCount/unreadAnswerCount를 노출한다
 * inboundBearingDegrees는 수신자 기준 방향이다(발송자 기준 matched_bearing_deg가
 * 아니다) — 화면에 표시하는 방향은 언제나 이 값에서 파생한다.
 * reactedByMe는 조회하는 뷰어 본인의 질문글 공감 여부다 — 하트를 채워 그릴지 비워
 * 그릴지 클라이언트가 알아야 하는 값이고, reactionCount만으로는 알 수 없다.
 * distanceM과 distanceBand는 상호 배타적이다 — 근거리 하한 미만이면 distanceM이
 * null이고 distanceBand만 채워지며, 하한 이상이면 반대다. 판정은 SQL이 하므로 여기서는
 * 값을 그대로 옮긴다.
 */
public record InboxCard(
        long postRecipientId,
        long postId,
        PostRecipientStatus status,
        String questionText,
        String bodyText,
        List<Long> mediaIds,
        String senderCoarseRegionCode,
        BigDecimal inboundBearingDegrees,
        Long distanceM,
        String distanceBand,
        Instant matchedAt,
        Instant expiresAt,
        long answerCount,
        boolean reactedByMe,
        long reactionCount,
        long unreadAnswerCount
) {
    public InboxCard {
        mediaIds = List.copyOf(mediaIds);
    }
}
