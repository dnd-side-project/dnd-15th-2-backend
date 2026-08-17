package com.dnd.qello.filtering.moderation;

import java.util.Optional;

import com.dnd.qello.filtering.domain.FilterTargetType;

// 이의제기 인용(OVERTURN_HIDDEN) 직전에 "moderation 말고 다른 이유로 이 콘텐츠를
// 공개하면 안 되는 사정이 있는가"를 되묻는 포트.
//
// 계정 차단·삭제, 법적 명령처럼 필터링 판정과 무관한 공개 금지 사유가 남아 있는데
// 복원 콜백을 내보내면, appeal 한 건으로 그 사유가 통째로 무력화된다.
public interface PublicationBlockChecker {

	// 공개를 막는 사유가 있으면 30자 이내의 사유 코드를, 없으면 빈 값을 돌려준다.
	//
	// 사유 코드가 열거형이 아닌 문자열인 이유: 공개 금지 사유의 목록이 아직
	// 확정되지 않았고, 새 사유가 생길 때마다 필터링 도메인을 고치지 않아도
	// 되게 하려는 것이다.
	//
	// 확인할 수 없으면 예외를 던진다. "확인하지 못했다"를 "차단이 없다"로
	// 해석하면 안 되므로 빈 값으로 돌려주지 않는다.
	Optional<String> findPublicationBlockReason(FilterTargetType targetType, long targetId);
}
