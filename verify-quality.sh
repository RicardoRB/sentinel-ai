#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$ROOT"

if [ -x "$ROOT/mvnw" ]; then
  MVN="$ROOT/mvnw"
elif command -v mvn >/dev/null 2>&1; then
  MVN=mvn
else
  echo "verify-quality: Maven is unavailable" >&2
  exit 2
fi

"$MVN" -q -Dtest=ArchitectureBoundaryTest test

"$MVN" -q \
  org.jacoco:jacoco-maven-plugin:prepare-agent \
  test \
  checkstyle:check \
  spotbugs:check \
  org.jacoco:jacoco-maven-plugin:report

coverage=$(awk -F, '
  NR > 1 { missed += $4; covered += $5 }
  END {
    if (missed + covered == 0) exit 2
    printf "%.2f", (covered * 100) / (missed + covered)
  }
' target/site/jacoco/jacoco.csv) || {
  echo "verify-quality: JaCoCo produced no instruction coverage data" >&2
  exit 1
}

awk -v coverage="$coverage" 'BEGIN { if (coverage < 80) exit 1 }' || {
  echo "verify-quality: JaCoCo instruction coverage is ${coverage}% (80% required)" >&2
  exit 1
}

echo "verify-quality: passed (JaCoCo instruction coverage ${coverage}%)"
