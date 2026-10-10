package dev.sentinel.application.project;

import dev.sentinel.domain.config.SentinelException;

public class ProjectNotFoundException extends SentinelException {

  private static final long serialVersionUID = 1L;

  public ProjectNotFoundException() {
    super("No supported project detected.");
  }
}
