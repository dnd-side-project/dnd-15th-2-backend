package com.dnd.qello.feed.view;

import java.net.URL;
import java.time.Instant;

/**
 * 첨부 이미지 1건과 만료가 있는 조회 URL. private 버킷에서 이 URL 자체가 해당 객체에 대한 자격증명이므로 로그나 오류
 * 메시지에 남기지 않는다.
 */
public record MediaView(long mediaId, URL url, Instant expiresAt) {
}
