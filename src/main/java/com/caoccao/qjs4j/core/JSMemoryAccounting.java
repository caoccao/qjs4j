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
 * reference to its buffer, and {@link #reserve(Object, long)} drains the collected ones before deciding
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
    private final Set<Reservation> outstandingReservations;
    private final AtomicLong reservedBytes = new AtomicLong();

    /**
     * Create accounting with the given ceiling.
     *
     * @param limit the maximum number of data-block bytes, or a non-positive value for no limit
     */
    JSMemoryAccounting(long limit) {
        this(limit, ConcurrentHashMap.newKeySet());
    }

    /**
     * Create accounting with the given ceiling and registry.
     * <p>
     * The registry is a parameter for one reason: {@link #reserve(Object, long)} charges the counter
     * before the handle that owns the charge exists, and rolls the charge back if registering that
     * handle throws. Nothing the ordinary registry — a {@code ConcurrentHashMap} key set — can do
     * reaches that rollback, so the branch guarding the runtime's ceiling against a permanent leak
     * could not be exercised at all, and would have shipped untested until a change made it
     * reachable. A registry that throws on registration is how a test reaches it.
     * <p>
     * Package-private, and not on any public path: {@link JSRuntime} uses the constructor above, so
     * a runtime always gets the concurrent set. It is typed rather than reflective so that changing
     * this field's representation is a compilation error in the test rather than a runtime one.
     *
     * @param limit                   the maximum number of data-block bytes, or a non-positive value
     *                                for no limit
     * @param outstandingReservations where live reservations are held so they stay reclaimable
     */
    JSMemoryAccounting(long limit, Set<Reservation> outstandingReservations) {
        this.limit = Math.max(UNLIMITED, limit);
        this.outstandingReservations = outstandingReservations;
    }

    /**
     * Charge the counter, or refuse.
     * <p>
     * Private, and the only thing that ever adds to the total. The charge and the reservation that
     * owns it were once two public calls, so a caller could register a handle nothing had paid for
     * and then release it — driving the total negative and, since the limit check reads
     * {@code limit - reserved}, handing out more capacity than the embedder configured.
     *
     * @param bytes the number of bytes to charge; must not be negative
     * @return true when the charge fits under the limit
     */
    private boolean chargeBytes(long bytes) {
        // Reclaim before refusing: a limit that still counts blocks the collector has already taken
        // is not the limit the embedder configured.
        releaseCollectedReservations();
        while (true) {
            long current = reservedBytes.get();
            long headroom = headroomFrom(current);
            if (bytes > headroom) {
                return false;
            }
            if (reservedBytes.compareAndSet(current, current + bytes)) {
                return true;
            }
        }
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
     * The number of bytes that can still be charged on top of a given total.
     * <p>
     * "No limit" is not "no arithmetic". Unlimited mode used plain signed addition, so a total
     * already near {@code Long.MAX_VALUE} wrapped to a negative byte count — a number this class's
     * contract says cannot happen, and one that later release arithmetic then worked from. The
     * headroom is whichever of the configured ceiling and the range of a long comes first.
     * <p>
     * Shared with {@link #wouldExceedLimit(long)} so that the preflight answer and the charge that
     * follows it cannot disagree: the preflight had its own unconditional {@code false} for
     * unlimited mode, so it promised capacity the charge then refused.
     *
     * @param current the total currently reserved
     * @return the remaining capacity, never negative
     */
    private long headroomFrom(long current) {
        return limit == UNLIMITED ? Long.MAX_VALUE - current : limit - current;
    }

    /**
     * Give the counter back some bytes, never taking it below zero.
     * <p>
     * Private, and reached only through a live {@link Reservation}, which releases its own recorded
     * size once. The floor is a second line of defence rather than the first: a negative total is a
     * limit that has stopped being a limit, so it is clamped rather than propagated.
     *
     * @param bytes the number of bytes to give back
     */
    private void releaseBytes(long bytes) {
        if (bytes <= 0) {
            return;
        }
        while (true) {
            long current = reservedBytes.get();
            long updated = Math.max(0L, current - bytes);
            if (reservedBytes.compareAndSet(current, updated)) {
                return;
            }
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
     * Reserve capacity for a data block and bind it to its owner, or refuse.
     * <p>
     * One operation, because the two halves are not independently meaningful: bytes that are
     * charged but not bound to an owner are never given back, and a handle bound to bytes that were
     * never charged gives back capacity that was never taken. Either outcome breaks the limit, and
     * the split version of this let both happen through the public API.
     * <p>
     * Refusal is a normal, catchable outcome; the caller turns it into a {@code RangeError}. It is
     * deliberately not an {@code OutOfMemoryError}: a script that asks for too much has made an
     * ordinary mistake, and the engine stays usable afterwards. Nothing is charged on refusal.
     * <p>
     * The returned handle releases when {@code owner} becomes unreachable, and on demand — so a
     * buffer that is detached or transferred gives its reservation back immediately instead of
     * waiting for a collection. Releasing twice is harmless.
     *
     * @param owner the object whose lifetime the reservation follows
     * @param bytes the number of bytes to reserve; must not be negative
     * @return the reservation, or null when the limit would be exceeded
     */
    public Reservation reserve(Object owner, long bytes) {
        if (owner == null) {
            throw new IllegalArgumentException("A reservation needs an owner to follow");
        }
        if (bytes < 0) {
            throw new IllegalArgumentException("Cannot reserve a negative number of bytes: " + bytes);
        }
        if (!chargeBytes(bytes)) {
            return null;
        }
        try {
            Reservation reservation = new Reservation(this, owner, bytes);
            outstandingReservations.add(reservation);
            return reservation;
        } catch (RuntimeException | Error registrationFailure) {
            // The charge landed before the handle existed, so nothing else can give it back.
            releaseBytes(bytes);
            throw registrationFailure;
        }
    }

    /**
     * Whether a reservation of the given size would be refused.
     * <p>
     * This answers for the counter as it stands, so it reclaims what the collector has taken first
     * and then asks the same question {@link #reserve(Object, long)} asks — otherwise the two
     * disagree, and a caller that checked before reserving is told capacity exists that the very
     * next call refuses. It also rejects a negative size for the same reason: {@code reserve}
     * throws for one, so predicting {@code reserve} means throwing too rather than answering
     * "would fit".
     *
     * @param bytes the byte count; must not be negative
     * @return true when the reservation would exceed the limit
     * @throws IllegalArgumentException when {@code bytes} is negative
     */
    public boolean wouldExceedLimit(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Cannot reserve a negative number of bytes: " + bytes);
        }
        releaseCollectedReservations();
        return bytes > headroomFrom(reservedBytes.get());
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
            if (!accounting.chargeBytes(additionalBytes)) {
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
                accounting.releaseBytes(bytes);
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
            accounting.releaseBytes(amount);
        }
    }
}
