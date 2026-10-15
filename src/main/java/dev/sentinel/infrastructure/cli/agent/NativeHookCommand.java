package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.spec.ApprovedArtifactGuardService;
import dev.sentinel.application.spec.SpecValidationService;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.spec.GuardDecision;
import dev.sentinel.domain.spec.GuardRequest;
import dev.sentinel.domain.spec.SpecValidationReport;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Agent-neutral native hook adapter with harness-specific response translation. */
@Command(name = "native", description = "Run a native coding-harness hook adapter.")
public final class NativeHookCommand implements Callable<Integer> {
  private static final int MAX_PAYLOAD_BYTES = 1_048_576;
  private static final int MAX_FEEDBACK_CHARS = 8_192;

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
  private final InputStream input;

  @Inject
  public NativeHookCommand(
      final CheckService checkService,
      final SpecValidationService specService,
      final ApprovedArtifactGuardService guardService,
      final JsonCodec codec) {
    this(checkService, specService, guardService, codec, System.in);
  }

  NativeHookCommand(
      final CheckService checkService,
      final SpecValidationService specService,
      final ApprovedArtifactGuardService guardService,
      final JsonCodec codec,
      final InputStream input) {
    this.checkService = checkService;
    this.specService = specService;
    this.guardService = guardService;
    this.codec = codec;
    this.input = input;
  }

  @Override
  public Integer call() {
    try {
      final NativeHookEvent event = eventStdin ? readEvent() : new NativeHookEvent();
      if (guard) {
        return evaluateGuard(event);
      }
      final CheckReport report =
          checkService.checkExcluding(options.directory(), SupportedQualityGates.specIds());
      final SpecValidationReport specs = validateEditedPath(event);
      if (!report.passed() || specs.hasFindings()) {
        error().println(failureText(report, specs));
        return ExitCodes.ERROR;
      }
      if (isTurnEnd(event)) {
        final SpecValidationReport full =
            specService.validateConfigured(options.directory(), List.of());
        if (full.hasFindings()) {
          output().println(feedback(full));
        }
      }
      return ExitCodes.OK;
    } catch (RuntimeException exception) {
      error()
          .println("Sentinel native hook could not complete validation: " + safeMessage(exception));
      return ExitCodes.ERROR;
    }
  }

  private int evaluateGuard(final NativeHookEvent event) {
    try {
      final Optional<GuardRequest> request = guardRequest(event);
      if (request.isEmpty()) {
        return ExitCodes.OK;
      }
      final GuardDecision decision = guardService.evaluate(request.get());
      if (!decision.allowed()) {
        output().println(denyResponse(agent, bounded(decision.reason())));
      }
      return ExitCodes.OK;
    } catch (RuntimeException exception) {
      error().println("Sentinel native hook could not inspect the tool call.");
      return ExitCodes.ERROR;
    }
  }

  private Optional<GuardRequest> guardRequest(final NativeHookEvent event) {
    final String tool = event.toolName();
    final Path root = options.directory();
    final String workingDirectory = event.cwd == null ? "" : event.cwd;
    if (event.unreadable) {
      return Optional.of(GuardRequest.unreadable(root, "tool payload"));
    }
    if (event.path() != null) {
      return Optional.of(GuardRequest.paths(root, workingDirectory, List.of(event.path())));
    }
    if (event.patch() != null) {
      return Optional.of(GuardRequest.patch(root, workingDirectory, event.patch()));
    }
    if (event.command != null && !event.command.isBlank()) {
      return Optional.of(GuardRequest.shell(root, workingDirectory, event.command));
    }
    if (event.skill != null && !event.skill.isBlank()) {
      return Optional.of(GuardRequest.skill(root, event.skill));
    }
    return tool == null ? Optional.empty() : Optional.of(GuardRequest.unreadable(root, tool));
  }

  private SpecValidationReport validateEditedPath(final NativeHookEvent event) {
    return event.path() == null
        ? SpecValidationReport.EMPTY
        : specService.validatePaths(options.directory(), List.of(), List.of(event.path()));
  }

  private NativeHookEvent readEvent() {
    try {
      final byte[] bytes = input.readNBytes(MAX_PAYLOAD_BYTES + 1);
      if (bytes.length > MAX_PAYLOAD_BYTES) {
        final NativeHookEvent event = new NativeHookEvent();
        event.unreadable = true;
        return event;
      }
      final String payload = new String(bytes, StandardCharsets.UTF_8);
      if (payload.isBlank()) {
        final NativeHookEvent event = new NativeHookEvent();
        event.unreadable = true;
        return event;
      }
      return codec.fromJson(payload, NativeHookEvent.class);
    } catch (IOException | RuntimeException exception) {
      final NativeHookEvent event = new NativeHookEvent();
      event.unreadable = true;
      return event;
    }
  }

  private boolean isTurnEnd(final NativeHookEvent event) {
    return Set.of("stop", "Stop", "SessionEnd", "sessionEnd", "AfterAgent", "agent.end", "turn_end")
        .contains(event.eventName());
  }

  private static String failureText(final CheckReport report, final SpecValidationReport specs) {
    final String quality =
        report.passed()
            ? ""
            : report.results().stream()
                .map(Object::toString)
                .limit(20)
                .reduce("Sentinel quality findings:", (a, b) -> a + "\n" + b);
    final String spec = specs.hasFindings() ? feedback(specs) : "";
    return bounded(
        List.of(quality, spec).stream()
            .filter(text -> !text.isBlank())
            .reduce((a, b) -> a + "\n" + b)
            .orElse("Sentinel validation failed."));
  }

  private static String feedback(final SpecValidationReport report) {
    return bounded(
        report.items().stream()
            .flatMap(item -> item.findings().stream().map(finding -> item.title() + ": " + finding))
            .limit(20)
            .reduce("Sentinel spec validation findings:", (a, b) -> a + "\n" + b));
  }

  private static String safeMessage(final RuntimeException exception) {
    final String message = exception.getMessage();
    return message == null || message.isBlank()
        ? exception.getClass().getSimpleName()
        : bounded(message);
  }

  private static String bounded(final String value) {
    return value.length() <= MAX_FEEDBACK_CHARS
        ? value
        : value.substring(0, MAX_FEEDBACK_CHARS) + "…";
  }

  static String denyResponse(final String agent, final String reason) {
    return NativeHookResponse.deny(agent, reason);
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

  @JsonType
  static final class NativeHookEvent {
    @JsonProperty("event")
    String event;

    @JsonProperty("event_name")
    String eventName;

    @JsonProperty("hook_event_name")
    String hookEventName;

    @JsonProperty("type")
    String type;

    @JsonProperty("tool_name")
    String toolName;

    @JsonProperty("cwd")
    String cwd;

    @JsonProperty("file_path")
    String filePath;

    @JsonProperty("path")
    String path;

    @JsonProperty("command")
    String command;

    @JsonProperty("patch")
    String patch;

    @JsonProperty("patchText")
    String patchText;

    @JsonProperty("skill")
    String skill;

    @JsonProperty("name")
    String name;

    boolean unreadable;

    String eventName() {
      return first(event, eventName, hookEventName, type);
    }

    String toolName() {
      return first(toolName, name);
    }

    String path() {
      return first(filePath, path);
    }

    String patch() {
      return first(patch, patchText);
    }

    private static String first(final String... values) {
      for (final String value : values) {
        if (value != null && !value.isBlank()) return value;
      }
      return null;
    }
  }
}
