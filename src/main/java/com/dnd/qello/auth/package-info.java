/**
 * 백오피스 운영자와 앱 사용자의 인증을 담당한다.
 *
 * <p>두 사용자군은 인증 수단과 세션 수명이 달라 하나의 체계로 묶지 않는다.
 * 결정 배경은 {@code docs/adr/0006-split-operator-and-device-authentication.md}에 있다.
 *
 * <p>{@code account}는 계정의 신원과 상태를 소유하고 이 패키지는 자격증명을 소유한다.
 */
package com.dnd.qello.auth;
