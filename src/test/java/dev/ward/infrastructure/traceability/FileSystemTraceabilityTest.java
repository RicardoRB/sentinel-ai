package dev.ward.infrastructure.traceability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.traceability.ScenarioDefinition;
import dev.ward.domain.traceability.TraceabilityInventory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemTraceabilityTest {
  @Test
  void discoversLiveNestedScenariosButNotChanges(@TempDir final Path root) throws Exception {
    Files.createDirectories(root.resolve("openspec/specs/identity/user-auth"));
    Files.createDirectories(root.resolve("openspec/changes/foo/specs/identity"));
    Files.writeString(
        root.resolve("openspec/specs/identity/user-auth/spec.md"),
        "#### Scenario: Successful sign-in\n");
    Files.writeString(
        root.resolve("openspec/changes/foo/specs/identity/spec.md"), "#### Scenario: Delta only\n");
    final TraceabilityInventory<ScenarioDefinition> result =
        new FileSystemScenarioInventory().inspect(root);
    assertThat(result.entries()).hasSize(1);
    assertThat(result.entries().getFirst().identity().capabilityPath())
        .isEqualTo("identity/user-auth");
  }

  @Test
  void ignoresCommentsAndStrings(@TempDir final Path root) throws Exception {
    final Path source = root.resolve("src/test/java/Test.java");
    Files.createDirectories(source.getParent());
    Files.writeString(
        source,
        "// @Tag(\"spec:fake\")\nclass Test { String s = \"@DisplayName(\\\"fake\\\")\"; @Test void helper() {} }");
    assertThat(new JavaTestMappingInventory().inspect(root).entries()).isEmpty();
  }
}
