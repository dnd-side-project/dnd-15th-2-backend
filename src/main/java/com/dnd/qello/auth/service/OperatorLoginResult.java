package com.dnd.qello.auth.service;

/**
 * 로그인 판정 결과.
 *
 * @param userId
 *            로그인한 운영자의 계정 id
 * @param passwordChangeRequired
 *            초기 비밀번호이거나 변경 주기가 지나 비밀번호를 바꿔야 하는지(#376). true면 세션은 비밀번호 변경과 로그아웃만
 *            할 수 있다
 */
public record OperatorLoginResult(long userId, boolean passwordChangeRequired) {
}
