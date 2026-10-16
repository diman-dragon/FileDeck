// Версия и номер сборки, подставляются Vite из package.json.

export const APP_VERSION = typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : 'dev';

export const APP_BUILD = typeof __APP_BUILD__ === 'number' ? __APP_BUILD__ : 0;
