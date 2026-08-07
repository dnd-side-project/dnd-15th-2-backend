package com.dnd.qello.answer.service.port;

import java.net.URL;
import java.time.Instant;

public record PresignedUpload(URL url, Instant expiresAt) {
}
