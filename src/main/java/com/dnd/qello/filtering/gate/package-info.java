/**
 * 필터링 production 활성화 게이트(#113). 법률·계약·보안 확인이 끝나기 전에는
 * 필터링을 production에서 켤 수 없게 막는다. 확인 항목의 실제 검토와 승인은
 * 사람의 몫이고, 이 패키지는 그 승인 없이 켜지지 않는다는 것만 보장한다
 * (INV-CMP-005, INV-CMP-006).
 */
package com.dnd.qello.filtering.gate;
