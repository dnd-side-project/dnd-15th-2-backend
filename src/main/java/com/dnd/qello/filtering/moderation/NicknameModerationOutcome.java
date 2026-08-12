package com.dnd.qello.filtering.moderation;

// 닉네임 동기 게이트(NicknameSyncModerationGate)의 최종 출력(GitHub #106).
// ALLOWED만 서비스 진입을 허용하고 그 밖의 모든 경우는 REJECTED다 — 판정
// 불가(UNAVAILABLE)를 ALLOWED로 바꾸는 경로가 없다(INV-GEN-002, INV-NICK-001,
// INV-NICK-005). reason은 원인(BLOCK/판정 불가)을 구분할 수 있게 관측 목적으로
// 남긴다(INV-GEN-006).
//
// 게이트는 이 결과를 예외 없이 반환한다 — 호출자가 명확한 실패 결과로 서비스
// 진입 차단 여부를 판단할 수 있게 하기 위한 계약이다
// (TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER §2 설계 가정 3).
public sealed interface NicknameModerationOutcome {

	record Allowed() implements NicknameModerationOutcome {
	}

	record Rejected(Reason reason) implements NicknameModerationOutcome {

		public Rejected {
			if (reason == null) {
				throw new IllegalArgumentException("reason must not be null");
			}
		}
	}

	enum Reason {
		// 주 판정기가 명시적 BLOCK을 반환함 — 확정 결과이며 보조 판정기가 관여하지 않았다
		BLOCKED_BY_PRIMARY,
		// 주 판정기 timeout/error 후 호출된 보조 판정기가 명시적 BLOCK을 반환함
		BLOCKED_BY_SECONDARY,
		// 주·보조 판정기가 모두 timeout/error여서 fail-closed로 거부함(판정 불가)
		UNAVAILABLE
	}

	static NicknameModerationOutcome allowed() {
		return new Allowed();
	}

	static NicknameModerationOutcome rejected(Reason reason) {
		return new Rejected(reason);
	}
}
