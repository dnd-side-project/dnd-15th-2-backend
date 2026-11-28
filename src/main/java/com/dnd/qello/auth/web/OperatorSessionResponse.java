package com.dnd.qello.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;

// 로그인 성공 응답의 data.
//
// 세션 자체는 Set-Cookie로 전달한다. 본문에는 클라이언트가 화면을 구성할 때 필요한
// 식별자만 담고 권한 정보나 토큰은 넣지 않는다.
//
// passwordChangeRequired가 true면 세션은 비밀번호 변경과 로그아웃만 할 수 있다(#376). 초기 비밀번호와 변경 주기
// 만료를 구분하지 않는다. 프론트가 할 일은 어느 쪽이든 변경 화면을 띄우는 것 하나다.
@Schema(description = "로그인 성공 응답입니다. 세션은 Set-Cookie로 전달되며 본문에는 담기지 않습니다.")
public record OperatorSessionResponse(
		@Schema(description = "로그인한 운영자의 계정 식별자") long userId,

		@Schema(description = "비밀번호를 바꿔야 하는지 여부입니다. 초기 비밀번호이거나 변경 주기가 지났으면 true이고, "
				+ "이때 세션은 POST /admin/password와 POST /admin/logout(그리고 인증 없이 열린 GET /admin/csrf, "
				+ "POST /admin/login)만 호출할 수 있습니다.") boolean passwordChangeRequired) {
}
