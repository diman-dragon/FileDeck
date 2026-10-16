package dev.filedeck.app;

/** Что сканеру нужно от плагина: проверка отмены и отправка прогресса. */
interface ScanHost {
    /** Бросает Cancelled, если пользователь отменил сканирование. */
    void checkCancel();

    void emit(String phase, long done, long total, String current, boolean force);
}
