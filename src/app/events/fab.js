// Кнопка «+».
import { sheet } from '../../layers/sheet.js';
import { fab } from '../dom-refs.js';
import { createItem } from '../actions/create.js';

fab.addEventListener('click', () => {
  sheet({
    title: 'Создать',
    items: [
      { icon: 'folderPlus', label: 'Новая папка', onClick: () => createItem(true) },
      { icon: 'filePlus', label: 'Новый текстовый файл', onClick: () => createItem(false) },
    ],
  });
});
