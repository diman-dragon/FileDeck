// Понятные тексты ошибок архиватора.

export const explain = (e) => {
  const map = {
    RAR5: 'Формат RAR5 пока не поддерживается. RAR 4 и старше открываются.',
    FORMAT: e.message || 'Этот формат не поддерживается',
    PASSWORD_UNSUPPORTED: 'Пароль можно задать только для ZIP',
  };
  return map[e.code] || e.message || 'Операция не удалась';
};
