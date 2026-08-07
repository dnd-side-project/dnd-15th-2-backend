package com.dnd.qello.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorSeedService;

// 설정이 켜져 있을 때만 초기 운영자를 만든다.
//
// Flyway 시드 migration을 쓰지 않는 이유는 비밀번호 해시가 저장소에 남기 때문이다.
// 모든 환경이 같은 비밀번호를 갖게 되고, 바꾸려면 적용된 migration을 고쳐야 한다.
@Component
@ConditionalOnProperty(prefix = "qello.auth.operator-seed", name = "enabled", havingValue = "true")
public class OperatorSeedRunner implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(OperatorSeedRunner.class);

	private final OperatorSeedService operatorSeedService;
	private final OperatorSeedProperties properties;

	public OperatorSeedRunner(
		OperatorSeedService operatorSeedService,
		OperatorSeedProperties properties
	) {
		this.operatorSeedService = operatorSeedService;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) {
		LoginId loginId = LoginId.of(properties.loginId());
		boolean created = operatorSeedService.seedIfAbsent(
			loginId,
			new RawPassword(properties.password()),
			properties.nickname(),
			properties.coarseRegionCode(),
			properties.locale(),
			properties.timezone()
		);

		// loginId까지만 남긴다. 비밀번호는 어떤 형태로도 로그에 넣지 않는다.
		if (created) {
			LOG.info("seeded operator credential for loginId={}", loginId.value());
		} else {
			LOG.info("operator credential already present for loginId={}", loginId.value());
		}
	}

}
