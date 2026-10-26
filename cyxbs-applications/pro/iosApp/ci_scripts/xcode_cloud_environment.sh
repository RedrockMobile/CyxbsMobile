#!/bin/sh
# Source this in each process that runs Gradle. Exports in ci_post_clone.sh
# alone do not propagate to Xcode's Run Script build phases.
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo 'error: JDK 21 is missing; check ci_post_clone.sh.' >&2
  exit 1
fi
# Keep logs readable and avoid leaving a daemon running after the Cloud action.
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.daemon=false -Dorg.gradle.console=plain"
