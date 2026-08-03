/**
 * Qello 백엔드의 최상위 패키지다.
 *
 * <p>코드는 기능별 패키지로 나누고, 각 기능은 필요할 때 다음 3계층을 둔다.
 *
 * <ul>
 *   <li>{@code controller}: HTTP 요청·응답과 입력 형식 검증</li>
 *   <li>{@code service}: 유스케이스, 비즈니스 규칙, 트랜잭션 경계</li>
 *   <li>{@code repository}: 해당 기능이 소유한 데이터의 조회·저장</li>
 * </ul>
 *
 * <p>{@code domain}과 controller 내부 DTO는 계층을 보조하는 모델이다. 다른 기능과
 * 협력할 때는 상대 기능의 service 계약을 사용하고, controller·repository·entity를
 * 직접 참조하지 않는다.
 */
package com.dnd.qello;
