package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.init.OwnedSectionChange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnedSectionWriterTest {
  private static final String BEGIN = "<!-- begin -->";
  private static final String END = "<!-- end -->";
  private final OwnedSectionWriter writer = new OwnedSectionWriter();
  @TempDir Path root;

  private OwnedSection section(final String body, final boolean wholeFile) {
    return new OwnedSection(root.resolve("dir/FILE.md"), BEGIN, END, body, wholeFile);
  }

  @Test
  void createsMissingFileAndParentDirectories() throws IOException {
    final OwnedSection section = section("rules\n", false);

    assertThat(writer.plan(section).action()).isEqualTo(PlannedFileChange.Action.CREATE);
    writer.apply(section);

    assertThat(Files.readString(section.file())).isEqualTo(BEGIN + "\nrules\n" + END + "\n");
  }

  @Test
  void replacesOnlyTheSectionAndKeepsUserContentAroundIt() throws IOException {
    final OwnedSection section = section("new\n", false);
    Files.createDirectories(section.file().getParent());
    Files.writeString(section.file(), "top\n" + BEGIN + "\nold\n" + END + "\nbottom\n");

    assertThat(writer.plan(section).action()).isEqualTo(PlannedFileChange.Action.MODIFY);
    writer.apply(section);

    assertThat(Files.readString(section.file()))
        .isEqualTo("top\n" + BEGIN + "\nnew\n" + END + "\nbottom\n");
    assertThat(writer.plan(section).action()).isEqualTo(PlannedFileChange.Action.UNCHANGED);
  }

  @Test
  void appendsToUserFileWithoutTrailingNewline() {
    assertThat(OwnedSectionWriter.update(section("x\n", false), "user"))
        .contains("user\n\n" + BEGIN + "\nx\n" + END + "\n");
  }

  @Test
  void wholeFileSectionDoesNotTakeOverUserFile() throws IOException {
    final OwnedSection section = section("x\n", true);
    Files.createDirectories(section.file().getParent());
    Files.writeString(section.file(), "mine\n");

    assertThat(writer.plan(section).action()).isEqualTo(PlannedFileChange.Action.CONFLICT);
    assertThatThrownBy(() -> writer.apply(section))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("is user-owned");
    assertThat(Files.readString(section.file())).isEqualTo("mine\n");
  }

  @Test
  void malformedMarkersAreConflicts() {
    assertThat(OwnedSectionWriter.update(section("x\n", false), END + "\n" + BEGIN + "\n"))
        .isEmpty();
    assertThat(OwnedSectionWriter.update(section("x\n", false), BEGIN + "\n")).isEmpty();
    assertThat(
            OwnedSectionWriter.update(
                section("x\n", false), BEGIN + "\n" + END + "\n" + BEGIN + "\n" + END + "\n"))
        .isEmpty();
  }

  @Test
  void rollbackRestoresAnEditedFileAndDeletesACreatedOne() throws IOException {
    final OwnedSection created = section("x\n", false);
    writer.rollback(writer.apply(created));
    assertThat(created.file()).doesNotExist();

    Files.writeString(created.file(), "user\n");
    writer.rollback(writer.apply(created));
    assertThat(Files.readString(created.file())).isEqualTo("user\n");
  }

  @Test
  void unchangedWriteIsNotRolledBack() throws IOException {
    final OwnedSection section = section("x\n", false);
    writer.apply(section);

    final OwnedSectionChange change = writer.apply(section);
    Files.writeString(section.file(), "edited later\n");
    writer.rollback(change);
    writer.rollback(null);

    assertThat(change.changed()).isFalse();
    assertThat(Files.readString(section.file())).isEqualTo("edited later\n");
  }
}
