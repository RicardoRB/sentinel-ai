package dev.ward.application.provenance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.CheckService;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectInspection;
import dev.ward.domain.provenance.BootstrapDeclaration;
import dev.ward.domain.provenance.EnforcementFinding;
import dev.ward.domain.provenance.EnforcementReport;
import dev.ward.domain.provenance.GitChange;
import dev.ward.domain.provenance.GitRepository;
import dev.ward.domain.provenance.GitRevisionSnapshot;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpecEnforcementFixtureTest {
  private static final Path ROOT = Path.of("fixture");
  private static final String SPEC = "openspec/specs/feature/spec.md";
  private static final String ARCHIVE = "openspec/changes/archive/add-feature";
  private static final String OLD = "old";
  private static final List<GitChange> CHANGES =
      List.of(GitChange.modified(SPEC), GitChange.added(ARCHIVE + "/tasks.md"));
  private static final ProjectDetector DETECTOR =
      new ProjectDetector(
          new ProjectInspection() {
            @Override
            public Optional<Project> detect(final Path start) {
              return Optional.of(new Project(ROOT, Language.JAVA, BuildTool.MAVEN, Framework.NONE));
            }

            @Override
            public List<Project> discover(final Path start) {
              return List.of(new Project(ROOT, Language.JAVA, BuildTool.MAVEN, Framework.NONE));
            }
          });
  private static final CheckService PASSING_CHECKS =
      new CheckService(null, null, null, null, null) {
        @Override
        public CheckReport check(
            final Path start, final List<String> profiles, final Set<String> gates) {
          return new CheckReport(null, List.of());
        }
      };

  @Test
  void validArchiveBackedTransitionPasses() {
    final GitRevisionSnapshot baseline = new GitRevisionSnapshot(OLD, Map.of(SPEC, OLD));
    final GitRevisionSnapshot candidate = snapshot("new", "- [x] done");

    assertThat(enforce(baseline, candidate, snapshot("new", "- [x] done"), CHANGES).findings())
        .isEmpty();
  }

  @Test
  void unexplainedTransitionFails() {
    final EnforcementReportFixture report =
        enforce(
            new GitRevisionSnapshot(OLD, Map.of(SPEC, OLD)),
            snapshot("new", "- [x] done"),
            snapshot(OLD, "- [x] done"),
            CHANGES);

    assertThat(report.categories()).contains(EnforcementFinding.Category.PROVENANCE);
  }

  @Test
  void archiveRewriteFails() {
    final GitRevisionSnapshot baseline =
        new GitRevisionSnapshot("base", Map.of(SPEC, OLD, ARCHIVE + "/proposal.md", OLD));
    final GitRevisionSnapshot candidate =
        new GitRevisionSnapshot("head", Map.of(SPEC, OLD, ARCHIVE + "/proposal.md", "rewritten"));

    assertThat(
            enforce(
                    baseline,
                    candidate,
                    candidate,
                    List.of(GitChange.modified(ARCHIVE + "/proposal.md")))
                .categories())
        .contains(EnforcementFinding.Category.ARCHIVE_IMMUTABILITY);
  }

  @Test
  void incompleteArchiveTasksFail() {
    final GitRevisionSnapshot candidate = snapshot("new", "- [ ] unfinished");

    assertThat(
            enforce(
                    new GitRevisionSnapshot(OLD, Map.of(SPEC, OLD)),
                    candidate,
                    snapshot("new", "- [x] done"),
                    CHANGES)
                .categories())
        .contains(EnforcementFinding.Category.ARCHIVE_TASK);
  }

  @Test
  void insufficientHistoryReturnsPrerequisiteExitCode() {
    final SpecEnforcementService service =
        new SpecEnforcementService(
            DETECTOR,
            new FixtureGit(snapshot("base", "- [x] base"), true),
            (repository, baseline, archive) -> baseline,
            PASSING_CHECKS);

    assertThat(
            service
                .enforce(ROOT, "base", List.of(), new BootstrapDeclaration(""), false)
                .exitCode())
        .isEqualTo(2);
  }

  @Test
  void bootstrapRequiresExplicitSelection() {
    final EnforcementReportFixture report =
        enforce(
            new GitRevisionSnapshot("base", Map.of()),
            snapshot("new", "- [x] done"),
            snapshot("new", "- [x] done"),
            List.of());

    assertThat(report.categories()).contains(EnforcementFinding.Category.BOOTSTRAP);
  }

  private static EnforcementReportFixture enforce(
      final GitRevisionSnapshot baseline,
      final GitRevisionSnapshot candidate,
      final GitRevisionSnapshot replayed,
      final List<GitChange> changes) {
    final SpecEnforcementService service =
        new SpecEnforcementService(
            DETECTOR,
            new FixtureGit(baseline, candidate, changes),
            (root, ignored, archive) -> replayed,
            PASSING_CHECKS);
    final EnforcementReport report =
        service.enforce(ROOT, "base", List.of(), new BootstrapDeclaration(""), false);
    return new EnforcementReportFixture(report.findings(), report.exitCode());
  }

  private static GitRevisionSnapshot snapshot(final String spec, final String task) {
    return new GitRevisionSnapshot(spec, Map.of(SPEC, spec, ARCHIVE + "/tasks.md", task));
  }

  private record EnforcementReportFixture(List<EnforcementFinding> findings, int exitCode) {
    List<EnforcementFinding.Category> categories() {
      return findings.stream().map(EnforcementFinding::category).toList();
    }
  }

  private static final class FixtureGit implements GitRepository {
    private final GitRevisionSnapshot baseline;
    private final GitRevisionSnapshot candidate;
    private final List<GitChange> changes;
    private final boolean unavailable;

    FixtureGit(
        final GitRevisionSnapshot baseline,
        final GitRevisionSnapshot candidate,
        final List<GitChange> changes) {
      this.baseline = baseline;
      this.candidate = candidate;
      this.changes = changes;
      this.unavailable = false;
    }

    FixtureGit(final GitRevisionSnapshot ignored, final boolean unavailable) {
      this.baseline = ignored;
      this.candidate = ignored;
      this.changes = List.of();
      this.unavailable = unavailable;
    }

    @Override
    public String head(final Path repository) {
      return candidate.revision();
    }

    @Override
    public String resolveMergeBase(final Path repository, final String base, final String head) {
      if (unavailable) {
        throw new IllegalStateException("missing merge base");
      }
      return baseline.revision();
    }

    @Override
    public GitRevisionSnapshot snapshot(final Path repository, final String revision) {
      return revision.equals(baseline.revision()) ? baseline : candidate;
    }

    @Override
    public List<GitChange> changes(final Path repository, final String base, final String head) {
      return changes;
    }
  }
}
