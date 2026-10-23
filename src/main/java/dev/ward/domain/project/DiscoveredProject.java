package dev.ward.domain.project;

/**
 * A project found by repository discovery.
 *
 * @param project the project, rooted at its outermost build aggregator
 * @param moduleCount how many build modules discovery grouped under it
 */
public record DiscoveredProject(Project project, int moduleCount) {}
