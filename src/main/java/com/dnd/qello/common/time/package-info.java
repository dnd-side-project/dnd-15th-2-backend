/**
 * 기능이 공유하는 시각 원천.
 *
 * <p>현재 시각이 필요한 코드는 {@link java.time.Clock} 빈을 주입받는다.
 * {@code Instant.now()}를 직접 호출하면 테스트가 시각을 고정할 수 없다.
 */
package com.dnd.qello.common.time;
