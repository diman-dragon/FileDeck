package dev.filedeck.app;

import java.io.IOException;

/** Ошибка с кодом, который понимает интерфейс (PASSWORD_REQUIRED, WRONG_PASSWORD, ...). */
final class ArchiveError extends IOException {
    final String code;

    ArchiveError(String code, String message) {
        super(message);
        this.code = code;
    }
}
