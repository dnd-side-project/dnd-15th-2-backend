/*
 * Created at: 2026-09-02T17:42:43+09:00
 * Source scenario: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-013 through UNIT-017
 */
package com.dnd.qello.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangedJavaTypesTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("origin/main과 동일한 production Java는 ratchet 대상에서 빠진다")
	void excludesUnchangedProductionJava() throws Exception {
		Path repo = gitRepoWithOriginMain("src/main/java/com/dnd/qello/auth/service/DeviceTokenService.java",
				"class DeviceTokenService {}");

		assertThat(ChangedJavaTypes.productionJavaTypesFromOriginMain(repo)).isEmpty();
	}

	@Test
	@DisplayName("origin/main 대비 수정된 production Java는 ratchet 대상이다")
	void includesModifiedProductionJava() throws Exception {
		Path repo = gitRepoWithOriginMain("src/main/java/com/dnd/qello/auth/service/DeviceTokenService.java",
				"class DeviceTokenService {}");
		Files.writeString(repo.resolve("src/main/java/com/dnd/qello/auth/service/DeviceTokenService.java"),
				"class DeviceTokenService { int x; }");

		assertThat(ChangedJavaTypes.productionJavaTypesFromOriginMain(repo))
				.containsExactly("com.dnd.qello.auth.service.DeviceTokenService");
	}

	@Test
	@DisplayName("origin/main에 없는 새 production Java는 ratchet 대상이다")
	void includesNewProductionJava() throws Exception {
		Path repo = gitRepoWithOriginMain("src/main/java/com/dnd/qello/auth/service/DeviceTokenService.java",
				"class DeviceTokenService {}");
		Path added = repo.resolve("src/main/java/com/dnd/qello/feed/service/NewFeedService.java");
		Files.createDirectories(added.getParent());
		Files.writeString(added, "class NewFeedService {}");

		assertThat(ChangedJavaTypes.productionJavaTypesFromOriginMain(repo))
				.contains("com.dnd.qello.feed.service.NewFeedService");
	}

	@Test
	@DisplayName("같은 경로 목록이면 staged selector와 origin/main selector 결과가 같다")
	void stagedAndOriginMainAgreeForSamePaths() {
		List<String> paths = List.of("src/main/java/com/dnd/qello/feed/service/InboxApplicationService.java");

		assertThat(ChangedJavaTypes.productionJavaTypesFromManifest(paths))
				.isEqualTo(ChangedJavaTypes.toTypeNames(paths));
	}

	@Test
	@DisplayName("origin/main이 없으면 configuration failure로 실패한다")
	void missingOriginMainIsConfigurationFailure() throws Exception {
		Path repo = tempDir.resolve("no-origin-main");
		Files.createDirectories(repo);
		git(repo, "init", "--initial-branch=main");

		assertThatThrownBy(() -> ChangedJavaTypes.productionJavaTypesFromOriginMain(repo))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("origin/main")
				.hasMessageNotContaining("QELLO-JAVA-TX-001");
	}

	private Path gitRepoWithOriginMain(String relativePath, String content) throws Exception {
		Path repo = tempDir.resolve("repo");
		Files.createDirectories(repo);
		git(repo, "init", "--initial-branch=main");
		Path file = repo.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
		git(repo, "add", relativePath);
		git(repo, "commit", "-m", "seed");
		git(repo, "update-ref", "refs/remotes/origin/main", "HEAD");
		return repo;
	}

	private static void git(Path repositoryRoot, String... args) throws IOException, InterruptedException {
		List<String> command = new ArrayList<>();
		command.add("git");
		command.add("-c");
		command.add("user.email=convention-test@example.com");
		command.add("-c");
		command.add("user.name=convention-test");
		command.add("-c");
		command.add("commit.gpgsign=false");
		command.addAll(List.of(args));
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(repositoryRoot.toFile());
		builder.redirectErrorStream(true);
		Process process = builder.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + output);
		}
	}
}
