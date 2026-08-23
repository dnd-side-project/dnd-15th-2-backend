package com.dnd.qello.direction.web.request;

import java.math.BigDecimal;
import java.time.Instant;

import com.dnd.qello.direction.service.DirectionPresenceService;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record UpdateActiveUserPresenceRequest(
	@NotNull(message = "latitude는 필수입니다")
	@DecimalMin(value = "-90", message = "latitude 범위가 유효하지 않습니다")
	@DecimalMax(value = "90", message = "latitude 범위가 유효하지 않습니다")
	@Schema(minimum = "-90", maximum = "90", description = "위도")
	BigDecimal latitude,

	@NotNull(message = "longitude는 필수입니다")
	@DecimalMin(value = "-180", message = "longitude 범위가 유효하지 않습니다")
	@DecimalMax(value = "180", message = "longitude 범위가 유효하지 않습니다")
	@Schema(minimum = "-180", maximum = "180", description = "경도")
	BigDecimal longitude,

	@NotNull(message = "accuracyMeters는 필수입니다")
	@Schema(minimum = "0", description = "기기 위치 정확도(m). 운영 상한은 서버 설정을 따른다.")
	BigDecimal accuracyMeters,

	@NotNull(message = "receiveAllowed는 필수입니다")
	@Schema(description = "이 위치에서 질문을 받을지 여부")
	Boolean receiveAllowed,

	@NotNull(message = "observedAt은 필수입니다")
	@Schema(description = "기기가 이 위치를 관측한 시각. 서버 시각 기준 허용 범위를 벗어나면 저장하지 않습니다")
	Instant observedAt
) {
	public DirectionPresenceService.UpdateCommand toCommand() {
		return new DirectionPresenceService.UpdateCommand(
			latitude, longitude, accuracyMeters, receiveAllowed, observedAt);
	}

	@Override
	public String toString() {
		return "UpdateActiveUserPresenceRequest[redacted]";
	}
}
