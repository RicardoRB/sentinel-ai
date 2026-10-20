package dev.sentinel.domain.loop;

import java.nio.file.Path;

/** Outbound contract for inspecting repository state. */
@FunctionalInterface
public interface GitStateInspection {
  /** Reads the Git state of {@code projectRoot}, such as whether the working tree is dirty. */
  GitState inspect(Path projectRoot);
}
