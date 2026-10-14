package dev.sentinel.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShellCommandHeuristicTest {

  /** Whether the command is refused when every target is checked lexically from the root. */
  private static boolean refused(final String command) {
    final ShellInspection inspection = ShellCommandHeuristic.inspect(command);
    return !inspection.inspectable()
        || inspection.archiveCommand()
        || inspection.targets().stream()
            .anyMatch(
                target ->
                    ApprovedArtifactPolicy.evaluate(
                            Path.of(literalPrefix(target)), target.operation())
                        .isPresent());
  }

  private static String literalPrefix(final GuardTarget target) {
    if (!target.glob()) {
      return target.path();
    }
    final String path = target.path();
    final int glob = path.replaceAll("[*?\\[{].*", "").lastIndexOf('/');
    return glob < 0 ? "" : path.substring(0, glob);
  }

  private static List<GuardTarget> targets(final String command) {
    return ShellCommandHeuristic.inspect(command).targets();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "openspec archive foo --yes",
        "openspec validate foo && npx openspec archive foo -y",
        "FOO=1 openspec archive x",
        "npx -y @fission-ai/openspec@latest archive foo",
        "./node_modules/.bin/openspec archive foo",
        "pnpm exec openspec --store team archive foo",
        "sudo -u me env A=b openspec archive foo",
        "echo start; openspec archive foo",
        "bash -lc 'openspec archive foo'",
        "eval openspec archive foo",
        "rm -rf openspec/specs/x",
        "rm -rf openspec",
        "rm -rf openspec/*",
        "sed -i '' 's/a/b/' openspec/specs/x/spec.md",
        "perl -pi -e 's/a/b/' openspec/specs/x/spec.md",
        "cat draft.md > openspec/specs/x/spec.md",
        "echo text >> openspec/changes/archive/2026-10-05-old/tasks.md",
        "cat draft.md 2> /dev/null &> openspec/specs/x/spec.md",
        "git mv openspec/changes/foo openspec/changes/archive/2026-10-13-foo",
        "git -C . rm openspec/specs/x/spec.md",
        "git checkout -- openspec/specs/x/spec.md",
        "mv openspec/changes/archive/2026-10-05-old openspec/changes/old",
        "mv -t openspec/specs/x draft.md",
        "cp draft.md openspec/specs/x/spec.md",
        "cp --target-directory=openspec/specs/x draft.md",
        "rsync -a --delete build/ openspec/",
        "tee openspec/specs/x/spec.md < draft.md",
        "touch openspec/specs/new/spec.md",
        "mkdir -p openspec/specs/new",
        "dd if=draft.md of=openspec/specs/x/spec.md",
        "find openspec/specs -delete",
        "find openspec -delete",
        "find openspec/specs -name '*.md' -exec rm {} +",
        "cd openspec/specs && rm x/spec.md",
        "cd openspec; cd specs; rm -r x",
        "python3 tools/rewrite.py openspec/specs/x/spec.md",
        "sed -n 's/a/b/w openspec/specs/x/spec.md' draft.md",
        "awk '{ print > \"openspec/specs/x/spec.md\" }' draft.md",
        "x=$(rm -rf openspec/specs)",
        "echo `rm -rf openspec/specs`",
        "rm \"openspec/specs/x",
        "truncate -s 0 openspec/changes/archive/a/tasks.md",
        "rm -rf openspec/changes/foo/../../specs",
        "rm -rf ."
      })
  void refusesCommandsThatModifyProtectedArtifacts(final String command) {
    assertThat(refused(command)).as(command).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "grep -rn \"Requirement\" openspec/specs | head -20",
        "cat openspec/specs/x/spec.md",
        "ls -la openspec/changes/archive",
        "openspec validate --all --strict",
        "openspec show agent-integration --type spec",
        "openspec list --specs --json",
        "openspec archive --help",
        "sentinel spec validate openspec/specs/x/spec.md",
        "sentinel check --format json",
        "./mvnw -q test",
        "git status && git diff openspec/specs",
        "git add openspec/changes/foo",
        "git log -- openspec/changes/archive",
        "sed -n '1,40p' openspec/specs/x/spec.md",
        "awk '/Requirement/ { print }' openspec/specs/x/spec.md",
        "find openspec/specs -name '*.md'",
        "cp openspec/specs/x/spec.md openspec/changes/foo/specs/x/spec.md",
        "rm -rf openspec/changes/foo",
        "mkdir -p openspec/changes/foo/specs/x",
        "openspec new change foo",
        "cat > openspec/changes/foo/proposal.md <<'EOF'\nIt's a draft; rm -rf openspec/specs\nEOF\nls",
        "cat <<-EOF > notes.md\n\trm openspec/specs\n\tEOF",
        "echo 'rm -rf openspec/specs' # rm -rf openspec",
        "cmd 2>&1 | tee build.log",
        "diff <(cat openspec/specs/x/spec.md) draft.md",
        "rm -rf \"$TMPDIR/cache\"",
        "python tools/rewrite.py",
        "true",
        ""
      })
  void allowsReadOnlyValidationAndActiveChangeCommands(final String command) {
    assertThat(refused(command)).as(command).isFalse();
  }

  @Test
  void classifiesOperationsAndKeepsGlobPatterns() {
    assertThat(targets("mv a.md b.md openspec/changes/foo"))
        .containsExactly(
            GuardTarget.of(GuardOperation.MOVE_FROM, "a.md"),
            GuardTarget.of(GuardOperation.MOVE_FROM, "b.md"),
            GuardTarget.of(GuardOperation.MOVE_TO, "openspec/changes/foo"));
    assertThat(targets("rm -rf openspec/*"))
        .containsExactly(new GuardTarget(GuardOperation.DELETE, "openspec/*", true));
    assertThat(targets("rm -rf 'openspec/*'"))
        .containsExactly(GuardTarget.of(GuardOperation.DELETE, "openspec/*"));
    assertThat(targets("cd /tmp && rm x"))
        .containsExactly(GuardTarget.of(GuardOperation.DELETE, "/tmp/x"));
    assertThat(targets("cd $HOME && rm x"))
        .containsExactly(GuardTarget.of(GuardOperation.DELETE, "x"));
    assertThat(targets("rsync -a --remove-source-files a/ b/"))
        .containsExactly(
            GuardTarget.of(GuardOperation.DELETE, "a/"),
            GuardTarget.of(GuardOperation.WRITE, "b/"));
    assertThat(targets("find . -name '*.class' -delete"))
        .containsExactly(GuardTarget.of(GuardOperation.WRITE, "."));
    assertThat(targets("find -delete")).containsExactly(GuardTarget.of(GuardOperation.DELETE, "."));
    assertThat(targets("find . -fprint out.txt"))
        .containsExactly(
            GuardTarget.of(GuardOperation.WRITE, "."),
            GuardTarget.of(GuardOperation.WRITE, "out.txt"));
    assertThat(targets("git --no-pager -c a=b clean -fd build"))
        .containsExactly(GuardTarget.of(GuardOperation.DELETE, "build"));
    assertThat(targets("timeout 5 rm x"))
        .containsExactly(GuardTarget.of(GuardOperation.DELETE, "x"));
    assertThat(targets("sentinel integrate claude-code"))
        .containsExactly(
            GuardTarget.of(GuardOperation.WRITE, "integrate"),
            GuardTarget.of(GuardOperation.WRITE, "claude-code"));
    assertThat(targets("tool --out=report.md"))
        .containsExactly(GuardTarget.of(GuardOperation.WRITE, "report.md"));
    assertThat(targets("printf x > a\\ b.md"))
        .containsExactly(GuardTarget.of(GuardOperation.WRITE, "a b.md"));
    assertThat(targets("printf x \\\n > out.md"))
        .containsExactly(GuardTarget.of(GuardOperation.WRITE, "out.md"));
    assertThat(targets("cat a <> rw.md"))
        .containsExactly(GuardTarget.of(GuardOperation.WRITE, "rw.md"));
    assertThat(targets("cmd >| clobber.md >&2"))
        .containsExactly(GuardTarget.of(GuardOperation.WRITE, "clobber.md"));
    assertThat(targets("cmd >&log.md"))
        .containsExactly(GuardTarget.of(GuardOperation.WRITE, "log.md"));
    assertThat(targets("cat <<< text > out.md"))
        .containsExactly(GuardTarget.of(GuardOperation.WRITE, "out.md"));
    assertThat(targets("x# y > z"))
        .containsExactly(
            GuardTarget.of(GuardOperation.WRITE, "z"), GuardTarget.of(GuardOperation.WRITE, "y"));
    assertThat(targets("cat \"a\\\"b\" \"$X\" > \"o\\\nut\""))
        .containsExactly(GuardTarget.of(GuardOperation.WRITE, "out"));
    assertThat(targets("rm trailing \\"))
        .containsExactly(GuardTarget.of(GuardOperation.DELETE, "trailing"));
  }

  @Test
  void nestingIsBoundedAndDynamicDirectoriesAreForgotten() {
    assertThat(
            ShellCommandHeuristic.inspect("sh -c \"sh -c 'sh -c \\\"sh -c true\\\"'\"")
                .inspectable())
        .isFalse();
    assertThat(ShellCommandHeuristic.inspect("bash script.sh out.md").targets())
        .containsExactly(
            GuardTarget.of(GuardOperation.WRITE, "script.sh"),
            GuardTarget.of(GuardOperation.WRITE, "out.md"));
    assertThat(ShellCommandHeuristic.inspect("bash -c").targets()).isEmpty();
    assertThat(ShellCommandHeuristic.inspect("cd - ; cd ; rm x").targets())
        .containsExactly(GuardTarget.of(GuardOperation.DELETE, "x"));
    assertThat(ShellCommandHeuristic.inspect("git -C").targets()).isEmpty();
    assertThat(ShellCommandHeuristic.inspect("openspec").targets()).isEmpty();
    assertThat(ShellCommandHeuristic.inspect(null).inspectable()).isTrue();
  }
}
