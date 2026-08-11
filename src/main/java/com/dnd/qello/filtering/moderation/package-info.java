/**
 * 닉네임 동기 경로와 답변 비동기 경로가 공유하는 공통 moderation 판정 pipeline.
 * 정규화, 고신뢰 로컬 규칙, 외부 Moderation 공급자 호출, 내부 정책 결합의
 * 오케스트레이션만 소유한다. 실제 정규화 규칙·사전·category mapping·threshold
 * 내용과, 닉네임·답변 호출 지점의 실제 배선은 이 패키지의 범위가 아니다.
 */
package com.dnd.qello.filtering.moderation;
