package com.dnd.qello.auth.web;

// 로그인 성공 응답의 data.
//
// 세션 자체는 Set-Cookie로 전달한다. 본문에는 클라이언트가 화면을 구성할 때 필요한
// 식별자만 담고 권한 정보나 토큰은 넣지 않는다.
public record OperatorSessionResponse(long userId) {
}
