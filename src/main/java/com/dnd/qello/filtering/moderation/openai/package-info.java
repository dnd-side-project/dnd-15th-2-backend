/**
 * 1차 Moderation 공급자 OpenAI(DESIGN.md 결정 8)를 감싸는 벤더 중립 어댑터.
 * {@code com.dnd.qello.filtering.moderation.ModerationProviderClient} 포트의
 * 유일한 구현체이며, 이 패키지 밖에서는 그 포트로만 참조한다. 이 패키지의 클래스는
 * 의도적으로 Spring 컴포넌트 스캔 대상이 아니다 — 실제 빈 등록과 실행 자원 배선은
 * 호출자(#106/#107)가 맡는다.
 */
package com.dnd.qello.filtering.moderation.openai;
