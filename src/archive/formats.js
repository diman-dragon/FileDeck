// Форматы архивов и уровни сжатия для диалога создания.

export const FORMATS = [
  ['zip', 'ZIP', 'Подходит всем, есть пароль AES-256'],
  ['7z', '7z', 'Самое сильное сжатие'],
  ['tar.gz', 'TAR.GZ', 'Для Linux и серверов'],
  ['tar.xz', 'TAR.XZ', 'Сильное сжатие, медленнее'],
  ['tar.bz2', 'TAR.BZ2', 'Среднее сжатие'],
  ['tar', 'TAR', 'Без сжатия'],
];

export const LEVELS = [[2, 'Быстро'], [5, 'Обычно'], [9, 'Максимум']];

export const FORMAT_LABEL = { ZIP: 'ZIP', SEVENZ: '7z', TAR: 'TAR', TGZ: 'TAR.GZ', TBZ2: 'TAR.BZ2', TXZ: 'TAR.XZ', GZ: 'GZ', BZ2: 'BZ2', XZ: 'XZ', RAR: 'RAR' };
