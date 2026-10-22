package dev.ward.domain.init;

import java.nio.file.Path;

/**
 * The state of a file before an owned section was written, used to restore it.
 *
 * @param file the file
 * @param existed whether the file existed
 * @param previous its previous content, empty when it did not exist
 * @param changed whether the write changed the file
 */
public record OwnedSectionChange(Path file, boolean existed, String previous, boolean changed) {}
