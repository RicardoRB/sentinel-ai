package dev.ward.infrastructure.cli.agent;

import dev.ward.application.agent.NativeHookProtocol;
import dev.ward.domain.agent.NativeHookInput;
import dev.ward.domain.agent.NativeHookOutcome;
import dev.ward.infrastructure.cli.ExitCodes;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Delivers turn-end findings once through the harness's continuation envelope. When the harness is
 * already continuing because of an earlier hook decision, the findings are only reported on stderr,
 * so an unfixable finding cannot keep the turn running.
 */
final class NativeHookTurnEnd {
  private static final Logger LOGGER = Logger.getLogger(NativeHookTurnEnd.class.getName());

  private NativeHookTurnEnd() {}

  static int deliver(
      final NativeHookProtocol protocol,
      final NativeHookInput event,
      final Optional<String> results,
      final PrintWriter output,
      final PrintWriter error) {
    if (results.isEmpty()) {
      return ExitCodes.OK;
    }
    final String findings = results.get();
    if (event.continued()) {
      LOGGER.log(Level.INFO, "event=native-turn-end-reported reason=already-continued");
      error.println(findings);
    } else {
      LOGGER.log(Level.INFO, "event=native-turn-end-continued");
      output.println(protocol.serialize(NativeHookOutcome.feedback(findings)));
    }
    return ExitCodes.OK;
  }
}
