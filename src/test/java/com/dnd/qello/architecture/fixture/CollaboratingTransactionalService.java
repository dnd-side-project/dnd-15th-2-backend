/*
 * Created at: 2026-09-02T19:35:00+09:00
 * Source scenario: TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-INT-004
 */
package com.dnd.qello.architecture.fixture;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CollaboratingTransactionalService {

	private final QueryLikeTransactionalService query;

	public CollaboratingTransactionalService(QueryLikeTransactionalService query) {
		this.query = query;
	}

	public void list() {
		query.list();
	}
}
