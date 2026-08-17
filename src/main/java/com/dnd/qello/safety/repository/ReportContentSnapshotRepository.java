package com.dnd.qello.safety.repository;

import java.util.Optional;

import com.dnd.qello.safety.domain.ReportContentSnapshot;

// UPDATE·DELETE 메서드를 의도적으로 두지 않는다 — append-only는 저장소 계약과
// DB 트리거 양쪽에서 강제한다.
public interface ReportContentSnapshotRepository {

	ReportContentSnapshot save(ReportContentSnapshot snapshot);

	Optional<ReportContentSnapshot> findByReportId(long reportId);
}
