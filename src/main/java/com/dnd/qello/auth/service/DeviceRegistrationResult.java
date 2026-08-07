package com.dnd.qello.auth.service;

import com.dnd.qello.auth.security.DeviceSecret;
import com.dnd.qello.auth.token.IssuedAccessToken;

// 기기 등록 결과. deviceSecret은 이 결과에서만 평문으로 존재하고 컨트롤러가 응답 한 번에만 쓴다.
public record DeviceRegistrationResult(long userId, DeviceSecret deviceSecret, IssuedAccessToken accessToken) {
}
