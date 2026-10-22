package dev.ward.application.gate;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import java.util.List;

/** Requires an explicit, user-supplied DAST target; Ward never infers or starts one. */
public final class ZapTargetValidation {
  public static final String PLACEHOLDER = SupportedQualityGates.ZAP_TARGET_PLACEHOLDER;

  private ZapTargetValidation() {}

  /**
   * Checks that the ZAP command of {@code gate} names a real {@code -t} target.
   *
   * @throws WardException if the target is missing or still the placeholder
   */
  public static void require(final String gate, final List<String> command) {
    final int flag = command.indexOf("-t");
    if (flag < 0 || flag + 1 >= command.size() || PLACEHOLDER.equals(command.get(flag + 1))) {
      throw new WardException(
          "Quality gate '"
              + gate
              + "' requires an explicit target: add '-t <url>' to its command in ward.toml.");
    }
  }
}
