package dev.ward.infrastructure.cli.agent;

import dev.ward.application.agent.NativeHookProtocol;
import dev.ward.application.spec.ApprovedArtifactGuardService;
import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.NativeHookInput;
import dev.ward.domain.agent.NativeHookOutcome;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.spec.SpecValidationReport;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.cli.ProjectOptions;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Agent-neutral native hook adapter with harness-specific response translation. */
@Command(name = "native", description = "Run a native coding-harness hook adapter.")
public final class NativeHookCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Option(names = "--agent", required = true)
  private String agent;

  @Option(names = "--event-stdin")
  private boolean eventStdin;

  @Option(names = "--guard-approved-specs")
  private boolean guard;

  private final HookChecks hookChecks;
  private final ApprovedArtifactGuardService guardService;
  private final JsonCodec codec;
  private final Set<AgentIntegration> integrations;
  private final InputStream input;

  /**
   * Creates the native hook command reading its event from standard input. {@code hookChecks} runs
   * the quality gates and spec validation, {@code guardService} decides approved-artifact guard
   * requests, and {@code integrations} supply each harness's hook protocol.
   */
  @Inject
  public NativeHookCommand(
      final HookChecks hookChecks,
      final ApprovedArtifactGuardService guardService,
      final JsonCodec codec,
      final Set<AgentIntegration> integrations) {
    this(hookChecks, guardService, codec, integrations, System.in);
  }

  NativeHookCommand(
      final HookChecks hookChecks,
      final ApprovedArtifactGuardService guardService,
      final JsonCodec codec,
      final Set<AgentIntegration> integrations,
      final InputStream input) {
    this.hookChecks = hookChecks;
    this.guardService = guardService;
    this.codec = codec;
    this.integrations = Set.copyOf(integrations);
    this.input = input;
  }

  @Override
  public Integer call() {
    try {
      final NativeHookProtocol protocol = protocol();
      final NativeHookInput event =
          eventStdin ? NativeHookEvents.read(input, protocol, codec) : NativeHookInput.empty();
      if (guard) {
        return evaluateGuard(protocol, event);
      }
      if (protocol.turnEnds(event)) {
        final CheckReport report =
            hookChecks
                .checks()
                .checkExcluding(options.directory(), SupportedQualityGates.specIds());
        return NativeHookTurnEnd.deliver(
            protocol,
            event,
            new TurnEndResults(
                report, hookChecks.specs().validateConfigured(options.directory(), List.of())),
            output(),
            error());
      }
      final CheckReport report =
          hookChecks
              .checks()
              .checkPartial(
                  options.directory(),
                  NativeHookEditedPaths.of(event, options.directory()),
                  SupportedQualityGates.specIds());
      final SpecValidationReport specs = validateEditedPath(event);
      if (report.passed() && !specs.hasFindings()) {
        return ExitCodes.OK;
      }
      final String findings = NativeHookMessages.failureText(report, specs);
      final Optional<String> feedback = protocol.postEditFeedback(findings);
      if (feedback.isPresent()) {
        output().println(feedback.get());
        return ExitCodes.OK;
      }
      error().println(findings);
      return ExitCodes.ERROR;
    } catch (WardException | JsonCodecException | UncheckedIOException exception) {
      error()
          .println(
              "Ward native hook could not complete validation: "
                  + NativeHookMessages.safeMessage(exception));
      return ExitCodes.ERROR;
    }
  }

  private int evaluateGuard(final NativeHookProtocol protocol, final NativeHookInput event) {
    try {
      final Optional<NativeHookOutcome> deny =
          NativeHookGuard.evaluate(guardService, event, options.directory());
      if (deny.isEmpty()) {
        return ExitCodes.OK;
      }
      if (protocol.deniesWithExitStatus()) {
        error().println(deny.get().message());
        return ExitCodes.ERROR;
      }
      output().println(protocol.serialize(deny.get()));
      return ExitCodes.OK;
    } catch (WardException | UncheckedIOException exception) {
      error().println("Ward native hook could not inspect the tool call.");
      return ExitCodes.ERROR;
    }
  }

  private SpecValidationReport validateEditedPath(final NativeHookInput event) {
    return event.path() == null
        ? SpecValidationReport.EMPTY
        : hookChecks.specs().validatePaths(options.directory(), List.of(), List.of(event.path()));
  }

  private NativeHookProtocol protocol() {
    return integrations.stream()
        .filter(candidate -> candidate.id().equals(agent))
        .filter(NativeHookProtocol.class::isInstance)
        .map(NativeHookProtocol.class::cast)
        .findFirst()
        .orElseThrow(() -> new WardException("Unsupported native hook agent: " + agent));
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }

  private PrintWriter error() {
    return spec == null
        ? new PrintWriter(System.err, true, StandardCharsets.UTF_8)
        : spec.commandLine().getErr();
  }
}
