package com.dnd.qello.auth.token;

// 발급된 액세스 토큰과 남은 만료 시간(초).
public record IssuedAccessToken(String value, long expiresInSeconds) {
}
