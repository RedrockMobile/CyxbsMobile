#!/usr/bin/env bash
# 读取线上版本，并在当前 CI 工作树注入 PR 对应的正式版本文件。
set -euo pipefail

git fetch --no-tags origin release
printf '%s\n' "$PR_BODY" > "$RUNNER_TEMP/pr-body.txt"
python .github/scripts/release_version.py online --output "$RUNNER_TEMP/online-release.json"
python .github/scripts/release_version.py prepare \
  --online-file "$RUNNER_TEMP/online-release.json" --title "$PR_TITLE" \
  --body-file "$RUNNER_TEMP/pr-body.txt" --output "$RUNNER_TEMP/release-metadata.json"
git rev-parse origin/release > "$RUNNER_TEMP/release-base.sha"
git rev-parse HEAD > "$RUNNER_TEMP/release-head.sha"
