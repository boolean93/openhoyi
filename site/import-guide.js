/* No GitHub API token, analytics, or uploaded screenshots. This optional index
 * is published with Pages after the release asset has been verified. */
(() => {
  'use strict';
  const status = document.getElementById('prompt-status');
  const download = document.getElementById('prompt-download');
  const fallback = '请按 App 版本选择附件。若该版本没有提示词，请先升级到带有附件的版本。';
  const requested = new URLSearchParams(window.location.search).get('version');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8000);
  fetch('./updates/curve-import-prompt.json', { signal: controller.signal, cache: 'no-cache' })
    .then(response => {
      if (!response.ok) throw new Error('No verified prompt index');
      return response.json();
    })
    .then(index => {
      if (index.schemaVersion !== 1 || index.available !== true ||
          !Number.isSafeInteger(index.versionCode) || index.versionCode < 1 ||
          index.releaseTag !== `dev-${index.versionCode}` ||
          typeof index.versionName !== 'string' || index.versionName.length > 100 ||
          !Number.isSafeInteger(index.schema) || index.schema < 1 ||
          !Number.isSafeInteger(index.revision) || index.revision < 1 ||
          !/^[a-f0-9]{64}$/.test(index.sha256 || '')) throw new Error('Unavailable');
      const expected = `https://github.com/boolean93/openhoyi/releases/download/${index.releaseTag}/OpenHOYI-curve-import-prompt.zh-CN.txt`;
      if (index.downloadUrl !== expected) throw new Error('Unexpected asset');
      if (requested && requested !== index.releaseTag && requested !== index.versionName && requested !== String(index.versionCode)) {
        status.textContent = '当前提示词与此 App 版本不一致，请在对应 Release 选择附件，或升级后使用新版提示词。';
        return;
      }
      download.href = expected;
      download.textContent = `下载 ${index.releaseTag} 提示词`;
      download.rel = 'noopener';
      download.hidden = false;
      status.textContent = `适用 ${index.versionName}（${index.releaseTag}）。请确认与你的 App 版本一致；其他版本请在 Releases 中选择。`;
    })
    .catch(() => { status.textContent = fallback; })
    .finally(() => clearTimeout(timeout));
})();
