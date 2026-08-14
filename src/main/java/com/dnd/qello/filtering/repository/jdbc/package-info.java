/**
 * filtering feature의 lock·조건부 갱신 연산. ADR 0002에 따라 {@code SELECT ... FOR
 * UPDATE}처럼 성공 행 수와 잠금이 계약인 연산만 여기 둔다.
 */
package com.dnd.qello.filtering.repository.jdbc;
