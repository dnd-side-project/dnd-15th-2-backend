package com.dnd.qello.auth.service;

import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.auth.token.IssuedAccessToken;

// 재발급 결과. 앱이 탈퇴 유예 중인 계정인지 알고 철회 화면을 띄울 수 있게 계정 상태를 함께 돌려준다(#337).
public record DeviceTokenReissue(IssuedAccessToken accessToken, AccountStatus accountStatus) {
}
