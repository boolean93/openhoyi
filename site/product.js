(() => {
  'use strict';

  // Pages publishes the verified Develop index with this site. Leave the static
  // Releases link usable even when the index, network or JavaScript is unavailable.
  const directApk = document.querySelector('[data-direct-apk]');
  const downloadRelease = document.querySelector('[data-download-release]');
  if (directApk && downloadRelease && typeof fetch === 'function' && typeof AbortController === 'function') {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6000);
    fetch('./updates/dev.json', { signal: controller.signal, cache: 'no-cache', redirect: 'error' })
      .then(response => {
        if (!response.ok) throw new Error('No verified Develop index');
        return response.json();
      })
      .then(index => {
        const repository = 'boolean93/openhoyi';
        if (!index || index.schemaVersion !== 1 || index.repository !== repository ||
            index.channel !== 'develop-test' || index.packageName !== 'io.openhoyi.mobile' ||
            !Number.isSafeInteger(index.versionCode) || index.versionCode < 1 ||
            typeof index.versionName !== 'string' || index.versionName.length > 100 ||
            !/^[0-9]+\.[0-9]+\.[0-9]+-dev\.[0-9]+$/.test(index.versionName) ||
            !index.versionName.endsWith(`-dev.${index.versionCode}`) ||
            !Number.isSafeInteger(index.apkSize) || index.apkSize < 1 || index.apkSize > 256 * 1024 * 1024 ||
            typeof index.apkSha256 !== 'string' || !/^[a-f0-9]{64}$/.test(index.apkSha256)) return;

        const tag = `dev-${index.versionCode}`;
        const filename = `OpenHOYI-${index.versionName}.apk`;
        const origin = `https://github.com/${repository}/releases`;
        const apkUrl = `${origin}/download/${tag}/${filename}`;
        const releaseUrl = `${origin}/tag/${tag}`;
        if (index.releaseTag !== tag || index.apkFile !== filename ||
            index.downloadUrl !== apkUrl || index.releaseUrl !== releaseUrl) return;

        directApk.href = apkUrl;
        downloadRelease.href = releaseUrl;
        downloadRelease.classList.replace('button-primary', 'button-outline');
        directApk.hidden = false;
      })
      .catch(() => { /* The existing Releases link is the fallback. */ })
      .finally(() => clearTimeout(timeout));
  }

  const nav = document.querySelector('[data-nav]');
  if (nav) {
    let ticking = false;
    const update = () => {
      nav.classList.toggle('is-scrolled', window.scrollY > 24);
      ticking = false;
    };
    update();
    window.addEventListener('scroll', () => {
      if (ticking) return;
      ticking = true;
      window.requestAnimationFrame(update);
    }, { passive: true });
  }

  const revealTargets = document.querySelectorAll('.section [data-reveal]');
  if (revealTargets.length && 'IntersectionObserver' in window &&
      !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('reveal-in');
        observer.unobserve(entry.target);
      });
    }, { threshold: 0.08, rootMargin: '0px 0px -8% 0px' });
    revealTargets.forEach((target) => observer.observe(target));
  }

  const revealDetails = (hash) => {
    if (!hash || hash[0] !== '#') return;
    const target = document.getElementById(hash.slice(1));
    if (target instanceof HTMLDetailsElement) target.open = true;
  };
  document.querySelectorAll('a[href^="#"]').forEach((link) => {
    link.addEventListener('click', () => revealDetails(link.hash));
  });
  window.addEventListener('hashchange', () => revealDetails(window.location.hash));
  revealDetails(window.location.hash);

  const compare = document.querySelector('[data-compare]');
  if (compare) {
    const buttons = Array.from(compare.querySelectorAll('[data-metric]'));
    buttons.forEach((button) => { button.hidden = false; });
    const select = (metric) => {
      compare.dataset.active = metric;
      buttons.forEach((button) => {
        button.setAttribute('aria-pressed', String(button.dataset.metric === metric));
      });
    };
    buttons.forEach((button, index) => {
      button.addEventListener('click', () => select(button.dataset.metric));
      button.addEventListener('keydown', (event) => {
        if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
        event.preventDefault();
        const step = event.key === 'ArrowRight' ? 1 : -1;
        const next = buttons[(index + step + buttons.length) % buttons.length];
        next.focus();
        select(next.dataset.metric);
      });
    });
  }

  const viewer = document.querySelector('#image-viewer');
  if (!viewer || typeof viewer.showModal !== 'function') return;

  const image = viewer.querySelector('[data-viewer-image]');
  const title = viewer.querySelector('[data-viewer-title]');
  const original = viewer.querySelector('[data-viewer-original]');
  const scale = viewer.querySelector('[data-viewer-scale]');
  const close = viewer.querySelector('[data-viewer-close]');
  const scroll = viewer.querySelector('.viewer-scroll');
  let returnFocus = null;
  let focusX = 1200;
  let focusY = 700;

  const resetScale = () => {
    viewer.dataset.originalSize = 'false';
    scale.setAttribute('aria-pressed', 'false');
    scale.textContent = '放大查看';
    scroll.scrollTop = 0;
    scroll.scrollLeft = 0;
  };

  scale.hidden = false;
  document.querySelectorAll('[data-view-image]').forEach((link) => {
    link.addEventListener('click', (event) => {
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      returnFocus = link;
      focusX = Number(link.dataset.focusX) || 1200;
      focusY = Number(link.dataset.focusY) || 700;
      title.textContent = link.dataset.imageTitle;
      image.src = link.href;
      image.alt = `${link.dataset.imageTitle}，模拟器平板尺寸界面，使用模拟数据，非真机截图`;
      original.href = link.href;
      resetScale();
      viewer.showModal();
      close.focus();
    });
  });

  scale.addEventListener('click', () => {
    const enlarged = viewer.dataset.originalSize !== 'true';
    viewer.dataset.originalSize = String(enlarged);
    scale.setAttribute('aria-pressed', String(enlarged));
    scale.textContent = enlarged ? '适应宽度' : '放大查看';
    scroll.scrollLeft = enlarged ? Math.max(0, focusX - scroll.clientWidth / 2) : 0;
    scroll.scrollTop = enlarged ? Math.max(0, focusY - scroll.clientHeight / 2) : 0;
  });

  close.addEventListener('click', () => viewer.close());
  viewer.addEventListener('click', (event) => {
    if (event.target !== viewer) return;
    const box = viewer.getBoundingClientRect();
    const outside = event.clientX < box.left || event.clientX > box.right ||
      event.clientY < box.top || event.clientY > box.bottom;
    if (outside) viewer.close();
  });
  viewer.addEventListener('close', () => {
    if (returnFocus) returnFocus.focus({ preventScroll: true });
  });
})();
