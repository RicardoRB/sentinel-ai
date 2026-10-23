package dev.ward.application.gate;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.OpenApiGateOptions;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.gate.ProjectFiles;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.RevisionFiles;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory Git, working tree, and environment for OpenAPI gate tests. */
final class OpenApiGateFixtures {
  static final String SPEC = "src/main/resources/openapi.yml";
  static final String HEAD_SHA = "a".repeat(40);
  static final String BASE_SHA = "b".repeat(40);
  static final String PARENT_SHA = "c".repeat(40);
  static final Project PROJECT =
      new Project(Path.of("/project"), Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);
  static final List<String> LINT_COMMAND = List.of("vacuum", "lint", "-z", "-n", "warn");
  static final List<String> DIFF_COMMAND = List.of("oasdiff", "breaking", "--fail-on", "ERR");

  final Map<String, String> revisions = new HashMap<>();
  final Set<String> committedFiles = new HashSet<>();
  final Map<String, String> workingTree = new HashMap<>();
  final Map<String, String> environment = new HashMap<>();
  Optional<String> mergeBase = Optional.empty();

  /** A working tree containing the spec, committed at {@code HEAD}. */
  static OpenApiGateFixtures committedSpec() {
    final OpenApiGateFixtures fixtures = new OpenApiGateFixtures();
    fixtures.workingTree.put(SPEC, "openapi: 3.0.3\n");
    fixtures.revisions.put("HEAD", HEAD_SHA);
    fixtures.committedFiles.add(HEAD_SHA + ":" + SPEC);
    return fixtures;
  }

  QualityGate gate(final String id, final List<String> command, final CommandExecutor executor) {
    return gates(executor)
        .create(
            id,
            new GateConfiguration(
                true, command, Set.of("ci"), null, null, new OpenApiGateOptions(SPEC, null)),
            PROJECT);
  }

  QualityGate gate(final String id, final List<String> command) {
    return gate(id, command, new FakeCommandExecutor(0, "", ""));
  }

  OpenApiGates gates(final CommandExecutor executor) {
    return new OpenApiGates(
        executor,
        root -> new EnvironmentFacts(Set.of(), environment),
        new RevisionFiles() {
          @Override
          public Optional<String> resolve(final Path root, final String revision) {
            return Optional.ofNullable(revisions.get(revision));
          }

          @Override
          public Optional<String> mergeBase(
              final Path root, final String first, final String second) {
            return mergeBase;
          }

          @Override
          public boolean exists(final Path root, final String commit, final String path) {
            return committedFiles.contains(commit + ":" + path);
          }
        },
        new ProjectFiles() {
          @Override
          public boolean exists(final Path root, final String path) {
            return workingTree.containsKey(path);
          }

          @Override
          public boolean contains(final Path root, final String path, final String text) {
            return workingTree.getOrDefault(path, "").contains(text);
          }
        });
  }
}
