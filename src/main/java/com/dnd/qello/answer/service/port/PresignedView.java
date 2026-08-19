package com.dnd.qello.answer.service.port;

import java.net.URL;
import java.time.Instant;

/**
 * 만료가 있는 객체 조회 URL. private 버킷에서 이 URL 자체가 해당 객체에 대한 자격증명이므로
 * 로그나 오류 메시지에 그대로 남기지 않는다.
 */
public record PresignedView(URL url, Instant expiresAt) {
}
