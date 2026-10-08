package dev.sentinel.infrastructure.terminal;

import dev.sentinel.domain.terminal.TerminalCapabilities;
import javax.inject.Inject;

public final class JdkTerminalCapabilities implements TerminalCapabilities {
  private final boolean consoleTerminal;
  private final String noColor;
  private final String term;

  @Inject
  public JdkTerminalCapabilities() {
    this(
        System.console() != null && System.console().isTerminal(),
        System.getenv("NO_COLOR"),
        System.getenv("TERM"));
  }

  JdkTerminalCapabilities(final boolean consoleTerminal, final String noColor, final String term) {
    this.consoleTerminal = consoleTerminal;
    this.noColor = noColor;
    this.term = term;
  }

  @Override
  public boolean interactive() {
    return consoleTerminal && noColor == null && !"dumb".equalsIgnoreCase(term);
  }

  @Override
  public boolean colorEnabled() {
    return consoleTerminal && noColor == null && !"dumb".equalsIgnoreCase(term);
  }
}
