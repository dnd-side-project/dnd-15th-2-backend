package com.dnd.qello.filtering.moderation;

// NicknameModerationChecker의 production 구현체(#168). qello.filtering.production.enabled가
// true일 때만 빈으로 등록되며(NicknameModerationGateConfig), 실제 판정을 전부
// NicknameSyncModerationGate(#106)에 위임한다.
public class GatedNicknameModerationChecker implements NicknameModerationChecker {

	private final NicknameSyncModerationGate gate;

	public GatedNicknameModerationChecker(NicknameSyncModerationGate gate) {
		this.gate = gate;
	}

	@Override
	public NicknameModerationOutcome check(String nickname, ModerationLanguage language) {
		return gate.evaluate(nickname, language);
	}
}
