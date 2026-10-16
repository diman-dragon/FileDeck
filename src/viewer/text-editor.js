// Текстовый редактор.
import { icon } from '../icons/icon.js';
import { pushLayer } from '../layers/stack.js';
import { el } from '../layers/dom.js';
import { toast } from '../layers/toast.js';
import { confirmDialog } from '../layers/confirm-dialog.js';
import { FM } from '../plugin.js';
import { escapeHtml } from '../html.js';

/** Простой текстовый редактор (до 2 МБ, UTF-8). */
export async function openEditor(entry, { onSaved } = {}) {
  let text;
  try {
    ({ text } = await FM.readText({ path: entry.path }));
  } catch (e) {
    toast(e.message || 'Не удалось прочитать файл', { error: true });
    return;
  }
  let original = text;
  let dirty = false;

  const node = el(
    `<div class="ed-bar">
       <button class="icon-btn" data-act="back" aria-label="Закрыть">${icon('back')}</button>
       <div class="ed-title"><span class="ed-name">${escapeHtml(entry.name)}</span><i class="ed-dot" hidden></i></div>
       <button class="btn primary small" data-act="save" disabled>Сохранить</button>
     </div>
     <textarea class="ed-area" spellcheck="false" autocapitalize="off" autocomplete="off"></textarea>`,
    'layer-root layer-editor'
  );
  const area = node.querySelector('.ed-area');
  const saveBtn = node.querySelector('[data-act="save"]');
  const dot = node.querySelector('.ed-dot');
  area.value = text;

  const setDirty = (d) => {
    dirty = d;
    saveBtn.disabled = !d;
    dot.hidden = !d;
  };
  const save = async () => {
    if (!dirty) return;
    try {
      await FM.writeText({ path: entry.path, text: area.value });
      original = area.value;
      setDirty(false);
      toast('Сохранено');
      onSaved && onSaved();
    } catch (e) {
      toast(e.message || 'Не удалось сохранить', { error: true });
    }
  };
  let closeLayer;
  const requestClose = async () => {
    if (dirty) {
      const discard = await confirmDialog({
        title: 'Закрыть без сохранения?',
        text: 'Изменения в файле будут потеряны.',
        okText: 'Закрыть',
      });
      if (!discard) return;
    }
    closeLayer();
  };
  closeLayer = pushLayer(node, { onBack: requestClose });

  area.addEventListener('input', () => setDirty(area.value !== original));
  area.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
      e.preventDefault();
      save();
    }
    if (e.key === 'Tab') {
      e.preventDefault();
      const s = area.selectionStart;
      area.setRangeText('  ', s, area.selectionEnd, 'end');
      area.dispatchEvent(new Event('input'));
    }
  });
  node.querySelector('[data-act="back"]').addEventListener('click', requestClose);
  saveBtn.addEventListener('click', save);
}
