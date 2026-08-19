package com.dnd.qello.filtering.moderation;

// TextNormalizer의 최소 실제 구현체(#168). 정교한 정규화 규칙(자모 분리 우회,
// 특수문자 치환 등)은 이 이슈의 범위 밖이다 — trim만 적용해 원문을 그대로
// 공급자에 전달한다. normalizationRef는 release가 어떤 규칙 집합을 가리키는지
// 식별하는 값일 뿐, 이 구현체는 그 값을 해석하지 않는다.
public class PassthroughTextNormalizer implements TextNormalizer {

	@Override
	public String normalize(String rawContent, String normalizationRef) {
		return rawContent == null ? null : rawContent.trim();
	}
}
