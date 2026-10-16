// Тестовые данные браузерной файловой системы.

export const ROOT = '/storage/emulated/0';

export const SD = '/storage/1A2B-3C4D';

export const DAY = 86400000;

const now = Date.now();

export const fs = new Map();

const put = (path, props = {}) => fs.set(path, { dir: false, size: 0, mtime: now - Math.random() * 40 * DAY, ...props });

export const dir = (path) => put(path, { dir: true });

export const file = (path, size, props = {}) => put(path, { size, ...props });

[ROOT, SD].forEach(dir);
['Download', 'DCIM', 'DCIM/Camera', 'Pictures', 'Pictures/Screenshots', 'Music', 'Movies', 'Documents', 'Documents/Работа', 'Android', 'Telegram'].forEach((d) => dir(`${ROOT}/${d}`));
for (let i = 1; i <= 14; i++) file(`${ROOT}/DCIM/Camera/IMG_2026${String(i).padStart(2, '0')}15_1${i}2233.jpg`, 2_400_000 + i * 91_000);
for (let i = 1; i <= 6; i++) file(`${ROOT}/Pictures/Screenshots/Screenshot_${i}.png`, 640_000 + i * 12_000);
file(`${ROOT}/DCIM/Camera/VID_20260301_184502.mp4`, 184_000_000);
file(`${ROOT}/Download/report-q3.pdf`, 1_820_000);
file(`${ROOT}/Download/setup-1.4.2.apk`, 48_300_000);
file(`${ROOT}/Download/backup-2026-09.zip`, 312_000_000);
file(`${ROOT}/Download/Фильм 2024 (1080p).mkv`, 2_400_000_000);
file(`${ROOT}/Music/Дорога домой.mp3`, 7_900_000);
file(`${ROOT}/Music/ambient-02.flac`, 38_500_000);
file(`${ROOT}/Documents/Работа/бюджет.xlsx`, 84_000);
file(`${ROOT}/Documents/Работа/договор.docx`, 129_000);
file(`${ROOT}/Documents/заметки.txt`, 214, { text: 'Купить хлеб\nПозвонить в сервис\nОбновить FileDeck\n' });
file(`${ROOT}/Documents/config.json`, 58, { text: '{\n  "theme": "dark",\n  "sync": false\n}\n' });
file(`${ROOT}/Documents/README.md`, 120, { text: '# Проект\n\nОписание проекта.\n' });
file(`${ROOT}/Download/report-q3 (1).pdf`, 1_820_000);
file(`${ROOT}/Documents/Работа/report-q3.pdf`, 1_820_000);
file(`${ROOT}/Download/setup-1.4.2 (1).apk`, 48_300_000);
file(`${ROOT}/DCIM/Camera/IMG_20260115_112233 (1).jpg`, 2_490_000);
file(`${ROOT}/Telegram/IMG_20260115_112233.jpg`, 2_490_000);
for (let i = 2; i <= 4; i++) file(`${ROOT}/Pictures/Screenshots/Screenshot_${i}_edit.png`, 640_000 + i * 12_000);
file(`${ROOT}/Download/installer-old.apk`, 22_000_000);
file(`${ROOT}/Download/video-part.crdownload`, 54_000_000);
file(`${ROOT}/Download/export.tmp`, 3_100_000);
file(`${ROOT}/Documents/debug.log`, 780_000);
file(`${ROOT}/Pictures/.thumbnails/t1.dat`, 18_000_000);
file(`${ROOT}/Telegram/cache/c1.bin`, 96_000_000);
file(`${ROOT}/Documents/пусто.txt`, 0, { text: '' });
file(`${ROOT}/Download/пустой файл.dat`, 0);
dir(`${ROOT}/Documents/Старое`); dir(`${ROOT}/Documents/Старое/2019`);
dir(`${ROOT}/Download/Новая папка`);
file(`${ROOT}/Documents/secret.zip`, 120_000);
file(`${ROOT}/Download/archive-2025.7z`, 88_000_000);
file(`${ROOT}/Download/photos.rar`, 41_000_000);
dir(`${SD}/Backup`);
file(`${SD}/Backup/photos-2025.zip`, 1_200_000_000);
file(`${SD}/video.mp4`, 920_000_000);
