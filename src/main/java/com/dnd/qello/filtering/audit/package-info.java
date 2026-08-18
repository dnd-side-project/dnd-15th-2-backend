/**
 * 운영자 행위 감사(#113). 필터링 authority를 사람이 바꾼 사실을 actor·reason·
 * policy version·시간과 함께 append-only로 남긴다. 도메인 이벤트 원장
 * (release_promotion_history 등)과 목적이 다르며 서로를 대체하지 않는다.
 */
package com.dnd.qello.filtering.audit;
