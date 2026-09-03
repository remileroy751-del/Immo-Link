#!/bin/sh
# ImmoLink wrapper bootstrap. GitHub Actions uses gradle/actions/setup-gradle.
# Local builds require Gradle 9.6.1 installed or available on PATH.
exec gradle "$@"
