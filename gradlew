#!/bin/sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle n'est pas installé localement. Utilisez ./gradlew avec une installation Gradle, ou le workflow GitHub Actions fourni." >&2
exit 1
