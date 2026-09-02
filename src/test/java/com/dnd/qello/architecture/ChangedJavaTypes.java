/*
 * Created at: 2026-09-02T17:42:43+09:00
 * Source scenario: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-013 through UNIT-017
 */
package com.dnd.qello.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class ChangedJavaTypes {

	private ChangedJavaTypes() {
	}

	public static Set<String> productionJavaTypesFromOriginMain(Path repositoryRoot) {
		ensureOriginMain(repositoryRoot);
		Set<String> names = new TreeSet<>();
		names.addAll(gitLines(repositoryRoot, "diff", "--name-only", "origin/main", "--", "src/main/java"));
		names.addAll(gitLines(repositoryRoot, "diff", "--name-only", "--cached", "origin/main", "--",
				"src/main/java"));
		names.addAll(gitLines(repositoryRoot, "ls-files", "--others", "--exclude-standard", "--", "src/main/java"));
		return toTypeNames(names);
	}

	public static Set<String> productionJavaTypesFromManifest(Collection<String> relativePaths) {
		return toTypeNames(relativePaths);
	}

	public static Set<String> toTypeNames(Collection<String> relativePaths) {
		Set<String> types = new TreeSet<>();
		for (String path : relativePaths) {
			String normalized = path.replace('\\', '/');
			if (!normalized.startsWith("src/main/java/") || !normalized.endsWith(".java")) {
				continue;
			}
			types.add(normalized.substring("src/main/java/".length(), normalized.length() - ".java".length())
					.replace('/', '.'));
		}
		return types;
	}

	static void ensureOriginMain(Path repositoryRoot) {
		if (gitExit(repositoryRoot, "rev-parse", "--verify", "origin/main") != 0) {
			throw new IllegalStateException(
					"origin/main is missing; fetch origin main before running convention checks");
		}
	}

	private static List<String> gitLines(Path repositoryRoot, String... args) {
		CommandResult result = git(repositoryRoot, args);
		if (result.exitCode != 0) {
			throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + result.output);
		}
		if (result.output.isBlank()) {
			return List.of();
		}
		return List.of(result.output.split("\n"));
	}

	private static int gitExit(Path repositoryRoot, String... args) {
		return git(repositoryRoot, args).exitCode;
	}

	private static CommandResult git(Path repositoryRoot, String... args) {
		List<String> command = new ArrayList<>();
		command.add("git");
		command.add("-c");
		command.add("safe.directory=*");
		command.addAll(List.of(args));
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(repositoryRoot.toFile());
		builder.redirectErrorStream(true);
		try {
			Process process = builder.start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			int exitCode = process.waitFor();
			return new CommandResult(exitCode, output);
		} catch (IOException exception) {
			throw new IllegalStateException("git execution failed", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("git execution failed", exception);
		}
	}

	private record CommandResult(int exitCode, String output) {
	}
}
