package dev.sentinel.application.project;

import dev.sentinel.domain.config.SentinelException;

/** Reports that no supported project contains the given path. */
public class ProjectNotFoundException extends SentinelException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception with the standard message. */
  public ProjectNotFoundException() {
    super("No supported project detected.");
  }
}
