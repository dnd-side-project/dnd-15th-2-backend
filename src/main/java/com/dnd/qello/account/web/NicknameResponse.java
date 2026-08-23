package com.dnd.qello.account.web;

import com.dnd.qello.account.domain.Account;

import io.swagger.v3.oas.annotations.media.Schema;

// 닉네임 변경 응답. 변경된 닉네임만 담고 다른 계정 필드는 노출하지 않는다.
public record NicknameResponse(
	@Schema(description = "변경된 닉네임.") String nickname
) {

	public static NicknameResponse from(Account account) {
		return new NicknameResponse(account.getNickname());
	}
}
