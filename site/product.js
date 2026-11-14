(() => {
  'use strict';

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
    buttons.forEach((button) => button.addEventListener('click', () => select(button.dataset.metric)));
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
