package dev.ward.infrastructure.cli.agent;

import java.io.InputStream;
import java.util.function.BooleanSupplier;

/**
 * Where a hook reads its event and whether that input is an interactive terminal.
 *
 * @param stream the hook event input, normally standard input
 * @param interactive whether {@code stream} is a terminal, which carries no event payload
 */
record HookStdin(InputStream stream, BooleanSupplier interactive) {

  /** Standard input, interactive when a console terminal is attached. */
  static HookStdin system() {
    return new HookStdin(
        System.in, () -> System.console() != null && System.console().isTerminal());
  }
}
