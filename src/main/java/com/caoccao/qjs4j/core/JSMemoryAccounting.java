/*
 * Copyright (c) 2025-2026. caoccao.com Sam Cao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.caoccao.qjs4j.core;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime-owned accounting for the binary data blocks guest code sizes directly.
 * <p>
 * <strong>What this bounds.</strong> Every byte the engine allocates for an
 * {@code ArrayBuffer} or {@code SharedArrayBuffer} data block — and therefore for every typed
 * array and {@code DataView} built on one — is reserved here before the allocation happens. A
 * reservation that would exceed {@link JSRuntimeOptions#getMaxMemoryUsage()} is refused and the
 * caller raises a catchable {@code RangeError}, so a script cannot walk the JVM heap off a cliff
 * with {@code new ArrayBuffer(n)}. Reservations are released when the buffer is detached or
 * transferred, and otherwise when the buffer becomes unreachable, through a {@link Cleaner}.
 * <p>
 * <strong>What this does not bound.</strong> It is not a heap limit and does not pretend to be
 * one. Objects, arrays, strings, shapes, bytecode and the engine's own working memory are ordinary
 * Java allocations governed by {@code -Xmx}; a script that builds ten million small objects is
 * bounded by the JVM, not by this. Data blocks are singled out because they are the one allocation
 * whose size a script names outright, which makes them the cheap exhaustion primitive — the rest
 * costs a script proportionate work.
 * <p>
 * Accounting is per {@link JSRuntime}. A {@code SharedArrayBuffer} handed to another agent stays
 * charged to the runtime that created it.
 */
public final class JSMemoryAccounting {
    /**
     * Sentinel for "no limit".
     */
    public static final long UNLIMITED = 0L;
    /**
     * One {@code Cleaner} for the process rather than one per runtime: a {@code Cleaner} owns a
     * daemon thread, and an embedder that creates a runtime per unit of work would otherwise pay
     * for a thread every time.
     */
    private static final Cleaner CLEANER = Cleaner.create(runnable -> {
        Thread thread = new Thread(runnable, "qjs4j-memory-accounting");
        thread.setDaemon(true);
        return thread;
    });
    private final long limit;
    private final AtomicLong reservedBytes = new AtomicLong();

    /**
     * Create accounting with the given ceiling.
     *
     * @param limit the maximum number of data-block bytes, or a non-positive value for no limit
     */
    JSMemoryAccounting(long limit) {
        this.limit = Math.max(UNLIMITED, limit);
    }

    /**
     * The configured ceiling.
     *
     * @return the limit in bytes, or {@link #UNLIMITED}
     */
    public long getLimit() {
        return limit;
    }

    /**
     * The number of data-block bytes currently reserved.
     *
     * @return the reserved byte count
     */
    public long getReservedBytes() {
        return reservedBytes.get();
    }

    /**
     * Register a reservation to be released when {@code owner} becomes unreachable.
     * <p>
     * The returned handle also releases on demand, so a buffer that is detached or transferred
     * gives its reservation back immediately instead of waiting for a collection. Releasing twice
     * is harmless.
     *
     * @param owner the object whose lifetime the reservation follows
     * @param bytes the reserved byte count
     * @return a handle that releases the reservation
     */
    public Reservation registerReservation(Object owner, long bytes) {
        Reservation reservation = new Reservation(this, bytes);
        // The cleanup action must not capture `owner`, or the buffer would never become
        // unreachable and the reservation would never be released.
        CLEANER.register(owner, reservation);
        return reservation;
    }

    /**
     * Release a previous reservation.
     *
     * @param bytes the number of bytes to release; must not be negative
     */
    public void release(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Cannot release a negative number of bytes: " + bytes);
        }
        if (bytes > 0) {
            reservedBytes.addAndGet(-bytes);
        }
    }

    /**
     * Reserve capacity for a data block, or refuse.
     * <p>
     * Refusal is a normal, catchable outcome; the caller turns it into a {@code RangeError}. It is
     * deliberately not an {@code OutOfMemoryError}: a script that asks for too much has made an
     * ordinary mistake, and the engine stays usable afterwards.
     *
     * @param bytes the number of bytes to reserve; must not be negative
     * @return true when the reservation succeeded
     */
    public boolean reserve(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Cannot reserve a negative number of bytes: " + bytes);
        }
        if (limit == UNLIMITED) {
            reservedBytes.addAndGet(bytes);
            return true;
        }
        while (true) {
            long current = reservedBytes.get();
            if (bytes > limit - current) {
                return false;
            }
            if (reservedBytes.compareAndSet(current, current + bytes)) {
                return true;
            }
        }
    }

    /**
     * Whether a reservation of the given size would be refused.
     *
     * @param bytes the byte count
     * @return true when the reservation would exceed the limit
     */
    public boolean wouldExceedLimit(long bytes) {
        if (limit == UNLIMITED) {
            return false;
        }
        return bytes > limit - reservedBytes.get();
    }

    /**
     * A released-once reservation. Runs as the {@link Cleaner} action for its owner.
     */
    public static final class Reservation implements Runnable {
        private final JSMemoryAccounting accounting;
        private final AtomicBoolean released = new AtomicBoolean();
        private volatile long bytes;

        private Reservation(JSMemoryAccounting accounting, long bytes) {
            this.accounting = accounting;
            this.bytes = bytes;
        }

        /**
         * The size of this reservation.
         *
         * @return the byte count, or 0 once released
         */
        public long bytes() {
            return released.get() ? 0L : bytes;
        }

        /**
         * Grow this reservation, or refuse.
         *
         * @param additionalBytes the extra bytes to reserve
         * @return true when the reservation grew
         */
        public boolean grow(long additionalBytes) {
            if (released.get()) {
                return false;
            }
            if (!accounting.reserve(additionalBytes)) {
                return false;
            }
            bytes += additionalBytes;
            return true;
        }

        /**
         * Release this reservation. Idempotent.
         */
        public void release() {
            if (released.compareAndSet(false, true)) {
                accounting.release(bytes);
            }
        }

        @Override
        public void run() {
            release();
        }
    }
}
