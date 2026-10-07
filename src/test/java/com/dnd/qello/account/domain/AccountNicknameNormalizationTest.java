/*
 * Created at: 2026-10-07T11:00:43+09:00
 * Source scenario: TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-001 through UNIT-006, UNIT-010
 */
package com.dnd.qello.account.domain;

import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 보이지 않는 문자는 소스에 그대로 두지 않고 코드 포인트 상수로만 만든다. 리터럴이나 유니코드 이스케이프는
// 편집·포맷 도구를 거치며 실제 문자로 바뀌어 리뷰에서 보이지 않게 될 수 있다.
class AccountNicknameNormalizationTest {

	private static final String ZWSP = text(0x200B);
	private static final String ZWNJ = text(0x200C);
	private static final String ZWJ = text(0x200D);
	private static final String WORD_JOINER = text(0x2060);
	private static final String BOM = text(0xFEFF);
	private static final String RIGHT_TO_LEFT_OVERRIDE = text(0x202E);
	private static final String IDEOGRAPHIC_SPACE = text(0x3000);
	private static final String NBSP = text(0x00A0);
	private static final String TAB = text(0x0009);
	private static final String VARIATION_SELECTOR_16 = text(0xFE0F);
	private static final String MAN = text(0x1F468);
	private static final String WOMAN = text(0x1F469);
	private static final String GIRL = text(0x1F467);
	private static final String LIGHT_SKIN = text(0x1F3FB);
	private static final String LAPTOP = text(0x1F4BB);
	private static final String WHITE_FLAG = text(0x1F3F3);
	private static final String RAINBOW = text(0x1F308);
	private static final String BARAM = text(0xBC14, 0xB78C);
	private static final Instant NOW = Instant.parse("2026-10-07T02:00:00Z");

	@ParameterizedTest
	@MethodSource("invisibleVariantsOfBaram")
	@DisplayName("UNIT-001: zero-width·BOM·방향 제어 문자와 앞뒤 전각 공백·NBSP를 지워 같은 닉네임으로 맞춘다")
	void removesInvisibleCharactersAndOuterUnicodeSpaces(String nickname) {
		assertThat(Account.normalizeNickname(nickname)).isEqualTo(BARAM);
	}

	@ParameterizedTest
	@MethodSource("innerWhitespaceRuns")
	@DisplayName("UNIT-002: 안쪽의 연속 공백은 전각 공백·탭·NBSP를 포함해 한 칸으로 줄인다")
	void collapsesInnerWhitespaceRuns(String nickname) {
		assertThat(Account.normalizeNickname(nickname)).isEqualTo("여름 바람");
	}

	@Test
	@DisplayName("UNIT-003: 분해된 자모는 완성형으로 조합하고, NFKC는 적용하지 않아 호환 자모와 전각 영문은 그대로 둔다")
	void composesWithNfcButKeepsCompatibilityCharacters() {
		String decomposedBaram = text(0x1107, 0x1161, 0x1105, 0x1161, 0x11B7);
		String compatibilityJamo = text(0x314B, 0x314B);
		String fullwidthLatin = text(0xFF21, 0xFF22, 0xFF23);

		assertThat(Account.normalizeNickname(decomposedBaram)).isEqualTo(BARAM);
		assertThat(Account.normalizeNickname(compatibilityJamo)).isEqualTo(compatibilityJamo);
		assertThat(Account.normalizeNickname(fullwidthLatin)).isEqualTo(fullwidthLatin);
	}

	@ParameterizedTest
	@MethodSource("valuesThatBecomeEmpty")
	@DisplayName("UNIT-004: 정규화하면 비는 닉네임은 REQUIRED_VALUE_MISSING이다")
	void rejectsNicknameThatBecomesEmpty(String nickname) {
		assertThatThrownBy(() -> Account.normalizeNickname(nickname))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("UNIT-004: 길이는 정규화한 뒤에 세어, 보이는 글자 51자는 TEXT_TOO_LONG이고 50자에 보이지 않는 문자를 더한 값은 통과한다")
	void countsLengthAfterNormalization() {
		assertThatThrownBy(() -> Account.normalizeNickname("가".repeat(51)))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.TEXT_TOO_LONG);

		assertThat(Account.normalizeNickname("가".repeat(50) + ZWSP.repeat(10))).isEqualTo("가".repeat(50));
	}

	@Test
	@DisplayName("UNIT-005: 새 입력 경로는 모두 정규화한 닉네임을 저장하고, 가입할 때 닉네임을 비우면 계속 null이다")
	void newInputPathsStoreNormalizedNickname() {
		String spoofed = "바" + ZWSP + "람";
		Account user = Account.createUser("KR", "KR", "ko-KR", "Asia/Seoul", spoofed);
		Account operator = Account.createOperator("KR", "ko-KR", "Asia/Seoul", spoofed);
		Account changed = restored("기존닉네임").changeNickname(IDEOGRAPHIC_SPACE + spoofed + " ", NOW);
		Account updated = restored("기존닉네임").updateProfile("KR", "ko-KR", "Asia/Seoul", spoofed);

		assertThat(user.getNickname()).isEqualTo(BARAM);
		assertThat(operator.getNickname()).isEqualTo(BARAM);
		assertThat(changed.getNickname()).isEqualTo(BARAM);
		assertThat(updated.getNickname()).isEqualTo(BARAM);
		assertThat(Account.normalizeNickname(changed.getNickname())).isEqualTo(changed.getNickname());
		assertThat(Account.createUser("KR", "KR", "ko-KR", "Asia/Seoul", null).getNickname()).isNull();
	}

	@Test
	@DisplayName("UNIT-006: 이전 규칙으로 저장된 닉네임은 restore와 상태 전이 뒤에도 저장값 그대로 남는다")
	void restoreKeepsStoredNickname() {
		String legacyNickname = BARAM + ZWSP;
		Account legacy = restored(legacyNickname);
		Account invisibleOnly = restored(ZWSP);

		assertThat(legacy.getNickname()).isEqualTo(legacyNickname);
		assertThat(legacy.block().getNickname()).isEqualTo(legacyNickname);
		assertThat(legacy.withProfileImage(1L).getNickname()).isEqualTo(legacyNickname);
		assertThat(invisibleOnly.getNickname()).isEqualTo(ZWSP);
		assertThat(invisibleOnly.block().getNickname()).isEqualTo(ZWSP);
	}

	@ParameterizedTest
	@MethodSource("emojiSequences")
	@DisplayName("UNIT-010: 이모지 결합에 쓰인 ZWJ는 피부색 수식자·변형 선택자를 건너뛰어 판정하고 그대로 남긴다")
	void keepsJoinersInsideEmojiSequences(String sequence) {
		assertThat(Account.normalizeNickname(sequence)).isEqualTo(sequence);
	}

	@ParameterizedTest
	@MethodSource("strayJoiners")
	@DisplayName("UNIT-010: 글자 옆·문자열 끝의 ZWJ와 모든 ZWNJ는 지우고, 연속한 ZWJ는 하나만 남긴다")
	void removesJoinersOutsideEmojiSequences(String nickname, String expected) {
		assertThat(Account.normalizeNickname(nickname)).isEqualTo(expected);
	}

	private static Stream<String> invisibleVariantsOfBaram() {
		return Stream.of(
				BARAM + ZWSP,
				"바" + ZWSP + "람",
				BOM + BARAM,
				BARAM + WORD_JOINER,
				RIGHT_TO_LEFT_OVERRIDE + BARAM,
				IDEOGRAPHIC_SPACE + BARAM,
				BARAM + IDEOGRAPHIC_SPACE,
				NBSP + BARAM);
	}

	private static Stream<String> innerWhitespaceRuns() {
		return Stream.of(
				"여름" + IDEOGRAPHIC_SPACE + IDEOGRAPHIC_SPACE + "바람",
				"여름" + TAB + "바람",
				"여름  " + NBSP + "바람");
	}

	private static Stream<String> valuesThatBecomeEmpty() {
		return Stream.of(ZWSP, BOM, IDEOGRAPHIC_SPACE, ZWSP + IDEOGRAPHIC_SPACE + ZWSP);
	}

	private static Stream<String> emojiSequences() {
		return Stream.of(
				MAN + ZWJ + WOMAN + ZWJ + GIRL,
				WOMAN + LIGHT_SKIN + ZWJ + LAPTOP,
				WHITE_FLAG + VARIATION_SELECTOR_16 + ZWJ + RAINBOW);
	}

	private static Stream<Arguments> strayJoiners() {
		return Stream.of(
				Arguments.of("바" + ZWJ + "람", BARAM),
				Arguments.of(MAN + ZWJ + BARAM, MAN + BARAM),
				Arguments.of(BARAM + ZWJ + MAN, BARAM + MAN),
				Arguments.of(ZWJ + MAN, MAN),
				Arguments.of(MAN + ZWJ, MAN),
				Arguments.of(MAN + ZWNJ + WOMAN, MAN + WOMAN),
				Arguments.of(MAN + ZWJ + ZWJ + WOMAN, MAN + ZWJ + WOMAN));
	}

	private static Account restored(String nickname) {
		return Account.restore(1L, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR", "ko-KR", "Asia/Seoul",
				nickname, null);
	}

	private static String text(int... codePoints) {
		return new String(codePoints, 0, codePoints.length);
	}
}
