package com.dnd.qello.auth.web;

// 클라이언트가 다음 상태 변경 요청에 실어 보낼 CSRF 토큰과 그 헤더 이름.
public record CsrfTokenResponse(String headerName, String token) {
}
