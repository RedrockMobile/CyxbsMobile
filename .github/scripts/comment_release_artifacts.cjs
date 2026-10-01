/**
 * 更新发版 PR 的下载评论，接收 github-script 提供的 API 客户端、运行上下文和日志工具。
 * 产物名及检查结果从环境变量读取；仅更新当前 PR 中本机器人的评论，旧运行直接跳过。
 * 会读取本次 CI 的产物与 PR 评论，并创建或更新一条包含下载入口和中文检查结果的评论。
 */
module.exports = async function commentReleaseArtifacts({ github, context, core }) {
  const marker = '<!-- cyxbs-android-release-artifacts -->';
  const repo = context.repo;
  const eventPr = context.payload.pull_request;
  const { data: pr } = await github.rest.pulls.get({ ...repo, pull_number: eventPr.number });
  // 旧提交或旧文案的运行不能覆盖当前 PR 的下载提示。
  if (pr.state !== 'open' || pr.head.sha !== eventPr.head.sha
      || pr.title !== eventPr.title || pr.body !== eventPr.body || pr.draft !== eventPr.draft) {
    core.info('PR 已关闭或内容已变化，跳过旧运行的下载提示。');
    return;
  }
  const runUrl = `${context.serverUrl}/${repo.owner}/${repo.repo}/actions/runs/${context.runId}`;
  const artifacts = await github.paginate(github.rest.actions.listWorkflowRunArtifacts, {
    ...repo, run_id: context.runId, per_page: 100,
  });
  const entries = [
    ['**ARM64 正式 APK（内部群测试用）**', process.env.APK_ARTIFACT_NAME],
    ['x86_64 APK（模拟器测试用，保留一天）', process.env.UI_TEST_ARTIFACT_NAME],
    ['ARM64 mapping 混淆文件', process.env.MAPPING_ARTIFACT_NAME],
  ];
  // 仅链接本次运行内唯一且未过期的产物；失败时清除上一轮的旧链接。
  const rows = entries.map(([label, name]) => {
    const matches = artifacts.filter(item => name && item.name === name && !item.expired);
    const download = matches.length === 1
      ? `[下载 ${name}](${runUrl}/artifacts/${matches[0].id})`
      : '未生成或已过期，请查看 CI 日志并重跑';
    return `| ${label} | ${download} |`;
  });
  const checks = [
    ['版本检查', process.env.VERSION_RESULT],
    ['两个 ABI 构建', process.env.BUILD_RESULT],
    ['ARM64 产物检查', process.env.ARTIFACT_RESULT],
    ['更新弹窗与下载检测', process.env.UI_TEST_RESULT],
  ];
  const statusLabels = {
    success: '✅ 通过', failure: '❌ 失败', cancelled: '⏹️ 已取消', skipped: '⏭️ 已跳过',
  };
  const passed = checks.every(([, result]) => result === 'success');
  const body = [
    marker,
    '## Android 发版产物下载',
    '',
    passed
      ? '**CI 检查已通过。请下载 ARM64 正式 APK 发到内部群，重点确认登录和课表正常，再审批合入。**'
      : '**CI 检查尚未全部通过，请查看失败日志，修复后重跑再合入。**',
    '',
    '| 产物 | 下载地址 |',
    '| --- | --- |',
    ...rows,
    '',
    'Actions 下载的是 ZIP，解压后获得 `.Apk` 或 mapping 文件。',
    '',
    checks.map(([label, result]) => `${label}：${statusLabels[result] || '状态未知'}`).join('；'),
    '',
    `[查看本次 CI 检查和日志](${runUrl}) · 来源提交：\`${eventPr.head.sha.slice(0, 12)}\` · 第 ${process.env.GITHUB_RUN_ATTEMPT} 次运行`,
  ].join('\n');
  const comments = await github.paginate(github.rest.issues.listComments, {
    ...repo, issue_number: eventPr.number, per_page: 100,
  });
  // 固定标记且核对机器人作者，只更新自己的评论，保留人工讨论。
  const previous = comments.find(comment => comment.user?.login === 'github-actions[bot]'
    && comment.body?.startsWith(marker));
  if (previous) {
    await github.rest.issues.updateComment({ ...repo, comment_id: previous.id, body });
  } else {
    await github.rest.issues.createComment({ ...repo, issue_number: eventPr.number, body });
  }
};
