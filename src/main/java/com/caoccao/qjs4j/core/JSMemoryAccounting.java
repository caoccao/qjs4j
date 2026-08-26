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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * transferred, and otherwise when the buffer becomes unreachable: each reservation is a weak
 * reference to its buffer, and {@link #reserve(long)} drains the collected ones before deciding
 * whether the next allocation fits. Reclaiming at the moment the limit is consulted, on the thread
 * consulting it, is both more accurate than waiting for a background thread to have run and free of
 * one — this object belongs to a single runtime, and so does everything it reclaims.
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
     * The buffers whose data blocks have been collected, so their bytes can be given back.
     */
    private final ReferenceQueue<Object> collectedOwners = new ReferenceQueue<>();
    private final long limit;
    /**
     * The reservations still outstanding.
     * <p>
     * A reservation is only enqueued if the reference object is itself still reachable when its
     * buffer is collected, and the buffer is otherwise the only thing holding it — so this set is
     * what keeps reclamation possible. It holds reservations, never buffers, so it pins nothing.
     */
    private final Set<Reservation> outstandingReservations = ConcurrentHashMap.newKeySet();
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
        releaseCollectedReservations();
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
        Reservation reservation = new Reservation(this, owner, bytes);
        outstandingReservations.add(reservation);
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
     * Give back the bytes of every buffer the collector has taken.
     * <p>
     * Called before the limit is consulted and before the total is reported, both of which happen
     * on the thread using the engine. An empty queue poll costs nothing.
     */
    private void releaseCollectedReservations() {
        Reference<?> collected;
        while ((collected = collectedOwners.poll()) != null) {
            ((Reservation) collected).release();
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
        // Reclaim before refusing: a limit that still counts blocks the collector has already taken
        // is not the limit the embedder configured.
        releaseCollectedReservations();
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
     * A released-once reservation, and the weak reference to the buffer whose lifetime it follows.
     * <p>
     * Being the reference means there is no separate cleanup object to keep alive, and nothing
     * anywhere captures the buffer — which would stop it ever being collected and defeat the point.
     */
    public static final class Reservation extends WeakReference<Object> {
        private final JSMemoryAccounting accounting;
        private final AtomicBoolean released = new AtomicBoolean();
        private volatile long bytes;

        private Reservation(JSMemoryAccounting accounting, Object owner, long bytes) {
            super(owner, accounting.collectedOwners);
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
        public synchronized boolean grow(long additionalBytes) {
            if (additionalBytes < 0) {
                throw new IllegalArgumentException("Cannot grow by a negative number of bytes: " + additionalBytes);
            }
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
        public synchronized void release() {
            if (released.compareAndSet(false, true)) {
                accounting.release(bytes);
                bytes = 0L;
                accounting.outstandingReservations.remove(this);
                clear();
            }
        }

        /**
         * Give back part of this reservation.
         * <p>
         * This is the rollback for an allocation that was charged and then failed. Accounting is
         * committed before the JVM allocation, deliberately — a reservation the allocation would
         * exceed has to be refused before any memory is touched — so the exceptional path has to
         * hand the bytes back. Without it a transient {@code OutOfMemoryError} left the runtime's
         * ceiling permanently inflated, and later, much smaller allocations were refused for space
         * nothing was using.
         *
         * @param releasedBytes the bytes to give back, clamped to what this reservation holds
         */
        public synchronized void shrink(long releasedBytes) {
            if (releasedBytes < 0) {
                throw new IllegalArgumentException("Cannot shrink by a negative number of bytes: " + releasedBytes);
            }
            if (released.get()) {
                return;
            }
            long amount = Math.min(releasedBytes, bytes);
            bytes -= amount;
            accounting.release(amount);
        }
    }
}
