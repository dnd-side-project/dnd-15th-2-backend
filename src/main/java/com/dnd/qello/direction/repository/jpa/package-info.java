/**
 * direction feature의 JPA 영속화. ADR 0002에 따라 단순 aggregate CRUD만 여기 둔다.
 * PostGIS 조회, 조건부 갱신, snapshot bulk insert는 jdbc 패키지가 담당한다.
 */
package com.dnd.qello.direction.repository.jpa;
