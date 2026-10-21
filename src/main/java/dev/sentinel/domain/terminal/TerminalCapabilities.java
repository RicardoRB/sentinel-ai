package dev.sentinel.domain.terminal;

/** What the output terminal supports. */
public interface TerminalCapabilities {
  /** Returns whether output goes to an interactive terminal that can redraw lines. */
  boolean interactive();

  /** Returns whether colored output is enabled. */
  boolean colorEnabled();

  /** Returns whether a console terminal is attached, regardless of color or line-redraw support. */
  boolean consoleAttached();
}
