/**
 * 필터링 운영 지표(#113). Micrometer의 MeterRegistry에만 의존하고 exporter는
 * 고르지 않는다 — 관측·경보 도구가 미결정이다. 모든 tag는 허용목록을 거쳐야
 * 하며, 원문과 직접 식별정보는 어떤 경로로도 지표에 실리지 않는다
 * (INV-CMP-001, INV-CMP-002).
 */
package com.dnd.qello.filtering.observability;
