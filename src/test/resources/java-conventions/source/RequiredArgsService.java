package fixture;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class RequiredArgsService {

	private final Dependency dependency;

	private static final class Dependency {
	}
}
