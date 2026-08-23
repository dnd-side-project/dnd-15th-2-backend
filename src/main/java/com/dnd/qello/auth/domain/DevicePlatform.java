package com.dnd.qello.auth.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기기를 등록한 앱 플랫폼")
public enum DevicePlatform {
	IOS,
	ANDROID
}
