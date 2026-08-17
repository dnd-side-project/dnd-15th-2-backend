package com.dnd.qello.safety.repository;

import com.dnd.qello.safety.domain.ReportCaseEvent;

// UPDATE·DELETE 메서드를 의도적으로 두지 않는다 — append-only는 저장소 계약과
// DB 트리거 양쪽에서 강제한다.
public interface ReportCaseEventRepository {

	ReportCaseEvent save(ReportCaseEvent event);
}
