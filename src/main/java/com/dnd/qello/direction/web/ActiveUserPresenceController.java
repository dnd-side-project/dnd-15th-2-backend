package com.dnd.qello.direction.web;

import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.direction.service.DirectionPresenceService;
import com.dnd.qello.direction.web.request.UpdateActiveUserPresenceRequest;
import com.dnd.qello.direction.web.response.UpdateActiveUserPresenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/direction/presence")
public class ActiveUserPresenceController implements ActiveUserPresenceApiSpec {

    private final DirectionPresenceService presenceService;
    private final ApiResponseFactory responseFactory;

    @Override
    public ResponseEntity<ApiResponse<UpdateActiveUserPresenceResponse>> update(
            UpdateActiveUserPresenceRequest request,
            Authentication authentication) {
        boolean applied = presenceService.update(authenticatedUserId(authentication), request.toCommand());
        return ResponseEntity.ok(responseFactory.success(new UpdateActiveUserPresenceResponse(applied)));
    }

    private long authenticatedUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw unauthorized();
        }
        try {
            long userId = Long.parseLong(authentication.getName());
            if (userId <= 0) {
                throw unauthorized();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw unauthorized();
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 사용자 정보가 유효하지 않습니다");
    }
}
