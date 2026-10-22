package dev.ward.infrastructure.cli.update;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.logging.VerboseExecutionStrategy;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

class UpdateNoticePrinterTest {
  private static final String NOTICE = "ward: v0.4.0 is available (you have v0.3.1).";
  private static final String CHECK = "check";
  private static final String FAIL = "--fail";
  private static final int FAILED = 1;

  private final StringWriter out = new StringWriter();
  private final StringWriter err = new StringWriter();
  private final AtomicInteger lookups = new AtomicInteger();

  private int run(final boolean console, final Optional<String> notice, final String... args) {
    final UpdateNoticePrinter printer =
        new UpdateNoticePrinter(
            () -> {
              lookups.incrementAndGet();
              return notice;
            },
            () -> console);
    final CommandLine cli = new CommandLine(commands());
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    cli.setExecutionStrategy(
        new VerboseExecutionStrategy(printer.around(new CommandLine.RunLast())));
    return cli.execute(args);
  }

  // Built without annotations so picocli's processor generates no test-only native metadata.
  private CommandSpec commands() {
    final AtomicReference<CommandSpec> check = new AtomicReference<>();
    check.set(
        CommandSpec.wrapWithoutInspection(
            (Callable<Integer>)
                () -> {
                  out.write("report" + System.lineSeparator());
                  return Boolean.TRUE.equals(check.get().findOption(FAIL).getValue()) ? FAILED : 0;
                }));
    check.get().mixinStandardHelpOptions(true);
    check.get().addOption(OptionSpec.builder("--format").type(String.class).build());
    check.get().addOption(OptionSpec.builder(FAIL).type(boolean.class).build());
    final CommandSpec root =
        CommandSpec.wrapWithoutInspection((Callable<Integer>) () -> 0)
            .name("ward")
            .version("0.3.1")
            .mixinStandardHelpOptions(true);
    root.addSubcommand(CHECK, check.get());
    root.addSubcommand("hook", CommandSpec.wrapWithoutInspection((Callable<Integer>) () -> 0));
    return root;
  }

  @Test
  void interactiveCommandEndsWithTheNotice() {
    assertThat(run(true, Optional.of(NOTICE), CHECK)).isZero();

    assertThat(out).hasToString("report" + System.lineSeparator());
    assertThat(err.toString()).endsWith(NOTICE + System.lineSeparator());
  }

  @Test
  void failedCommandKeepsItsExitCode() {
    assertThat(run(true, Optional.of(NOTICE), CHECK, FAIL)).isEqualTo(FAILED);
    assertThat(err.toString()).contains(NOTICE);
  }

  @Test
  void offlineOrCurrentPrintsNothing() {
    assertThat(run(true, Optional.empty(), CHECK)).isZero();
    assertThat(err.toString()).isEmpty();
    assertThat(lookups).hasValue(1);
  }

  @Test
  void agentHookNeverLooksUp() {
    assertThat(run(true, Optional.of(NOTICE), "hook")).isZero();
    assertThat(err.toString()).isEmpty();
    assertThat(lookups).hasValue(0);
  }

  @Test
  void machineReadableOutputStaysPure() {
    run(true, Optional.of(NOTICE), CHECK, "--format", "json");
    run(true, Optional.of(NOTICE), CHECK, "--format=SARIF");

    assertThat(out.toString()).doesNotContain(NOTICE);
    assertThat(err.toString()).isEmpty();
    assertThat(lookups).hasValue(0);
  }

  @Test
  void helpVersionAndBareRootNeverLookUp() {
    run(true, Optional.of(NOTICE), CHECK, "--help");
    run(true, Optional.of(NOTICE), "--version");
    run(true, Optional.of(NOTICE));

    assertThat(lookups).hasValue(0);
  }

  @Test
  void noInteractiveConsoleNeverLooksUp() {
    run(false, Optional.of(NOTICE), CHECK);

    assertThat(err.toString()).isEmpty();
    assertThat(lookups).hasValue(0);
  }
}
