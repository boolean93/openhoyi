/* Progressive enhancement only. No telemetry, remote requests, or device controls. */
(() => {
  'use strict';

  const guide = document.querySelector('[data-product-guide]');
  if (guide) {
    const tabs = [...guide.querySelectorAll('[data-scene-tab]')];
    const panels = tabs.map(tab => document.getElementById(tab.dataset.sceneTab));
    if (tabs.length && panels.every(Boolean)) {
      const select = (index, focus = false) => {
        tabs.forEach((tab, i) => {
          tab.setAttribute('aria-selected', String(i === index));
          tab.tabIndex = i === index ? 0 : -1;
          panels[i].hidden = i !== index;
        });
        if (focus) tabs[index].focus();
      };
      guide.querySelector('[data-scene-tabs]').setAttribute('role', 'tablist');
      tabs.forEach((tab, i) => {
        tab.setAttribute('role', 'tab');
        tab.setAttribute('aria-controls', panels[i].id);
        panels[i].setAttribute('role', 'tabpanel');
        panels[i].setAttribute('aria-labelledby', tab.id);
        panels[i].tabIndex = 0;
        tab.addEventListener('click', event => {
          event.preventDefault();
          select(i);
        });
        tab.addEventListener('keydown', event => {
          let next;
          if (event.key === 'ArrowRight') next = (i + 1) % tabs.length;
          if (event.key === 'ArrowLeft') next = (i - 1 + tabs.length) % tabs.length;
          if (event.key === ' ' || event.key === 'Enter') next = i;
          if (event.key === 'Home') next = 0;
          if (event.key === 'End') next = tabs.length - 1;
          if (next !== undefined) {
            event.preventDefault();
            select(next, true);
          }
        });
      });
      guide.dataset.enhanced = 'true';
      select(Math.max(0, tabs.findIndex(tab => tab.dataset.sceneTab === 'observe')));
    }
  }

  const viewer = document.getElementById('image-viewer');
  if (viewer && typeof viewer.showModal === 'function') {
    const image = viewer.querySelector('img');
    const title = viewer.querySelector('[data-viewer-title]');
    const original = viewer.querySelector('[data-viewer-original]');
    const close = viewer.querySelector('[data-viewer-close]');
    const scale = viewer.querySelector('[data-viewer-scale]');
    const scroll = viewer.querySelector('.viewer-scroll');
    if (scale) {
      scale.hidden = false;
      scale.addEventListener('click', () => {
        const fullSize = viewer.dataset.originalSize !== 'true';
        viewer.dataset.originalSize = String(fullSize);
        scale.setAttribute('aria-pressed', String(fullSize));
        scale.textContent = fullSize ? '适应窗口' : '原尺寸';
        scroll.scrollTo(0, 0);
      });
    }
    let trigger;
    document.querySelectorAll('[data-view-image]').forEach(link => {
      link.addEventListener('click', event => {
        if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        trigger = link;
        image.src = link.href;
        image.alt = link.dataset.viewImage;
        title.textContent = link.dataset.viewImage;
        original.href = link.href;
        viewer.dataset.originalSize = 'false';
        if (scale) {
          scale.setAttribute('aria-pressed', 'false');
          scale.textContent = '原尺寸';
        }
        viewer.showModal();
        scroll.scrollTo(0, 0);
        close.focus();
      });
    });
    close.addEventListener('click', () => viewer.close());
    viewer.addEventListener('click', event => {
      if (event.target === viewer) {
        const bounds = viewer.getBoundingClientRect();
        if (event.clientX < bounds.left || event.clientX > bounds.right ||
            event.clientY < bounds.top || event.clientY > bounds.bottom) viewer.close();
      }
    });
    viewer.addEventListener('close', () => trigger?.focus());
  }

  const copy = document.querySelector('[data-copy-command]');
  const command = document.getElementById('build-command');
  const status = document.getElementById('copy-status');
  if (copy && command && status && navigator.clipboard?.writeText) {
    copy.hidden = false;
    copy.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(command.textContent.trim() + '\n');
        status.textContent = '命令已复制。运行前请先阅读构建与使用风险说明。';
      } catch {
        status.textContent = '复制未成功，可以直接选中下方命令。';
      }
    });
  }
})();
