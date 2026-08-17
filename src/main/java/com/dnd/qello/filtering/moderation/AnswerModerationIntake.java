package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterTarget;

// answer 담당 코드가 filtering 내부 구현(FilterJob 필드, release 조회, outbox payload 형식)을
// 몰라도 되게 하는 진입점 seam. AnswerModerationJobIntakeService가 유일한
// 구현체다 — deadlineWindow 운영값이 정해지기 전에는 이 인터페이스의 Spring bean이 없으므로
// answer 호출자는 Optional 주입으로 부재를 감지해야 한다.
public interface AnswerModerationIntake {

    FilterJob submit(FilterTarget target, String rawContent, ModerationLanguage language, String idempotencyKey);
}
