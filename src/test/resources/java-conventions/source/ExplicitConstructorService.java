package fixture;

import org.springframework.stereotype.Service;

@Service
class ExplicitConstructorService {

	private final Dependency dependency;

	ExplicitConstructorService(Dependency dependency) {
		this.dependency = dependency;
	}

	private static final class Dependency {
	}
}
