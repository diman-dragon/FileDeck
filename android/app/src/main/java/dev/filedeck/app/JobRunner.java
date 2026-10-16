package dev.filedeck.app;

import com.getcapacitor.PluginCall;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Выполняет работу плагина в своём пуле потоков: длинные операции не блокируют остальные вызовы. */
final class JobRunner {
    interface Job {
        void run() throws Exception;
    }

    private final ExecutorService pool;

    JobRunner(int threads) {
        pool = Executors.newFixedThreadPool(threads);
    }

    /** Запускает job; любое исключение превращается в call.reject с понятным текстом. */
    void async(final PluginCall call, final Job job) {
        pool.execute(() -> {
            try {
                job.run();
            } catch (Exception e) {
                String msg = e.getMessage();
                call.reject(msg == null || msg.isEmpty() ? e.getClass().getSimpleName() : msg);
            }
        });
    }

    /** Для операций с собственной обработкой ответа (сканирование, архивы). */
    void execute(Runnable task) {
        pool.execute(task);
    }

    void shutdown() {
        pool.shutdownNow();
    }
}
