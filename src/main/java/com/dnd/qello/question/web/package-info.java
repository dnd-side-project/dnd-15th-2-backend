/**
 * 질문 제안 제출·조회(사용자)와 검토(운영자) API. SecurityConfiguration의
 * appApiSecurityFilterChain(/api/**)과 backofficeSecurityFilterChain
 * (/admin/**, hasRole("OPERATOR"))이 각각 인가를 맡는다.
 */
package com.dnd.qello.question.web;
