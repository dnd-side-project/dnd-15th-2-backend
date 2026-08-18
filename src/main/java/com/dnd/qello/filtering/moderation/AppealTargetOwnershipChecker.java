package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.domain.FilterTargetType;

// "이 대상을 작성한 사람이 맞는가"를 호출자 도메인에 되묻는 포트.
//
// 필터링 시스템은 target_id의 의미를 해석하지 않으므로 작성자를 스스로 알 수
// 없다. 인터페이스는 이 패키지가 소유하고 구현체가 answer를 참조하므로,
// filtering -> answer 방향 의존은 생기지 않는다.
public interface AppealTargetOwnershipChecker {

	// 판단할 수 없으면 false를 돌려준다. 확인 실패를 소유권 인정으로 해석하지 않는다.
	boolean isOwnedBy(FilterTargetType targetType, long targetId, long userId);
}
