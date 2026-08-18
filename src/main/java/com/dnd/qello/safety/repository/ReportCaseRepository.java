package com.dnd.qello.safety.repository;

import java.util.Optional;

import com.dnd.qello.safety.domain.ReportCase;

public interface ReportCaseRepository {

	/** ON CONFLICT 없는 순수 INSERT. 같은 대상에 이미 열린 사건이 있으면 unique violation을 던진다. */
	ReportCase save(ReportCase reportCase);

	Optional<ReportCase> findById(long id);

	ReportCase update(ReportCase reportCase);
}
