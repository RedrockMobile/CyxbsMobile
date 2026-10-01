#!/usr/bin/env bash
# 默认读取线上版本；--reuse-checked 复用版本检查输出，供两个 ABI 在独立 Runner 并行构建。
set -euo pipefail

printf '%s\n' "$PR_BODY" > "$RUNNER_TEMP/pr-body.txt"
case "${1:-}" in
  '')
    git fetch --no-tags origin release
    python3 .github/scripts/release_version.py online --output "$RUNNER_TEMP/online-release.json"
    git rev-parse origin/release > "$RUNNER_TEMP/release-base.sha"
    ;;
  --reuse-checked)
    # JSON 通过环境变量传递，避免 PR 文案或异常线上值被 shell 解释。
    python3 - <<'PY'
import json
import os
import re
from pathlib import Path

temp = Path(os.environ['RUNNER_TEMP'])
for variable, filename in (
    ('CHECKED_ONLINE_RELEASE', 'online-release.json'),
    ('CHECKED_RELEASE_METADATA', 'checked-metadata.json'),
):
    data = json.loads(os.environ[variable])
    (temp / filename).write_text(json.dumps(data, ensure_ascii=False) + '\n', encoding='utf-8')
base = os.environ['CHECKED_RELEASE_BASE']
if not re.fullmatch(r'[0-9a-f]{40}', base):
    raise ValueError('版本检查输出的 release 基线 SHA 非法')
(temp / 'release-base.sha').write_text(base + '\n', encoding='utf-8')
PY
    git cat-file -e "$(cat "$RUNNER_TEMP/release-base.sha")^{commit}"
    ;;
  *)
    echo '::error::候选版本注入参数非法。'
    exit 1
    ;;
esac
python3 .github/scripts/release_version.py prepare \
  --online-file "$RUNNER_TEMP/online-release.json" --title "$PR_TITLE" \
  --body-file "$RUNNER_TEMP/pr-body.txt" --output "$RUNNER_TEMP/release-metadata.json"
if [ "${1:-}" = --reuse-checked ] && ! cmp -s "$RUNNER_TEMP/release-metadata.json" "$RUNNER_TEMP/checked-metadata.json"; then
  echo '::error::并行构建使用的版本与版本检查结果不一致。'
  exit 1
fi
git rev-parse HEAD > "$RUNNER_TEMP/release-head.sha"
