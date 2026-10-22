package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.init.OwnedSectionChange;
import dev.ward.domain.init.OwnedSectionGeneration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Writes Ward-owned marker sections, leaving the rest of each file as the user wrote it. */
public final class OwnedSectionWriter implements OwnedSectionGeneration {
  private static final Logger LOGGER = Logger.getLogger(OwnedSectionWriter.class.getName());
  private static final String DETAIL = "Ward section";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public OwnedSectionWriter() {}

  @Override
  public PlannedFileChange plan(final OwnedSection section) {
    final Path file = section.file();
    final String original = read(file);
    final Optional<String> updated = update(section, original);
    if (updated.isEmpty()) {
      return new PlannedFileChange(
          file, PlannedFileChange.Action.CONFLICT, List.of(conflict(section)));
    }
    if (!Files.exists(file)) {
      return new PlannedFileChange(file, PlannedFileChange.Action.CREATE, List.of(DETAIL));
    }
    return updated.get().equals(original)
        ? new PlannedFileChange(file, PlannedFileChange.Action.UNCHANGED)
        : new PlannedFileChange(file, PlannedFileChange.Action.MODIFY, List.of(DETAIL));
  }

  @Override
  public OwnedSectionChange apply(final OwnedSection section) {
    final Path file = section.file();
    final boolean existed = Files.exists(file);
    final String original = read(file);
    final String updated =
        update(section, original).orElseThrow(() -> new WardException(conflict(section)));
    if (existed && updated.equals(original)) {
      return new OwnedSectionChange(file, true, original, false);
    }
    try {
      final Path parent = file.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(file, updated);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write " + file, e);
    }
    LOGGER.log(Level.INFO, () -> "event=init-owned-section status=written existed=" + existed);
    return new OwnedSectionChange(file, existed, original, true);
  }

  @Override
  public void rollback(final OwnedSectionChange change) {
    if (change == null || !change.changed()) {
      return;
    }
    try {
      if (change.existed()) {
        Files.writeString(change.file(), change.previous());
      } else {
        Files.deleteIfExists(change.file());
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not restore " + change.file(), e);
    }
  }

  /**
   * Replaces the section between its markers, or appends it when the file has none. Empty when the
   * markers are malformed or a whole-file section would overwrite user content.
   */
  static Optional<String> update(final OwnedSection section, final String original) {
    final int start = original.indexOf(section.begin());
    final int end = original.indexOf(section.end());
    if (start < 0 && end < 0) {
      return append(section, original);
    }
    if (start < 0
        || end < start
        || original.indexOf(section.begin(), start + 1) >= 0
        || original.indexOf(section.end(), end + 1) >= 0) {
      return Optional.empty();
    }
    final int afterEnd = end + section.end().length();
    final int after =
        afterEnd < original.length() && original.charAt(afterEnd) == '\n' ? afterEnd + 1 : afterEnd;
    return Optional.of(original.substring(0, start) + section.text() + original.substring(after));
  }

  private static Optional<String> append(final OwnedSection section, final String original) {
    if (original.isEmpty()) {
      return Optional.of(section.text());
    }
    if (section.wholeFile()) {
      return Optional.empty();
    }
    return Optional.of(original + (original.endsWith("\n") ? "\n" : "\n\n") + section.text());
  }

  private static String conflict(final OwnedSection section) {
    return section.file()
        + (section.wholeFile()
            ? " exists and is user-owned"
            : " has malformed or duplicate Ward section markers");
  }

  private static String read(final Path file) {
    try {
      return Files.exists(file) ? Files.readString(file) : "";
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + file, e);
    }
  }
}
