// Диалог параметров нового архива: имя, формат, сжатие, пароль.
import { icon } from '../icons/icon.js';
import { pushLayer } from '../layers/stack.js';
import { el } from '../layers/dom.js';
import { toast } from '../layers/toast.js';
import { escapeHtml } from '../html.js';
import { FORMATS, LEVELS } from './formats.js';

export function createOptionsDialog({ defaultName, count }) {
  return new Promise((resolve) => {
    let format = 'zip';
    let level = 5;
    const node = el(
      `<div class="scrim"></div>
       <form class="dialog arc-dialog" role="dialog" aria-modal="true">
         <h2 class="dialog-title">Создать архив</h2>
         <p class="dialog-body">${count === 1 ? 'Один объект' : `Объектов: ${count}`}</p>
         <label class="fld-label" for="arc-name">Имя</label>
         <input id="arc-name" class="dialog-input" type="text" autocomplete="off" autocapitalize="off" spellcheck="false" value="${escapeHtml(defaultName)}" />
         <div class="fld-label">Формат</div>
         <div class="chips" data-group="format">${FORMATS.map(([v, l]) => `<button type="button" class="chip${v === format ? ' on' : ''}" data-v="${v}">${l}</button>`).join('')}</div>
         <p class="fld-hint" id="arc-hint"></p>
         <div class="fld-label">Сжатие</div>
         <div class="chips" data-group="level">${LEVELS.map(([v, l]) => `<button type="button" class="chip${v === level ? ' on' : ''}" data-v="${v}">${l}</button>`).join('')}</div>
         <div class="fld-label" id="pw-label">Пароль (необязательно)</div>
         <div class="pw-row"><input id="arc-pw" class="dialog-input" type="password" autocomplete="new-password" autocapitalize="off" spellcheck="false" placeholder="Без пароля" />
           <button type="button" class="icon-btn" data-act="eye" aria-label="Показать пароль">${icon('eye', 20)}</button></div>
         <div class="dialog-actions">
           <button type="button" class="btn ghost" data-act="cancel">Отмена</button>
           <button type="submit" class="btn primary">Создать</button>
         </div>
       </form>`,
      'layer-root layer-dialog'
    );
    let settled = false;
    const finish = (v) => {
      if (settled) return;
      settled = true;
      close();
      resolve(v);
    };
    const close = pushLayer(node, { onBack: () => finish(null) });
    const pw = node.querySelector('#arc-pw');
    const pwLabel = node.querySelector('#pw-label');
    const hint = node.querySelector('#arc-hint');
    const refresh = () => {
      hint.textContent = FORMATS.find((f) => f[0] === format)[2];
      const zip = format === 'zip';
      pw.disabled = !zip;
      if (!zip) pw.value = '';
      pw.placeholder = zip ? 'Без пароля' : 'Пароль есть только у ZIP';
      pwLabel.classList.toggle('muted', !zip);
    };
    refresh();
    node.querySelector('.scrim').addEventListener('click', () => finish(null));
    node.querySelector('[data-act="cancel"]').addEventListener('click', () => finish(null));
    node.querySelector('[data-act="eye"]').addEventListener('click', () => {
      pw.type = pw.type === 'password' ? 'text' : 'password';
    });
    node.querySelectorAll('.chips').forEach((group) =>
      group.addEventListener('click', (ev) => {
        const b = ev.target.closest('.chip');
        if (!b) return;
        group.querySelectorAll('.chip').forEach((c) => c.classList.toggle('on', c === b));
        if (group.dataset.group === 'format') format = b.dataset.v;
        else level = Number(b.dataset.v);
        refresh();
      })
    );
    node.querySelector('form').addEventListener('submit', (e) => {
      e.preventDefault();
      const name = node.querySelector('#arc-name').value.trim();
      if (!name) return toast('Введите имя архива', { error: true });
      if (name.includes('/')) return toast('В имени нельзя использовать «/»', { error: true });
      finish({ name, format, level, password: format === 'zip' ? pw.value : '' });
    });
    setTimeout(() => {
      const input = node.querySelector('#arc-name');
      input.focus();
      input.select();
    }, 220);
  });
}
