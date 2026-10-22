package dev.ward.domain.init;

import java.nio.file.Path;

/**
 * Ward-owned text between two marker lines in a project file.
 *
 * @param file the file that holds the section
 * @param begin the first line of the section
 * @param end the last line of the section
 * @param body the lines between the markers, each ending with a line break
 * @param wholeFile whether an existing file without the markers is user-owned and blocks the write;
 *     otherwise the section is appended to that file
 */
public record OwnedSection(Path file, String begin, String end, String body, boolean wholeFile) {
  /** Returns the section text, markers included. */
  public String text() {
    return begin + "\n" + body + end + "\n";
  }
}
