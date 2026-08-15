package com.dnd.qello.filtering.domain;

// synthetic snapshot health probe의 종류(#109). TARGET은 건강 여부를 판정하려는
// snapshot 자체를, CONTROL은 기준(예: 현재 PROMOTED snapshot)을 호출한다.
public enum ProbeType {
	TARGET,
	CONTROL
}
