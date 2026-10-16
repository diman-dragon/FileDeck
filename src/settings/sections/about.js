// Карточка «О нас»: логотип, название, версия и сведения о приложении.
import { folderGlyph } from '../../icons/folder-glyph.js';
import { escapeHtml } from '../../html.js';

const fact = (key, value) => `<div class="about-fact"><span>${key}</span><b>${value}</b></div>`;

export const aboutCard = (ctx) => `
  <div class="about-card">
    <div class="about-bg" aria-hidden="true">${folderGlyph(190)}</div>
    <div class="about-head">
      <div class="about-logo"><span class="about-d" aria-hidden="true"></span></div>
      <div class="about-title">
        <div class="about-name">FileDeck</div>
        <div class="about-tag">Файловый менеджер без лишнего</div>
      </div>
    </div>
    <div class="about-chips">
      <span class="chip">Версия ${escapeHtml(ctx.version)}</span>
      <span class="chip soft">сборка ${escapeHtml(String(ctx.build))}</span>
    </div>
    <div class="about-facts">
      ${fact('Сделано в', 'DataLaw')}
      ${fact('Платформа', ctx.native ? 'Android' : 'Веб-превью')}
      ${fact('Основа', 'Capacitor 8')}
    </div>
  </div>`;
