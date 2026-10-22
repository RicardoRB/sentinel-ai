package dev.ward.infrastructure.cli.agent;

import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;

/** The subset of a Claude Code hook event this adapter reads; other fields are ignored. */
@JsonType
public final class ClaudeHookEvent {
  private static final Logger LOGGER = Logger.getLogger(ClaudeHookEvent.class.getName());
  private static final int MAX_PAYLOAD_BYTES = 1_048_576;

  /** The guard reads whole payloads, which carry the full content of a {@code Write}. */
  private static final int MAX_GUARD_PAYLOAD_BYTES = 16 * MAX_PAYLOAD_BYTES;

  private static final String STOP = "Stop";
  private static final String PRE_TOOL_USE = "PreToolUse";

  @JsonProperty("hook_event_name")
  public String hookEventName;

  @JsonProperty("tool_input")
  public ToolInput toolInput;

  @JsonProperty("stop_hook_active")
  public boolean stopHookActive;

  @JsonProperty("tool_name")
  public String toolName;

  @JsonProperty("cwd")
  public String cwd;

  /**
   * Reads one event; an unreadable, empty, or invalid payload yields an empty {@code PostToolUse}
   * event without an edited path, so the hook keeps its previous behavior.
   */
  static ClaudeHookEvent read(final InputStream input, final JsonCodec codec) {
    final String text;
    try {
      text = new String(input.readNBytes(MAX_PAYLOAD_BYTES), StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOGGER.log(Level.INFO, "event=claude-hook-payload-ignored reason=unreadable");
      return new ClaudeHookEvent();
    }
    if (text.isBlank()) {
      return new ClaudeHookEvent();
    }
    try {
      final ClaudeHookEvent event = codec.fromJson(text, ClaudeHookEvent.class);
      return event == null ? new ClaudeHookEvent() : event;
    } catch (JsonCodecException e) {
      LOGGER.log(Level.INFO, "event=claude-hook-payload-ignored reason=invalid-json");
      return new ClaudeHookEvent();
    }
  }

  /**
   * Reads one event in full for the approved-artifact guard. Unlike {@link #read}, it returns empty
   * when the payload cannot be read, exceeds the guard limit, or is not a JSON object, because the
   * guard must not guess at a tool call it cannot see.
   */
  static Optional<ClaudeHookEvent> readComplete(final InputStream input, final JsonCodec codec) {
    try {
      final byte[] bytes = input.readNBytes(MAX_GUARD_PAYLOAD_BYTES + 1);
      if (bytes.length > MAX_GUARD_PAYLOAD_BYTES) {
        LOGGER.log(Level.INFO, "event=claude-hook-payload-rejected reason=oversized");
        return Optional.empty();
      }
      final String text = new String(bytes, StandardCharsets.UTF_8);
      return text.isBlank()
          ? Optional.empty()
          : Optional.ofNullable(codec.fromJson(text, ClaudeHookEvent.class));
    } catch (IOException | JsonCodecException e) {
      LOGGER.log(Level.INFO, "event=claude-hook-payload-rejected reason=unreadable");
      return Optional.empty();
    }
  }

  boolean isPreToolUse() {
    return PRE_TOOL_USE.equals(hookEventName);
  }

  boolean isStop() {
    return STOP.equals(hookEventName);
  }

  Optional<String> editedPath() {
    return Optional.ofNullable(toolInput)
        .map(input -> input.filePath)
        .filter(path -> !path.isBlank());
  }

  /** Returns a {@code tool_input} field, or empty when the payload has no tool input. */
  Optional<ToolInput> input() {
    return Optional.ofNullable(toolInput);
  }

  /** The tool input of a Claude Code hook event; its fields mirror the event JSON. */
  @JsonType
  public static final class ToolInput {
    private static final String NOTEBOOK_EDIT = "NotebookEdit";
    private static final String BASH = "Bash";

    @JsonProperty("file_path")
    public String filePath;

    @JsonProperty("notebook_path")
    public String notebookPath;

    @JsonProperty("command")
    public String command;

    @JsonProperty("skill")
    public String skill;

    /** The file a file-editing tool writes, or empty when the payload names none. */
    List<String> writtenPaths(final String tool) {
      final String path = NOTEBOOK_EDIT.equals(tool) ? notebookPath : filePath;
      return path == null || path.isBlank() ? List.of() : List.of(path);
    }

    /** The shell command for {@code Bash}, otherwise the skill name. */
    String text(final String tool) {
      return BASH.equals(tool) ? command : skill;
    }
  }
}
