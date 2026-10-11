package com.rbdip.bookstore.migration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Фоновая нагрузка: несколько потоков в цикле выполняют одно и то же
 * действие, пока нагрузку не остановят. Любое исключение действия (ошибка
 * SQL, не-2xx ответ, таймаут) записывается как ошибка, а не прерывает поток;
 * хранятся только первые {@value #MAX_KEPT_ERRORS} ошибок.
 */
final class ConcurrentLoad implements AutoCloseable {

    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }

    private static final int MAX_KEPT_ERRORS = 20;

    private final ExecutorService pool;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong iterations = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final List<Exception> errors = new CopyOnWriteArrayList<>();

    private ConcurrentLoad(int workers, Action action) {
        pool = Executors.newFixedThreadPool(workers);
        for (int i = 0; i < workers; i++) {
            pool.submit(() -> loop(action));
        }
    }

    static ConcurrentLoad start(int workers, Action action) {
        return new ConcurrentLoad(workers, action);
    }

    private void loop(Action action) {
        while (running.get()) {
            try {
                action.run();
                iterations.incrementAndGet();
            } catch (Exception e) {
                if (errorCount.incrementAndGet() <= MAX_KEPT_ERRORS) {
                    errors.add(e);
                }
            }
        }
    }

    /** Ждёт ещё {@code count} успешных итераций - так нагрузка гарантированно идёт до и после шага. */
    void awaitMoreIterations(long count, Duration timeout) throws InterruptedException {
        long target = iterations.get() + count;
        long deadline = System.nanoTime() + timeout.toNanos();
        while (iterations.get() < target) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("load made only " + iterations.get() + " successful iterations; "
                        + errorCount.get() + " errors, first ones: " + errors);
            }
            Thread.sleep(10);
        }
    }

    /** Первые ошибки нагрузки; пустой список - ошибок не было вовсе. */
    List<Exception> errors() {
        return List.copyOf(errors);
    }

    @Override
    public void close() throws InterruptedException {
        running.set(false);
        pool.shutdown();
        if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
            pool.shutdownNow();
        }
    }
}
