package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.init.QualityHookChange;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualityHookInstallerTest {
  @Test
  void createsBothExecutableHooksAndRunsProfiles(@TempDir final Path root) throws Exception {
    Files.createDirectories(root.resolve(".git/hooks"));

    final QualityHookInstaller installer = new QualityHookInstaller();
    final IntegrationResult result = installer.apply(root).result();

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(Files.readString(root.resolve(".git/hooks/pre-commit")))
        .contains(QualityHookInstaller.START_MARKER, "sentinel check --profile pre-commit");
    assertThat(Files.readString(root.resolve(".git/hooks/pre-push")))
        .contains(QualityHookInstaller.START_MARKER, "sentinel check --profile pre-push");
    assertThat(root.resolve(".git/hooks/pre-commit").toFile().canExecute()).isTrue();
    assertThat(root.resolve(".git/hooks/pre-push").toFile().canExecute()).isTrue();
  }

  @Test
  void insertsBeforeAndPreservesExistingHookContent(@TempDir final Path root) throws Exception {
    Files.createDirectories(root.resolve(".git/hooks"));
    final Path prePush = root.resolve(".git/hooks/pre-push");
    Files.writeString(prePush, "#!/bin/sh\necho user-hook\n");
    prePush.toFile().setExecutable(true, false);

    new QualityHookInstaller().apply(root);
    final String content = Files.readString(prePush);

    assertThat(content.indexOf(QualityHookInstaller.START_MARKER))
        .isLessThan(content.indexOf("echo user-hook"));
    assertThat(content).contains("#!/bin/sh", "echo user-hook");
  }

  @Test
  void refreshesWithoutDuplicatingAndRejectsMalformedMarkers(@TempDir final Path root)
      throws Exception {
    Files.createDirectories(root.resolve(".git/hooks"));
    final QualityHookInstaller installer = new QualityHookInstaller();
    installer.apply(root);
    installer.apply(root);
    final String content = Files.readString(root.resolve(".git/hooks/pre-push"));
    assertThat(content.indexOf(QualityHookInstaller.START_MARKER))
        .isEqualTo(content.lastIndexOf(QualityHookInstaller.START_MARKER));

    Files.writeString(
        root.resolve(".git/hooks/pre-push"),
        "#!/bin/sh\n" + QualityHookInstaller.START_MARKER + "\n");
    assertThatThrownBy(() -> installer.apply(root)).hasMessageContaining("Malformed");
  }

  @Test
  void removesOnlyManagedSections(@TempDir final Path root) throws Exception {
    Files.createDirectories(root.resolve(".git/hooks"));
    final Path prePush = root.resolve(".git/hooks/pre-push");
    Files.writeString(prePush, "#!/bin/sh\necho user-hook\n");
    final QualityHookInstaller installer = new QualityHookInstaller();
    installer.apply(root);

    final IntegrationResult result = installer.remove(root);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(Files.readString(prePush)).isEqualTo("#!/bin/sh\necho user-hook\n");
    assertThat(root.resolve(".git/hooks/pre-commit")).doesNotExist();
  }

  @Test
  void preflightsBothHooksBeforeWritingAndRollsBack(@TempDir final Path root) throws Exception {
    Files.createDirectories(root.resolve(".git/hooks"));
    final Path preCommit = root.resolve(".git/hooks/pre-commit");
    final Path prePush = root.resolve(".git/hooks/pre-push");
    Files.writeString(preCommit, "#!/bin/sh\necho existing\n");
    Files.writeString(prePush, QualityHookInstaller.START_MARKER + "\n");

    final QualityHookInstaller installer = new QualityHookInstaller();
    assertThatThrownBy(() -> installer.apply(root)).hasMessageContaining("Malformed");
    assertThat(Files.readString(preCommit)).isEqualTo("#!/bin/sh\necho existing\n");

    Files.delete(prePush);
    final QualityHookChange change = installer.apply(root);
    installer.rollback(change);
    assertThat(Files.readString(preCommit)).isEqualTo("#!/bin/sh\necho existing\n");
    assertThat(prePush).doesNotExist();
  }

  @Test
  void preservesOtherSentinelSectionsWhenRemovingQualityHooks(@TempDir final Path root)
      throws Exception {
    Files.createDirectories(root.resolve(".git/hooks"));
    final Path preCommit = root.resolve(".git/hooks/pre-commit");
    Files.writeString(
        preCommit,
        "#!/bin/sh\n" + "# sentinel-owned: spec-enforce-pre-commit v1\n" + "echo openspec\n");

    final QualityHookInstaller installer = new QualityHookInstaller();
    installer.apply(root);
    installer.remove(root);

    assertThat(Files.readString(preCommit))
        .contains("sentinel-owned: spec-enforce-pre-commit v1", "echo openspec")
        .doesNotContain(QualityHookInstaller.START_MARKER);
  }
}
