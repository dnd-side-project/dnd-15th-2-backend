package com.dnd.qello.account.web;

import java.time.Instant;

import com.dnd.qello.account.service.AccountWithdrawal;

import io.swagger.v3.oas.annotations.media.Schema;

// 탈퇴 요청·철회 응답(#337).
@Schema(description = "탈퇴 요청·철회 결과")
public record AccountWithdrawalResponse(
		@Schema(description = "처리 후 계정 상태", allowableValues = {
				"WITHDRAWAL_PENDING", "ACTIVE"}) String status,
		@Schema(description = "계정이 삭제될 예정 시각. 탈퇴 유예 중일 때만 값이 있고 철회하면 null입니다", nullable = true) Instant scheduledDeletionAt){

	public static AccountWithdrawalResponse from(AccountWithdrawal withdrawal) {
		return new AccountWithdrawalResponse(withdrawal.status().name(), withdrawal.scheduledDeletionAt());
	}
}
