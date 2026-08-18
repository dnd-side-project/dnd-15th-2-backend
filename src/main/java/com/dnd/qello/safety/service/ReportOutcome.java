package com.dnd.qello.safety.service;

import com.dnd.qello.safety.domain.Report;

/** 신고 접수 결과. alreadyReceived는 새로 만들지 않고 기존 접수를 반환했는지를 나타낸다. */
public record ReportOutcome(Report report, boolean alreadyReceived) {
}
