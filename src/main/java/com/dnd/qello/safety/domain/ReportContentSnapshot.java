package com.dnd.qello.safety.domain;

import java.time.Instant;
import java.util.List;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

// 신고 시점 증거의 비정규화 사본. author_id에 FK를 걸지 않는다 — 계정 삭제와
// 증거 보존이 충돌하면 증거가 이긴다(설계 문서 §7). DB 트리거가 이 레코드가
// 저장된 뒤의 UPDATE·DELETE를 전부 거부한다(INV-RPT-004).
public record ReportContentSnapshot(long reportId, Instant capturedAt, ReportTargetType targetType,
	long targetId, long authorId, String bodyText, List<String> mediaObjectKeys, int editCount,
	Instant contentPublishedAt, String contentHash, boolean legalHold, Instant purgeAfter) {

	public ReportContentSnapshot {
		requirePositive(reportId, "reportId");
		if (capturedAt == null || targetType == null) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, null, "스냅샷 필수 값이 없습니다");
		}
		requirePositive(targetId, "targetId");
		requirePositive(authorId, "authorId");
		mediaObjectKeys = mediaObjectKeys == null ? List.of() : List.copyOf(mediaObjectKeys);
		if (editCount < 0) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_SNAPSHOT_EDIT_COUNT, "editCount", "editCount는 음수일 수 없습니다");
		}
		if (contentHash == null || contentHash.isBlank()) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, "contentHash", "contentHash는 필수입니다");
		}
	}

	/** purgeAfter는 호출자가 {@code capturedAt + EvidenceRetentionPolicy.retentionPeriod()}로 계산해 넘긴다(#157). */
	public static ReportContentSnapshot capture(long reportId, Instant capturedAt, ReportTargetType targetType,
		long targetId, long authorId, String bodyText, List<String> mediaObjectKeys, int editCount,
		Instant contentPublishedAt, Instant purgeAfter) {
		return new ReportContentSnapshot(reportId, capturedAt, targetType, targetId, authorId, bodyText,
			mediaObjectKeys, editCount, contentPublishedAt, ReportContentHasher.hash(bodyText, mediaObjectKeys),
			false, purgeAfter);
	}

	public static ReportContentSnapshot restore(long reportId, Instant capturedAt, ReportTargetType targetType,
		long targetId, long authorId, String bodyText, List<String> mediaObjectKeys, int editCount,
		Instant contentPublishedAt, String contentHash, boolean legalHold, Instant purgeAfter) {
		return new ReportContentSnapshot(reportId, capturedAt, targetType, targetId, authorId, bodyText,
			mediaObjectKeys, editCount, contentPublishedAt, contentHash, legalHold, purgeAfter);
	}

	private static void requirePositive(long value, String field) {
		if (value <= 0) {
			throw new SafetyException(SafetyErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
	}
}
