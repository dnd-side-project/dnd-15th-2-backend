package com.dnd.qello.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;

// 로그인 성공 응답의 data.
//
// 세션 자체는 Set-Cookie로 전달한다. 본문에는 클라이언트가 화면을 구성할 때 필요한
// 식별자만 담고 권한 정보나 토큰은 넣지 않는다.
@Schema(description = "로그인 성공 응답. 세션은 Set-Cookie로 전달되며 본문에는 담기지 않는다.")
public record OperatorSessionResponse(
	@Schema(description = "로그인한 운영자의 계정 id")
	long userId
) {
}
