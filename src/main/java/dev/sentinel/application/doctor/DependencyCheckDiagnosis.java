package dev.sentinel.application.doctor;

import dev.sentinel.application.doctor.DoctorService.Finding;
import dev.sentinel.application.doctor.DoctorService.Status;
import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.OssIndexSetting;
import java.util.ArrayList;
import java.util.List;

/** Credential warnings for an enabled dependency-check gate; never reports values. */
final class DependencyCheckDiagnosis {
  private DependencyCheckDiagnosis() {}

  static List<Finding> findings(final EnvironmentFacts facts, final OssIndexSetting ossIndex) {
    final List<Finding> findings = new ArrayList<>();
    if (!facts.isSet("NVD_API_KEY")) {
      findings.add(
          new Finding(
              "dependency-check-nvd-api-key",
              Status.WARNING,
              "NVD_API_KEY is not set; dependency-check scans may be very slow or rate-limited."
                  + " Request a key from the NVD API."));
    }
    if (ossIndex == OssIndexSetting.ENABLED && !facts.isSet("OSS_INDEX_PASSWORD")) {
      findings.add(
          new Finding(
              "dependency-check-oss-index",
              Status.WARNING,
              "OSS Index is enabled for dependency-check but OSS_INDEX_PASSWORD is not set;"
                  + " scans fail with 401 Unauthorized. Disable the OSS Index analyzer or set"
                  + " OSS_INDEX_PASSWORD (Maven reads it through a settings.xml server)."));
    }
    return findings;
  }
}
