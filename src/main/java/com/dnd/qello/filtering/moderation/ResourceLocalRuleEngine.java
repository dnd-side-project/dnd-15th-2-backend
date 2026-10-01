package com.dnd.qello.filtering.moderation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// LocalRuleEngine 실제 구현체(#287). localRulesetRef가 가리키는 규칙 집합을 classpath
// 리소스(filtering/rulesets/<ref>.rules)에서 기동 시 한 번 읽어 불변으로 들고 있다. 규칙을
// 바꾸려면 새 파일과 새 release가 필요하므로 변경 이력이 release에 남는다.
//
// 리소스 형식: 한 줄에 `ruleId=term`. 빈 줄과 `#` 주석은 무시한다. 형식이 깨졌거나 리소스가
// 없으면 기동 시점에 예외로 실패한다 — 규칙 없음으로 조용히 대체하지 않는다. 운영 기본 규칙
// 집합(LOCAL_RULES_V1)은 항목이 없다. 판정은 공급자 호출에 위임된다.
//
// 비교는 접기(fold)한 문자열끼리 한다 — NFKC, 소문자, 글자·숫자 외 문자(구분자·공백) 제거.
// 정규화(TextNormalizer)는 공급자에 보내는 텍스트를 보존하므로 이 접기는 여기서만 쓴다.
//
// 규칙 원문과 입력 원문은 로그·예외 메시지에 남기지 않는다. 매칭 시 ruleId만 남긴다
// (INV-CMP-001, INV-CMP-002).
public class ResourceLocalRuleEngine implements LocalRuleEngine {

	public static final String LOCAL_RULES_V1 = "local-rules-v1";

	private static final Logger log = LoggerFactory.getLogger(ResourceLocalRuleEngine.class);
	private static final String RESOURCE_PREFIX = "filtering/rulesets/";
	private static final String RESOURCE_SUFFIX = ".rules";

	private final Map<String, List<FoldedRule>> rulesetsByRef;

	private ResourceLocalRuleEngine(Map<String, List<FoldedRule>> rulesetsByRef) {
		this.rulesetsByRef = rulesetsByRef;
	}

	// 운영 조립에서 쓰는 기본 구성. 지원하는 ref는 LOCAL_RULES_V1 하나다.
	public static ResourceLocalRuleEngine loadDefault() {
		return load(ResourceLocalRuleEngine.class.getClassLoader(), List.of(LOCAL_RULES_V1));
	}

	public static ResourceLocalRuleEngine load(ClassLoader classLoader, List<String> supportedRefs) {
		Map<String, List<FoldedRule>> loaded = new HashMap<>();
		for (String ref : supportedRefs) {
			loaded.put(ref, List.copyOf(readRuleset(classLoader, ref)));
		}
		return new ResourceLocalRuleEngine(Map.copyOf(loaded));
	}

	@Override
	public LocalRuleVerdict evaluate(String normalizedContent, String localRulesetRef) {
		List<FoldedRule> rules = localRulesetRef == null ? null : rulesetsByRef.get(localRulesetRef);
		if (rules == null) {
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, "localRulesetRef",
					"지원하지 않는 로컬 규칙 집합 참조입니다");
		}
		if (normalizedContent == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "normalizedContent");
		}
		String folded = fold(normalizedContent);
		for (FoldedRule rule : rules) {
			if (folded.contains(rule.foldedTerm())) {
				log.info("로컬 규칙 적중: ruleId={}", rule.ruleId());
				return LocalRuleVerdict.block(rule.ruleId());
			}
		}
		return LocalRuleVerdict.noMatch();
	}

	private static List<FoldedRule> readRuleset(ClassLoader classLoader, String ref) {
		String path = RESOURCE_PREFIX + ref + RESOURCE_SUFFIX;
		try (InputStream stream = classLoader.getResourceAsStream(path)) {
			if (stream == null) {
				throw new IllegalStateException("로컬 규칙 리소스를 찾을 수 없습니다: " + path);
			}
			List<FoldedRule> rules = new ArrayList<>();
			String[] lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
			for (int index = 0; index < lines.length; index++) {
				parseLine(lines[index], path, index + 1).ifPresent(rules::add);
			}
			return rules;
		} catch (IOException e) {
			throw new UncheckedIOException("로컬 규칙 리소스를 읽지 못했습니다: " + path, e);
		}
	}

	// 오류 메시지에는 리소스 경로와 줄 번호만 담고 규칙 내용은 담지 않는다.
	private static Optional<FoldedRule> parseLine(String line, String path, int lineNumber) {
		String trimmed = line.strip();
		if (trimmed.isEmpty() || trimmed.startsWith("#")) {
			return Optional.empty();
		}
		int separator = trimmed.indexOf('=');
		String ruleId = separator < 0 ? "" : trimmed.substring(0, separator).strip();
		String foldedTerm = separator < 0 ? "" : fold(trimmed.substring(separator + 1));
		if (ruleId.isEmpty() || foldedTerm.isEmpty()) {
			throw new IllegalStateException("로컬 규칙 형식이 올바르지 않습니다: " + path + ":" + lineNumber);
		}
		return Optional.of(new FoldedRule(ruleId, foldedTerm));
	}

	private static String fold(String text) {
		String compatibility = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		StringBuilder folded = new StringBuilder(compatibility.length());
		compatibility.codePoints()
				.filter(Character::isLetterOrDigit)
				.forEach(folded::appendCodePoint);
		return folded.toString();
	}

	private record FoldedRule(String ruleId, String foldedTerm) {
	}
}
