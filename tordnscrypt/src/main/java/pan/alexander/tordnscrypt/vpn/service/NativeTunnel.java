/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2026 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.vpn.service;

/**
 * Owns a native context and its worker. Native routing settings are process-wide.
 */
final class NativeTunnel implements AutoCloseable {
    interface Backend {
        long initialize();

        void start(long context);

        void stop(long context);

        void clear(long context);

        void destroy(long context);
    }

    interface Runner {
        void run(long context);
    }

    interface Dispatcher {
        void dispatch(Runnable action);
    }

    static final class ShutdownTimeoutException extends IllegalStateException {
        private final NativeTunnel owner;
        private final Thread worker;

        private ShutdownTimeoutException(NativeTunnel owner, Thread worker) {
            super("Native tunnel is still stopping");
            this.owner = owner;
            this.worker = worker;
        }
    }

    private static final class CleanupRetry {
        final ShutdownTimeoutException timeout;
        final long generation;
        final Dispatcher dispatcher;
        Runnable action;
        Thread waiter;

        CleanupRetry(ShutdownTimeoutException timeout, long generation,
                     Dispatcher dispatcher, Runnable action) {
            this.timeout = timeout;
            this.generation = generation;
            this.dispatcher = dispatcher;
            this.action = action;
        }
    }

    private static final Object LOCK = new Object();
    private static NativeTunnel active;
    private final Backend backend;
    private final long stopTimeoutMillis;
    // Context and worker are protected by LOCK; the worker never acquires LOCK.
    private long context;
    private Thread worker;
    private boolean stopRequested;
    private volatile boolean closed;
    // Command cancellation must not wait on LOCK while a worker is stopping.
    private final Object retryLock = new Object();
    private long retryGeneration;
    private CleanupRetry pendingRetry;

    NativeTunnel(Backend backend) {
        this(backend, 9000);
    }

    NativeTunnel(Backend backend, long stopTimeoutMillis) {
        if (stopTimeoutMillis <= 0)
            throw new IllegalArgumentException("Shutdown timeout must be positive");
        this.backend = backend;
        this.stopTimeoutMillis = stopTimeoutMillis;
    }

    void prepare() {
        synchronized (LOCK) {
            prepareLocked();
        }
    }

    boolean isRunning() {
        synchronized (LOCK) {
            return !closed && !stopRequested && worker != null && worker.isAlive();
        }
    }

    long cancelPendingRetry() {
        synchronized (retryLock) {
            retryGeneration++;
            cancelRetryLocked();
            return retryGeneration;
        }
    }

    private void cancelRetryLocked() {
        if (pendingRetry != null) {
            pendingRetry.action = null;
            pendingRetry.waiter.interrupt();
            pendingRetry = null;
        }
    }

    boolean retryAfterCleanup(ShutdownTimeoutException timeout, long generation,
                              Dispatcher dispatcher, Runnable action) {
        synchronized (retryLock) {
            if (closed || generation != retryGeneration)
                return false;
            cancelRetryLocked();
            CleanupRetry retry = new CleanupRetry(timeout, generation, dispatcher, action);
            retry.waiter = new Thread(() -> awaitCleanup(retry), "VPN cleanup retry");
            pendingRetry = retry;
            try {
                retry.waiter.start();
            } catch (RuntimeException | Error failure) {
                pendingRetry = null;
                retry.action = null;
                throw failure;
            }
            return true;
        }
    }

    private boolean isPending(CleanupRetry retry) {
        synchronized (retryLock) {
            return !closed && pendingRetry == retry && retry.generation == retryGeneration;
        }
    }

    private void awaitCleanup(CleanupRetry retry) {
        try {
            retry.timeout.worker.join();
        } catch (InterruptedException cancelled) {
            return;
        }
        synchronized (LOCK) {
            if (!isPending(retry))
                return;
            NativeTunnel previous = retry.timeout.owner;
            // Do not take the VPN back from a newer service that already started.
            if (active != null && active != previous && active != this) {
                synchronized (retryLock) {
                    if (pendingRetry == retry)
                        cancelRetryLocked();
                }
                return;
            }
            if (previous.worker == retry.timeout.worker) {
                previous.worker = null;
                previous.stopRequested = false;
                if (previous == this) {
                    if (context != 0)
                        backend.clear(context);
                } else {
                    previous.retire();
                    previous.destroyLocked();
                }
            }
        }
        if (isPending(retry))
            retry.dispatcher.dispatch(() -> runRetry(retry));
    }

    private void runRetry(CleanupRetry retry) {
        synchronized (LOCK) {
            final Runnable action;
            synchronized (retryLock) {
                if (!isPending(retry))
                    return;
                pendingRetry = null;
                action = retry.action;
                retry.action = null;
                if (active != null && active != retry.timeout.owner && active != this)
                    return;
            }
            action.run();
        }
    }

    /**
     * Serialize interface creation and native startup with ownership changes.
     */
    void withOwnership(Runnable operation) {
        synchronized (LOCK) {
            if (closed || active != this || context == 0)
                throw new IllegalStateException("Native tunnel no longer owns the VPN");
            operation.run();
        }
    }

    /**
     * Reject commands immediately on service destruction without waiting for shutdown.
     */
    void retire() {
        closed = true;
        cancelPendingRetry();
    }

    private void prepareLocked() {
        if (closed)
            throw new IllegalStateException("Native tunnel is closed");
        if (active != null && active != this) {
            NativeTunnel previous = active;
            previous.stopLocked();
            previous.retire();
            previous.destroyLocked();
        }
        if (context == 0) {
            context = backend.initialize();
            if (context == 0)
                throw new IllegalStateException("Cannot initialize native tunnel");
        }
        active = this;
    }

    void start(Runnable configure, Runner runner) {
        synchronized (LOCK) {
            if (closed)
                throw new IllegalStateException("Native tunnel is closed");
            if (!stopRequested && worker != null && worker.isAlive())
                return;
            // A worker may have exited on its own since the last start.
            stopLocked();
            prepareLocked();
            try {
                // Initialization resets global SOCKS settings, so configure afterward.
                configure.run();
                if (closed)
                    throw new IllegalStateException("Native tunnel is closed");
                stopRequested = false;
                backend.start(context);
                final long ownedContext = context;
                worker = new Thread(() -> runner.run(ownedContext), "VPN tunnel thread");
                worker.start();
            } catch (RuntimeException | Error failure) {
                worker = null;
                destroyLocked();
                throw failure;
            }
        }
    }

    void stop() {
        synchronized (LOCK) {
            stopLocked();
        }
    }

    private void stopLocked() {
        if (worker != null && worker.isAlive()) {
            stopRequested = true;
            backend.stop(context);
            try {
                worker.join(stopTimeoutMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Native tunnel shutdown interrupted", interrupted);
            }
            if (worker.isAlive())
                throw new ShutdownTimeoutException(this, worker);
        }
        worker = null;
        stopRequested = false;
        if (context != 0)
            backend.clear(context);
    }

    /**
     * Called on the service cleanup executor, never on the main thread.
     */
    @Override
    public void close() {
        retire();
        boolean interrupted = false;
        final Thread ownedWorker;
        synchronized (LOCK) {
            ownedWorker = worker;
            if (ownedWorker != null && ownedWorker.isAlive()) {
                stopRequested = true;
                backend.stop(context);
            }
        }
        // Retain the context, but let other owners reach their bounded stop attempt.
        if (ownedWorker != null) {
            while (ownedWorker.isAlive()) {
                try {
                    ownedWorker.join();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        }
        synchronized (LOCK) {
            // A replacement may already have joined this worker and destroyed its context.
            if (worker == ownedWorker) {
                worker = null;
                stopRequested = false;
                destroyLocked();
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    private void destroyLocked() {
        long ownedContext = context;
        context = 0;
        if (active == this)
            active = null;
        if (ownedContext != 0)
            backend.destroy(ownedContext);
    }
}
