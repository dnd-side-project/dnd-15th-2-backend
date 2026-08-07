package com.dnd.qello.auth.repository.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.domain.OperatorCredential;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.repository.OperatorCredentialRepository;

// 수정 경로는 관리 엔티티를 조회해 Dirty Checking에 맡긴다. JpaAccountRepository와 같은 방식이다.
//
// 이 테이블에는 @Version이 없다. 동시 수정은 사실상 로그인 실패 카운터 증가뿐이고,
// 경합으로 증가 하나를 잃는 것은 무해하다. 낙관적 잠금을 걸면 그 경합이 로그인 요청의
// 409 응답으로 바뀐다. 근거는 V5 migration 주석에 있다.
@Repository
@Transactional(readOnly = true)
public class JpaOperatorCredentialRepository implements OperatorCredentialRepository {

	private final SpringDataOperatorCredentialRepository repository;

	public JpaOperatorCredentialRepository(SpringDataOperatorCredentialRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public OperatorCredential save(OperatorCredential credential) {
		OperatorCredentialJpaEntity entity = OperatorCredentialJpaMapper.toNewEntity(credential);
		// userId를 직접 배정하므로 Spring Data는 이 엔티티를 기존 행으로 보고 merge로 흐른다.
		// SELECT가 한 번 더 나가지만 운영자 생성은 드문 경로라 그대로 둔다.
		//
		// flush를 명시하는 이유는 따로다. USER 계정에 자격증명을 붙이면 복합 FK가 거절하는데,
		// flush하지 않으면 그 위반이 transaction commit 시점까지 밀려 호출자가 원인을 알 수 없다.
		OperatorCredentialJpaEntity saved = repository.saveAndFlush(entity);
		return OperatorCredentialJpaMapper.toDomain(saved);
	}

	@Override
	@Transactional
	public OperatorCredential updateLoginState(OperatorCredential credential) {
		OperatorCredentialJpaEntity entity = findManaged(credential.getUserId());
		OperatorCredentialJpaMapper.updateLoginState(entity, credential);
		return OperatorCredentialJpaMapper.toDomain(entity);
	}

	@Override
	public Optional<OperatorCredential> findByLoginId(LoginId loginId) {
		return repository.findByLoginId(loginId.value())
			.map(OperatorCredentialJpaMapper::toDomain);
	}

	@Override
	public Optional<OperatorCredential> findByUserId(long userId) {
		return repository.findById(userId).map(OperatorCredentialJpaMapper::toDomain);
	}

	private OperatorCredentialJpaEntity findManaged(Long userId) {
		return repository.findById(userId)
			.orElseThrow(() -> new AuthException(
				AuthErrorCode.CREDENTIAL_NOT_FOUND, "userId", "대상 자격증명이 존재하지 않습니다"));
	}

}
