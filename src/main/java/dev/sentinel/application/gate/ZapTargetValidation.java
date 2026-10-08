package dev.sentinel.application.gate;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.SupportedQualityGates;
import java.util.List;

/** Requires an explicit, user-supplied DAST target; Sentinel never infers or starts one. */
public final class ZapTargetValidation {
  public static final String PLACEHOLDER = SupportedQualityGates.ZAP_TARGET_PLACEHOLDER;

  private ZapTargetValidation() {}

  public static void require(String gate, List<String> command) {
    final int flag = command.indexOf("-t");
    if (flag < 0 || flag + 1 >= command.size() || PLACEHOLDER.equals(command.get(flag + 1))) {
      throw new SentinelException(
          "Quality gate '"
              + gate
              + "' requires an explicit target: add '-t <url>' to its command in sentinel.toml.");
    }
  }
}
