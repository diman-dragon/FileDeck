// Вкладки настроек и горизонтальный «пейджер»: вкладка двигается вместе со свайпом.
export const tabsHtml = (pages) => `
  <div class="st-tabs" role="tablist" style="--n:${pages.length}">
    <i class="st-ind"></i>
    ${pages.map((p, i) => `<button role="tab" class="st-tab${i === 0 ? ' on' : ''}" data-tab="${i}">${p.title}</button>`).join('')}
  </div>`;

/** Связывает вкладки со свайпом страниц. Возвращает { goto(i) }. */
export function bindPager(node) {
  const pager = node.querySelector('.st-pager');
  const tabs = [...node.querySelectorAll('.st-tab')];
  const ind = node.querySelector('.st-ind');
  let raf = 0;

  const sync = () => {
    raf = 0;
    const w = pager.clientWidth || 1;
    const progress = pager.scrollLeft / w;
    ind.style.transform = `translateX(${progress * 100}%)`;
    const active = Math.round(progress);
    tabs.forEach((t, i) => {
      t.classList.toggle('on', i === active);
      t.setAttribute('aria-selected', String(i === active));
    });
  };
  pager.addEventListener('scroll', () => raf || (raf = requestAnimationFrame(sync)), { passive: true });

  const goto = (i) => pager.scrollTo({ left: i * pager.clientWidth, behavior: 'smooth' });
  node.querySelector('.st-tabs').addEventListener('click', (ev) => {
    const t = ev.target.closest('[data-tab]');
    if (t) goto(Number(t.dataset.tab));
  });
  sync();
  return { goto };
}
