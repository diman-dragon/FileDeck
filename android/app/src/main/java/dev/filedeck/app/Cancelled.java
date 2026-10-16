package dev.filedeck.app;

/** Бросается из длинной операции, когда пользователь нажал «Отмена». */
final class Cancelled extends RuntimeException {
    Cancelled() {
        super("cancelled");
    }
}
