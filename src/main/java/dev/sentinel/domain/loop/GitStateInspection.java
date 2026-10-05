package dev.sentinel.domain.loop;

import java.nio.file.Path;

/** Outbound contract for inspecting repository state. */
public interface GitStateInspection {
    GitState inspect(Path projectRoot);
}
