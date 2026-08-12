package com.dnd.qello.filtering.moderation;

// release에 귀속된 정규화 규칙 포트. 실제 정규화 규칙 내용은 이 이슈의 범위가
// 아니다 — normalizationRef가 가리키는 규칙 집합의 선택과 구현은 구현체의 책임이다.
public interface TextNormalizer {

	String normalize(String rawContent, String normalizationRef);
}
