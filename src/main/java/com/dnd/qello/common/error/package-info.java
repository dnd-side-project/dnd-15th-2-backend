/**
 * 기능 패키지가 공유하는 오류 코드 계약과 예외 최상위 타입.
 *
 * <p>특정 기능의 오류 코드는 해당 기능 패키지의 {@code error} 하위 패키지에 배치.
 * 이 패키지는 기능 패키지를 참조하지 않으나, DB 제약 이름을 기능 오류 코드로 옮기는
 * {@code ConstraintExceptionMapper}만 예외적으로 기능 오류 코드를 참조.
 */
package com.dnd.qello.common.error;
