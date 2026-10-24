package dev.filedeck.app;

/** Что логике архивов нужно от плагина: проверка отмены и отправка прогресса. */
public interface ArchiveHost {
    /** Бросает Cancelled, если пользователь нажал «Отмена». */
    void checkCancel();

    void emit(String op, double fraction, String current, boolean force);
}
