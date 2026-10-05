package dev.sentinel.domain.init;

import dev.sentinel.domain.project.Project;

import java.util.List;

/** Applies and rolls back build-tool declarations needed by selected setup options. */
public interface BuildToolConfiguration {
    PomChange apply(Project project, List<InitGateOption> gates);

    void rollback(PomChange change);
}
