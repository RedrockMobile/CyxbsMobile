#!/bin/sh
set -eu

# Xcode Cloud discovers this directory beside the production workspace.
CI_SCRIPTS_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
IOS_APP_DIR=$(dirname "$CI_SCRIPTS_DIR")
REPOSITORY_DIR=$(CDPATH= cd -- "$IOS_APP_DIR/../../.." && pwd)
export HOMEBREW_NO_AUTO_UPDATE=1
export HOMEBREW_NO_INSTALL_CLEANUP=1

brew list --versions openjdk@21 >/dev/null 2>&1 || brew install openjdk@21
command -v pod >/dev/null 2>&1 || brew install cocoapods
. "$CI_SCRIPTS_DIR/xcode_cloud_environment.sh"

java -version
pod --version
cd "$IOS_APP_DIR"
# Never patch Bugly into a simulator binary in a distribution build.
pod_attempt=1
until IS_SIMULATOR=0 pod install --deployment; do
  if [ "$pod_attempt" -ge 3 ]; then
    echo 'error: CocoaPods installation failed after 3 attempts.' >&2
    exit 1
  fi
  pod_attempt=$((pod_attempt + 1))
  echo "CocoaPods download failed; retrying locked dependencies (attempt $pod_attempt/3)."
  sleep 10
done

cd "$REPOSITORY_DIR"
# Download and check the repository's Gradle wrapper before xcodebuild starts.
./gradlew --version
