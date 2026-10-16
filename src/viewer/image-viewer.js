// Просмотр изображений.
import { icon } from '../icons/icon.js';
import { pushLayer } from '../layers/stack.js';
import { el } from '../layers/dom.js';
import { toast } from '../layers/toast.js';
import { FM } from '../plugin.js';
import { fileUrl } from '../file-url.js';
import { formatSize, formatDate } from '../format.js';

/** Просмотр изображений: свайп между файлами, двойной тап — масштаб, тап — скрыть панели. */
export function openImageViewer(images, startIndex) {
  let i = startIndex;
  let scale = 1;
  let tx = 0;
  let ty = 0;
  let chromeVisible = true;

  const node = el(
    `<div class="iv-bar iv-top">
       <button class="icon-btn" data-act="back" aria-label="Назад">${icon('back')}</button>
       <div class="iv-title"><div class="iv-name"></div><div class="iv-meta"></div></div>
       <button class="icon-btn" data-act="share" aria-label="Поделиться">${icon('share')}</button>
       <button class="icon-btn" data-act="external" aria-label="Открыть в приложении">${icon('external')}</button>
     </div>
     <div class="iv-stage"><img class="iv-img" alt="" draggable="false" /></div>
     <div class="iv-bar iv-bottom"><button class="icon-btn" data-act="prev" aria-label="Предыдущее">${icon('back')}</button><span class="iv-count"></span><button class="icon-btn flip" data-act="next" aria-label="Следующее">${icon('back')}</button></div>`,
    'layer-root layer-viewer'
  );
  const close = pushLayer(node);
  const img = node.querySelector('.iv-img');
  const stage = node.querySelector('.iv-stage');

  const apply = () => {
    img.style.transform = `translate(${tx}px, ${ty}px) scale(${scale})`;
  };
  const show = (n) => {
    i = (n + images.length) % images.length;
    scale = 1;
    tx = ty = 0;
    const e = images[i];
    img.classList.remove('loaded');
    img.onload = () => img.classList.add('loaded');
    img.src = fileUrl(e.path);
    apply();
    node.querySelector('.iv-name').textContent = e.name;
    node.querySelector('.iv-meta').textContent = `${formatSize(e.size)} · ${formatDate(e.mtime, true)}`;
    node.querySelector('.iv-count').textContent = `${i + 1} / ${images.length}`;
  };
  show(i);

  node.addEventListener('click', (ev) => {
    const act = ev.target.closest('[data-act]')?.dataset.act;
    if (!act) return;
    if (act === 'back') close();
    if (act === 'prev') show(i - 1);
    if (act === 'next') show(i + 1);
    if (act === 'share') FM.share({ paths: [images[i].path] }).catch((e) => toast(e.message, { error: true }));
    if (act === 'external') FM.open({ path: images[i].path }).catch((e) => toast(e.message, { error: true }));
  });

  // жесты
  let startX = 0, startY = 0, baseTx = 0, baseTy = 0, moved = false, lastTap = 0;
  stage.addEventListener('pointerdown', (e) => {
    stage.setPointerCapture(e.pointerId);
    startX = e.clientX;
    startY = e.clientY;
    baseTx = tx;
    baseTy = ty;
    moved = false;
  });
  stage.addEventListener('pointermove', (e) => {
    if (!stage.hasPointerCapture(e.pointerId)) return;
    const dx = e.clientX - startX;
    const dy = e.clientY - startY;
    if (Math.abs(dx) + Math.abs(dy) > 6) moved = true;
    if (scale > 1) {
      tx = baseTx + dx;
      ty = baseTy + dy;
      apply();
    }
  });
  stage.addEventListener('pointerup', (e) => {
    const dx = e.clientX - startX;
    if (moved) {
      if (scale === 1 && Math.abs(dx) > 60 && images.length > 1) show(dx < 0 ? i + 1 : i - 1);
      return;
    }
    const t = Date.now();
    if (t - lastTap < 280) {
      scale = scale > 1 ? 1 : 2.5;
      tx = ty = 0;
      apply();
      lastTap = 0;
    } else {
      lastTap = t;
      setTimeout(() => {
        if (lastTap === t) {
          chromeVisible = !chromeVisible;
          node.classList.toggle('chrome-hidden', !chromeVisible);
        }
      }, 290);
    }
  });
}
