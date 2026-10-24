package dev.ward.domain.gate;

import java.util.List;
import java.util.Set;

/** Stable configuration identifiers for the built-in quality gates. */
public final class SupportedQualityGates {
  public static final String TRACEABILITY = "openspec-junit-traceability";
  public static final String OPENAPI_LINT = "openapi-lint";
  public static final String OPENAPI_DIFF = "openapi-diff";
  public static final String OPENAPI_COMPAT = "openapi-compat";
  public static final List<String> IDS =
      QualityGateDefinitions.all().stream().map(QualityGateDefinitions.Definition::id).toList();

  private static final Set<String> SPEC_IDS = Set.of("openspec", "openspec-archived");

  private static final Set<String> OPENAPI_IDS = Set.of(OPENAPI_LINT, OPENAPI_DIFF, OPENAPI_COMPAT);

  public static final List<String> LEGACY_IDS = List.of("architecture", "compliance");

  public static final String ZAP_TARGET_PLACEHOLDER = "<TARGET_URL>";

  private SupportedQualityGates() {}

  /** Specification validations, which run separately from code-quality checks when requested. */
  public static Set<String> specIds() {
    return SPEC_IDS;
  }

  /** The gates that check a committed OpenAPI spec and take OpenAPI options. */
  public static Set<String> openApiIds() {
    return OPENAPI_IDS;
  }
}
