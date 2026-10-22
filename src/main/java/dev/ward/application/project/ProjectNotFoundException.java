package dev.ward.application.project;

import dev.ward.domain.config.WardException;

/** Reports that no supported project contains the given path. */
public class ProjectNotFoundException extends WardException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception with the standard message. */
  public ProjectNotFoundException() {
    super("No supported project detected.");
  }
}
