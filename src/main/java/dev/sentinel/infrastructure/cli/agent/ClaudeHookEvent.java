package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.json.JsonCodecException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
  private static final String STOP = "Stop";

  @JsonProperty("hook_event_name")
  public String hookEventName;

  @JsonProperty("tool_input")
  public ToolInput toolInput;

  @JsonProperty("stop_hook_active")
  public boolean stopHookActive;

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

  boolean isStop() {
    return STOP.equals(hookEventName);
  }

  Optional<String> editedPath() {
    return Optional.ofNullable(toolInput)
        .map(input -> input.filePath)
        .filter(path -> !path.isBlank());
  }

  @JsonType
  public static final class ToolInput {
    @JsonProperty("file_path")
    public String filePath;
  }
}
