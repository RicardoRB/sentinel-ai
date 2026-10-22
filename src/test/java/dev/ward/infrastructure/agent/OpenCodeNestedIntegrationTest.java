package dev.ward.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A nested project's OpenCode files live at the repository root and run Ward in the project. */
class OpenCodeNestedIntegrationTest {
  private static final String PLUGIN = ".opencode/plugins/ward-edit-write-apps-api.js";
  private static final String GUARD = ".opencode/plugins/ward-openspec-guard-apps-api.js";
  private static final String SESSION_CWD = "cwd: directory,";

  @TempDir Path repository;

  private final OpenCodeIntegration integration = new OpenCodeIntegration();

  @Test
  void nestedPluginAndCommandTargetTheProject() throws IOException {
    integration.integrate(repository, IntegrationRequest.INSTALL.forProject("apps/api"));

    final String plugin = Files.readString(repository.resolve(PLUGIN));
    assertThat(plugin)
        .contains(
            "const PROJECT = join(dirname(fileURLToPath(import.meta.url)), \"..\", \"..\", \"apps/api\");",
            "cwd: PROJECT,",
            ".map((path) => (isAbsolute(path) ? path : join(directory, path)))",
            "id: \"ward.edit-write.apps-api\"")
        .doesNotContain(SESSION_CWD);
    assertThat(Files.readString(repository.resolve(".opencode/commands/ward-check-apps-api.md")))
        .contains("`ward check -C apps/api --format json");
    assertThat(repository.resolve(".opencode/plugins/ward-edit-write.js")).doesNotExist();
  }

  @Test
  void nestedGuardRunsFromTheProject() throws IOException {
    integration.integrate(
        repository, new IntegrationRequest(false, GuardMode.ENABLE).forProject("apps/api"));

    assertThat(Files.readString(repository.resolve(GUARD)))
        .contains(
            "import { dirname, isAbsolute, join } from \"node:path\";",
            "cwd: PROJECT,",
            "isAbsolute(path) ? path : join(directory, path)",
            "id: \"ward.openspec-guard.apps-api\"")
        .doesNotContain(SESSION_CWD);
  }

  @Test
  void rootProjectFilesAreUnchanged() {
    final OpenCodeFiles root = new OpenCodeFiles("");

    assertThat(root.pluginFile()).isEqualTo(".opencode/plugins/ward-edit-write.js");
    assertThat(root.commandFile()).isEqualTo(".opencode/commands/ward-check.md");
    assertThat(root.plugin("{ cwd: directory, }")).isEqualTo("{ cwd: directory, }");
  }
}
