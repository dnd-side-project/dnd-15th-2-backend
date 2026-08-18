package com.dnd.qello.safety.domain;

import java.time.Instant;
import java.util.List;

// 신고 대상의 현재 콘텐츠. 열람 자격이 있는 신고자에게만 조회된다(#154).
// ReportContentSnapshot.capture(...)의 입력으로 그대로 쓰인다.
public record ReportTargetSnapshot(long authorId, String bodyText, List<String> mediaObjectKeys,
	int editCount, Instant contentPublishedAt) {

	public ReportTargetSnapshot {
		mediaObjectKeys = mediaObjectKeys == null ? List.of() : List.copyOf(mediaObjectKeys);
	}
}
