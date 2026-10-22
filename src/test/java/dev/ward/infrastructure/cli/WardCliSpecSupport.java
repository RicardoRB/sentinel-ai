package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** CLI fixture with a stub OpenSpec executable that logs each invocation. */
class WardCliSpecSupport extends WardCliIntegrationSupport {
  protected static final String SPEC = "spec";
  protected static final String VALIDATE = "validate";
  protected static final String DIRECTORY = "-C";

  /** Mimics OpenSpec 1.14: success on stdout, findings only on stderr, and logs each call. */
  private static final String STUB =
      """
      #!/bin/sh
      echo "$*" >> "$(dirname "$0")/calls.log"
      case "$*" in
        *--archived*) echo "Change 'old' has incomplete tasks" >&2; exit 1 ;;
        *" bad "*)
          echo "Change 'bad' has issues" >&2
          echo "⚠ [WARNING] x/spec.md: ADDED \\"Thing\\" should contain SHALL or MUST" >&2
          echo "✗ [ERROR] x/spec.md: ADDED \\"Thing\\" must include at least one scenario" >&2
          echo "Next steps:" >&2
          echo "  - Each requirement MUST include at least one #### Scenario: block" >&2
          exit 1 ;;
        *) echo "Item is valid"; exit 0 ;;
      esac
      """;

  protected String active() throws IOException {
    return """
        version = 1
        [quality-gates.openspec]
        command = ["%s", "validate", "--all", "--strict", "--no-interactive"]
        """
        .formatted(stub());
  }

  protected void configure(final String toml) throws IOException {
    Files.writeString(project.resolve(WARD_TOML), toml);
    Files.createDirectories(project.resolve("openspec"));
  }

  protected Path stub() throws IOException {
    final Path executable = project.resolve("openspec-stub");
    if (!Files.exists(executable)) {
      Files.writeString(executable, STUB);
      assertThat(executable.toFile().setExecutable(true)).isTrue();
    }
    return executable;
  }

  protected String calls() throws IOException {
    return Files.readString(project.resolve("calls.log"));
  }
}
