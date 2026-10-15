package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.application.agent.NativeHookProtocol;
import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.spec.ApprovedArtifactGuardService;
import dev.sentinel.application.spec.SpecValidationService;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.agent.NativeHookOutcome;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.json.JsonCodecException;
import dev.sentinel.domain.spec.SpecValidationReport;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

  private final CheckService checkService;
  private final SpecValidationService specService;
  private final ApprovedArtifactGuardService guardService;
  private final JsonCodec codec;
  private final Set<AgentIntegration> integrations;
  private final InputStream input;

  @Inject
  public NativeHookCommand(
      final CheckService checkService,
      final SpecValidationService specService,
      final ApprovedArtifactGuardService guardService,
      final JsonCodec codec,
      final Set<AgentIntegration> integrations) {
    this(checkService, specService, guardService, codec, integrations, System.in);
  }

  NativeHookCommand(
      final CheckService checkService,
      final SpecValidationService specService,
      final ApprovedArtifactGuardService guardService,
      final JsonCodec codec,
      final Set<AgentIntegration> integrations,
      final InputStream input) {
    this.checkService = checkService;
    this.specService = specService;
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
      final CheckReport report =
          checkService.checkExcluding(options.directory(), SupportedQualityGates.specIds());
      final SpecValidationReport specs = validateEditedPath(event);
      if (!report.passed() || specs.hasFindings()) {
        error().println(NativeHookMessages.failureText(report, specs));
        return ExitCodes.ERROR;
      }
      if (protocol.turnEnds(event)) {
        final SpecValidationReport full =
            specService.validateConfigured(options.directory(), List.of());
        if (full.hasFindings()) {
          output()
              .println(
                  protocol.serialize(
                      NativeHookOutcome.feedback(NativeHookMessages.feedback(full))));
        }
      }
      return ExitCodes.OK;
    } catch (SentinelException | JsonCodecException | UncheckedIOException exception) {
      error()
          .println(
              "Sentinel native hook could not complete validation: "
                  + NativeHookMessages.safeMessage(exception));
      return ExitCodes.ERROR;
    }
  }

  private int evaluateGuard(final NativeHookProtocol protocol, final NativeHookInput event) {
    try {
      NativeHookGuard.evaluate(guardService, event, options.directory())
          .ifPresent(deny -> output().println(protocol.serialize(deny)));
      return ExitCodes.OK;
    } catch (SentinelException | UncheckedIOException exception) {
      error().println("Sentinel native hook could not inspect the tool call.");
      return ExitCodes.ERROR;
    }
  }

  private SpecValidationReport validateEditedPath(final NativeHookInput event) {
    return event.path() == null
        ? SpecValidationReport.EMPTY
        : specService.validatePaths(options.directory(), List.of(), List.of(event.path()));
  }

  private NativeHookProtocol protocol() {
    return integrations.stream()
        .filter(candidate -> candidate.id().equals(agent))
        .filter(NativeHookProtocol.class::isInstance)
        .map(NativeHookProtocol.class::cast)
        .findFirst()
        .orElseThrow(() -> new SentinelException("Unsupported native hook agent: " + agent));
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
