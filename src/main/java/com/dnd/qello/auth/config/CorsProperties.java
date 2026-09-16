package com.dnd.qello.auth.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// /api/** CORS 허용 origin. 코드에 고정하지 않고 환경별로 주입한다(#230).
//
// 기본값을 빈 목록으로 둔다. 값을 채우지 않은 프로필(예: 기존 local/test)은
// 브라우저 Origin이 없는 호출만 받으므로 CORS가 비활성인 채로 계속 동작한다.
@ConfigurationProperties(prefix = "qello.web")
public record CorsProperties(
		@DefaultValue List<String> allowedOrigins) {

}
