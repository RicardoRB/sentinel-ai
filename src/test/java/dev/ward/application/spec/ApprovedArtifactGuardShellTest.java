package dev.ward.application.spec;

import static dev.ward.application.spec.ApprovedArtifactGuardServiceTest.SPEC;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.spec.GuardDecision;
import dev.ward.domain.spec.GuardRequest;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Shell commands, patterns, skills, and unreadable input evaluated against a real project tree. */
class ApprovedArtifactGuardShellTest {

  @TempDir Path root;

  @BeforeEach
  void project() throws IOException {
    ApprovedArtifactGuardServiceTest.openSpecProject(root);
  }

  private static ApprovedArtifactGuardService service() {
    return ApprovedArtifactGuardServiceTest.service();
  }

  @Test
  void shellCommandsAreCheckedAgainstTheWorkingDirectory() {
    assertThat(service().evaluate(GuardRequest.shell(root, "openspec", "rm -rf specs/x")).allowed())
        .isFalse();
    assertThat(service().evaluate(GuardRequest.shell(root, "", "openspec archive foo")).reason())
        .contains("`openspec archive`");
    assertThat(service().evaluate(GuardRequest.shell(root, "", "rm 'a")).reason())
        .contains("could not inspect the shell command");
    assertThat(service().evaluate(GuardRequest.shell(root, "", "cat " + SPEC)))
        .isEqualTo(GuardDecision.ALLOWED);
  }

  @Test
  void shellPatternsAreMatchedAgainstProtectedDirectories() {
    final ApprovedArtifactGuardService service = service();

    assertThat(service.evaluate(GuardRequest.shell(root, "", "rm -rf openspec/*")).allowed())
        .isFalse();
    assertThat(service.evaluate(GuardRequest.shell(root, "", "rm -rf *")).allowed()).isFalse();
    assertThat(service.evaluate(GuardRequest.shell(root, "", "rm -rf o*/sp*")).allowed()).isFalse();
    assertThat(
            service.evaluate(GuardRequest.shell(root, "", "rm openspec/specs/*/spec.md")).allowed())
        .isFalse();
    assertThat(service.evaluate(GuardRequest.shell(root, "", "rm -rf **/archive")).allowed())
        .isFalse();
    assertThat(service.evaluate(GuardRequest.shell(root, "", "rm -f *.log")))
        .isEqualTo(GuardDecision.ALLOWED);
    assertThat(service.evaluate(GuardRequest.shell(root, "", "rm -rf openspec/changes/f*")))
        .isEqualTo(GuardDecision.ALLOWED);
    assertThat(service.evaluate(GuardRequest.shell(root, "", "touch openspec/{a,b}.md")))
        .isEqualTo(GuardDecision.ALLOWED);
  }

  @Test
  void archiveAndSyncSkillsAreRefused() {
    assertThat(service().evaluate(GuardRequest.skill(root, "opsx:archive")).reason())
        .contains("The skill opsx:archive");
    assertThat(service().evaluate(GuardRequest.skill(root, "opsx:propose")))
        .isEqualTo(GuardDecision.ALLOWED);
  }

  @Test
  void unreadableInputIsRefused() {
    assertThat(service().evaluate(GuardRequest.unreadable(root, "tool payload")).reason())
        .contains("could not inspect the tool payload");
  }
}
