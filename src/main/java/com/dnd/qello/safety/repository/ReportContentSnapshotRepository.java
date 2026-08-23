package com.dnd.qello.safety.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.safety.domain.ReportContentSnapshot;

// 본문·해시 등 증거 자체는 append-only라 UPDATE·DELETE 메서드를 두지 않는다 —
// 이 계약과 DB 트리거 양쪽에서 강제한다. purgeMedia(#157)만 유일한 예외로,
// media_object_keys를 비우는 좁은 UPDATE라 별도 메서드로 분리해 일반 UPDATE와
// 구분한다.
public interface ReportContentSnapshotRepository {

	ReportContentSnapshot save(ReportContentSnapshot snapshot);

	Optional<ReportContentSnapshot> findByReportId(long reportId);

	/** legal_hold가 아니고 purge_after가 지났으며 아직 미디어가 남은 스냅샷 후보(#157). */
	List<ReportContentSnapshot> findPurgeable(Instant now, int limit);

	/**
	 * media_object_keys만 비우는 좁은 UPDATE(#157) — DB 트리거가 이 형태 외의
	 * 모든 변경을 거부한다. legal_hold 행에 호출하면 트리거가 예외를 던진다.
	 */
	void purgeMedia(long reportId);
}
