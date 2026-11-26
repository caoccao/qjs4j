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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import com.caoccao.qjs4j.exceptions.JSTypeErrorException;
import com.caoccao.qjs4j.utils.ByteArrayAtomics;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of Atomics object methods. Based on ES2017 Atomics specification.
 * <p>
 * The Atomics object provides atomic operations on SharedArrayBuffer and TypedArray views. These operations guarantee
 * atomic read-modify-write sequences and memory ordering.
 * <p>
 * Each JSRuntime owns an AtomicsObject instance so that wait/notify coordination is scoped to the agent cluster
 * (runtime), not shared globally across the JVM.
 */
public final class AtomicsObject implements AutoCloseable {
    private static final String INTEGER_ARRAY_REQUIREMENT = " only works on Int8Array, Uint8Array, Int16Array, Uint16Array, Int32Array, Uint32Array, BigInt64Array, or BigUint64Array";
    /**
     * How long {@link #close()} waits for the {@code waitAsync} executor to stop.
     */
    private static final long WAIT_EXECUTOR_SHUTDOWN_TIMEOUT_MS = 5_000L;
    /**
     * In-flight {@code Atomics.waitAsync} operations, grouped by the runtime that started them, so closing a runtime
     * can end its own waits and no others. The map holds runtimes weakly: a shared {@code AtomicsObject} outlives any
     * one member of its agent cluster.
     */
    private final Map<JSRuntime, Set<AsyncWaitRegistration>> asyncWaits = Collections
            .synchronizedMap(new WeakHashMap<>());
    // Atomic access to the backing byte[] goes through ByteArrayAtomics, which keeps the lock-free
    // VarHandle path where the JDK offers it and falls back to a striped lock where it does not.
    // Calling byteArrayViewVarHandle directly from here is what broke every 16-, 32- and 64-bit
    // Atomics operation on JDK 25, where those view handles no longer support any atomic mode.
    // Shared thread pool for Atomics.waitAsync() — reuses threads instead of creating one per call.
    // Cached pool: idle threads are terminated after 60s, new threads created on demand.
    private final ExecutorService waitAsyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "qjs4j-atomics-waitAsync");
        t.setDaemon(true);
        return t;
    });
    /**
     * Wait lists, keyed by the identity of the data block and then by the byte offset within it.
     * <p>
     * The key used to be the string {@code System.identityHashCode(bytes) + ":" + offset}. Identity hash codes are not
     * unique, so two live and unrelated {@code SharedArrayBuffer}s could share a wait list and {@code Atomics.notify}
     * on one would wake — and count — waiters on the other. The outer map compares the {@code byte[]} itself, which is
     * identity because arrays do not override {@code equals}; it holds the array weakly so a collected buffer takes its
     * wait lists with it.
     */
    private final Map<byte[], Map<Integer, WaitList>> waitLists = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Atomics.add(typedArray, index, value) ES2017 24.4.3 Atomically adds value to the element at index and returns the
     * old value.
     */
    public JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        return readModifyWrite(context, args, AtomicOperation.ADD);
    }

    /**
     * Atomics.and(typedArray, index, value) ES2017 24.4.4 Atomically computes bitwise AND and returns the old value.
     */
    public JSValue and(JSContext context, JSValue thisArg, JSValue[] args) {
        return readModifyWrite(context, args, AtomicOperation.AND);
    }

    /**
     * End every {@code Atomics.waitAsync} a runtime started, without touching any other runtime's.
     * <p>
     * Called from {@link JSRuntime#close()}. A cancelled wait settles as {@code "timed-out"} if its promise is still
     * worth settling, and is dropped otherwise, so an infinite wait no longer pins a daemon thread, a promise and a
     * closed context for the life of the process.
     *
     * @param runtime
     *            the closing runtime
     * @return how many waits were cancelled
     */
    public int cancelAsyncWaits(JSRuntime runtime) {
        Set<AsyncWaitRegistration> registrations = asyncWaits.remove(runtime);
        if (registrations == null) {
            return 0;
        }
        List<AsyncWaitRegistration> snapshot;
        synchronized (registrations) {
            snapshot = new ArrayList<>(registrations);
        }
        for (AsyncWaitRegistration registration : snapshot) {
            registration.cancel();
        }
        return snapshot.size();
    }

    /**
     * Release this object's own resources: cancel every wait it is holding and stop its executor.
     * <p>
     * Deliberately <em>not</em> called by {@link JSRuntime#close()}, which cancels only its own waits: an
     * {@code AtomicsObject} can be shared by a whole agent cluster through
     * {@link JSRuntimeOptions#setAtomicsObject(AtomicsObject)}, so the first runtime to close is not entitled to shut
     * it down. An embedder that owns one exclusively calls this.
     */
    @Override
    public void close() {
        List<Set<AsyncWaitRegistration>> allRegistrations;
        synchronized (asyncWaits) {
            allRegistrations = new ArrayList<>(asyncWaits.values());
            asyncWaits.clear();
        }
        for (Set<AsyncWaitRegistration> registrations : allRegistrations) {
            List<AsyncWaitRegistration> snapshot;
            synchronized (registrations) {
                snapshot = new ArrayList<>(registrations);
            }
            for (AsyncWaitRegistration registration : snapshot) {
                registration.cancel();
            }
        }
        waitAsyncExecutor.shutdownNow();
        // Every waiter has been cancelled, so the tasks unwind immediately; waiting makes close a
        // point after which the object holds no thread, rather than a request that it stop soon.
        try {
            if (!waitAsyncExecutor.awaitTermination(WAIT_EXECUTOR_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                // The threads are daemons, so a wait that will not unwind cannot outlive the JVM.
                Logger.getLogger(AtomicsObject.class.getName()).log(Level.WARNING,
                        "Atomics waitAsync executor did not stop within " + WAIT_EXECUTOR_SHUTDOWN_TIMEOUT_MS + " ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Atomics.compareExchange(typedArray, index, expectedValue, replacementValue) ES2017 24.4.5 Atomically compares and
     * exchanges if equal, returns the old value.
     */
    public JSValue compareExchange(JSContext context, JSValue thisArg, JSValue[] args) {
        return readModifyWrite(context, args, AtomicOperation.COMPARE_EXCHANGE);
    }

    private JSValue enqueueWait(JSContext context, JSTypedArray typedArray, long index, long timeoutMillis) {
        JSPromise promise = context.createJSPromise();
        JSObject result = context.createJSObject();
        result.set(PropertyKey.ASYNC, JSBoolean.TRUE);
        result.set(PropertyKey.VALUE, promise);
        WaitRegistration waitRegistration = registerWaiter(typedArray, index);
        JSRuntime owningRuntime = context.getRuntime();
        AsyncWaitRegistration registration = new AsyncWaitRegistration(waitRegistration.waitList(),
                waitRegistration.waiter());
        Set<AsyncWaitRegistration> registrations = asyncWaits.computeIfAbsent(owningRuntime,
                key -> Collections.synchronizedSet(new LinkedHashSet<>()));
        registrations.add(registration);
        try {
            waitAsyncExecutor.execute(() -> {
                String waitResult;
                try {
                    waitResult = waitRegistration.waitList().await(waitRegistration.waiter(), timeoutMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    waitResult = "timed-out";
                } finally {
                    registrations.remove(registration);
                    releaseWaitList(waitRegistration);
                }
                // A wait cancelled because its runtime closed must not touch that runtime's
                // promise: the context it belongs to is gone.
                if (!registration.isCancelled()) {
                    // EnqueueResolveInAgentJob: the promise is settled by a job on the context's own
                    // thread, never here. Enqueueing is the one cross-thread operation the engine
                    // supports, so the promise needs no lock of its own.
                    // Closing the context clears its queue, so a job enqueued for a closing runtime
                    // is dropped rather than run.
                    String settledResult = waitResult;
                    context.enqueueMicrotask(() -> promise.fulfill(new JSString(settledResult)));
                }
            });
        } catch (RejectedExecutionException e) {
            registrations.remove(registration);
            waitRegistration.waitList().cancel(waitRegistration.waiter());
            releaseWaitList(waitRegistration);
            promise.fulfill(new JSString("timed-out"));
        }
        return result;
    }

    /**
     * Atomics.exchange(typedArray, index, value) ES2017 24.4.6 Atomically exchanges the value at index and returns the
     * old value.
     */
    public JSValue exchange(JSContext context, JSValue thisArg, JSValue[] args) {
        return readModifyWrite(context, args, AtomicOperation.EXCHANGE);
    }

    /**
     * The wait list for one location, or {@code null} when nobody has ever waited there.
     *
     * @param typedArray
     *            the view
     * @param index
     *            the element index
     * @return the wait list, or {@code null}
     */
    WaitList findWaitList(JSTypedArray typedArray, long index) {
        Map<Integer, WaitList> byOffset = waitLists.get(requireAtomicArray(typedArray, index));
        return byOffset == null ? null : byOffset.get(getWaitOffset(typedArray, index));
    }

    /**
     * How many {@code Atomics.waitAsync} operations a runtime still has in flight.
     *
     * @param runtime
     *            the runtime
     * @return the count
     */
    public int getPendingAsyncWaitCount(JSRuntime runtime) {
        Set<AsyncWaitRegistration> registrations = asyncWaits.get(runtime);
        if (registrations == null) {
            return 0;
        }
        synchronized (registrations) {
            return registrations.size();
        }
    }

    /**
     * Atomics.isLockFree(size) ES2017 24.4.2 Returns whether operations on a given size are lock-free.
     * <p>
     * This is a capability query, so it has to report the path the engine actually took. {@link ByteArrayAtomics}
     * detects that JDK 25 withdrew the atomic access modes from byte-array view {@code VarHandle}s and routes every
     * width through striped locks there — one flag for all widths, because operations of different widths overlap on
     * the same data block and must agree on a protocol. Answering from the width alone told guest code the
     * implementation guarantees lock-free progress when it does not, which is exactly the premise an algorithm chooses
     * itself on.
     */
    public JSValue isLockFree(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        int size;
        try {
            size = (int) JSTypeConversions.toInteger(context, args[0]);
        } catch (JSErrorException e) {
            return context.throwError(e);
        }

        boolean supportedWidth = size == 1 || size == 2 || size == 4 || size == 8;
        return JSBoolean.valueOf(supportedWidth && ByteArrayAtomics.isLockFree());
    }

    /**
     * Whether the {@code waitAsync} executor has stopped.
     *
     * @return true when no wait thread remains
     */
    public boolean isWaitExecutorTerminated() {
        return waitAsyncExecutor.isTerminated();
    }

    /**
     * Atomics.load(typedArray, index) ES2017 24.4.7 Atomically loads and returns the value at index.
     */
    public JSValue load(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return context.throwTypeError("Atomics.load requires typedArray and index");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.load requires a TypedArray");
        }
        if (!typedArray.isAtomicsReadableAndWriteable()) {
            return context.throwTypeError("Atomics.load" + INTEGER_ARRAY_REQUIREMENT);
        }

        try {
            long index = getAtomicIndex(context, typedArray, args[1]);
            byte[] array = requireAtomicArray(typedArray, index);
            int offset = getWaitOffset(typedArray, index);
            long value = switch (typedArray.getBytesPerElement()) {
                case 1 -> ByteArrayAtomics.getVolatileByte(array, offset);
                case 2 -> ByteArrayAtomics.getVolatileShort(array, offset);
                case 4 -> ByteArrayAtomics.getVolatileInt(array, offset);
                case 8 -> ByteArrayAtomics.getVolatileLong(array, offset);
                default -> throw new AssertionError(typedArray.getBytesPerElement());
            };
            return atomicResult(typedArray, value);
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
    }

    /**
     * Atomics.notify(typedArray, index, count) ES2017 24.4.11 Notifies some agents that are sleeping in a wait on the
     * given index. Returns the number of agents that were awoken.
     */
    public JSValue notify(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 1) {
            return context.throwTypeError("Atomics.notify requires typedArray");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.notify requires a TypedArray");
        }

        if (!typedArray.isAtomicsWriteable()) {
            return context.throwTypeError(typedArray.getObjectTag() + " is not an int32 or BigInt64 typed array.");
        }
        try {
            long index = getAtomicIndex(context, typedArray, args.length >= 2 ? args[1] : JSUndefined.INSTANCE);
            double countNumber = args.length >= 3 && !(args[2] instanceof JSUndefined)
                    ? JSTypeConversions.toInteger(context, args[2])
                    : Double.POSITIVE_INFINITY;
            double clampedCount = Math.max(countNumber, 0.0);

            IJSArrayBuffer buffer = typedArray.getBuffer();
            if (!buffer.isShared()) {
                return JSNumber.of(0);
            }

            int count = Double.isInfinite(clampedCount)
                    ? Integer.MAX_VALUE
                    : (int) Math.min(clampedCount, Integer.MAX_VALUE);

            WaitList waitList = findWaitList(typedArray, index);
            if (waitList == null) {
                return JSNumber.of(0);
            }

            int notified = waitList.notifyWaiters(count);
            return JSNumber.of(notified);
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
    }

    /**
     * Atomics.or(typedArray, index, value) ES2017 24.4.8 Atomically computes bitwise OR and returns the old value.
     */
    public JSValue or(JSContext context, JSValue thisArg, JSValue[] args) {
        return readModifyWrite(context, args, AtomicOperation.OR);
    }

    /**
     * Atomics.pause() ES2024 Proposal Provides a hint to the runtime that it may be a good time to yield. Useful in
     * spin-wait loops.
     */
    public JSValue pause(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length > 0) {
            JSValue iterationNumber = args[0];
            if (!(iterationNumber instanceof JSUndefined)) {
                if (iterationNumber instanceof JSNumber jsNumber) {
                    double value = jsNumber.value();
                    if (!Double.isFinite(value) || value != Math.rint(value)) {
                        return context.throwTypeError("not an integral number");
                    }
                } else {
                    return context.throwTypeError("not an integral number");
                }
            }
        }
        // Java 9+ Thread.onSpinWait() provides a hint to the JVM that we're in a spin-wait loop
        Thread.onSpinWait();
        return JSUndefined.INSTANCE;
    }

    /**
     * Take a place in the wait list for one location, creating the list if needed.
     * <p>
     * Looking the list up and joining it are one operation from the caller's point of view: a list that was retired
     * between the two refuses the registration, and the loop then takes the successor that is actually in the lookup.
     * That is what makes a waiter reachable by {@code Atomics.notify} the moment it exists.
     *
     * @param typedArray
     *            the view
     * @param index
     *            the element index
     * @return the caller's registration
     */
    WaitRegistration registerWaiter(JSTypedArray typedArray, long index) {
        byte[] block = requireAtomicArray(typedArray, index);
        int offset = getWaitOffset(typedArray, index);
        while (true) {
            Map<Integer, WaitList> byOffset = waitLists.computeIfAbsent(block, key -> new ConcurrentHashMap<>());
            WaitList waitList = byOffset.computeIfAbsent(offset, key -> new WaitList());
            WaitList.Waiter waiter = waitList.registerIfLive();
            if (waiter != null) {
                return new WaitRegistration(waitList, waiter, block, offset);
            }
        }
    }

    /**
     * Drop a wait list once its last waiter has gone.
     * <p>
     * Emptiness, retirement and removal from the lookup happen under the wait list's own lock, so they are one step as
     * far as {@link #registerWaiter(JSTypedArray, int)} is concerned. Testing emptiness and then removing as two steps
     * left a window in which an agent that had already looked the list up could join it after it stopped being
     * reachable: a later {@code Atomics.notify} would look in the map, find nothing or a fresh list, report zero, and
     * leave that agent blocked — forever, for a wait with no timeout.
     *
     * @param registration
     *            the registration returned by {@link #registerWaiter(JSTypedArray, int)}
     */
    void releaseWaitList(WaitRegistration registration) {
        registration.waitList().retireIfEmpty(() -> {
            Map<Integer, WaitList> byOffset = waitLists.get(registration.block());
            if (byOffset != null) {
                byOffset.remove(registration.offset(), registration.waitList());
            }
        });
    }

    /**
     * Atomics.store(typedArray, index, value) ES2017 24.4.11 Atomically stores value at index and returns the value.
     */
    public JSValue store(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.store requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.store requires a TypedArray");
        }
        if (!typedArray.isAtomicsReadableAndWriteable()) {
            return context.throwTypeError("Atomics.store" + INTEGER_ARRAY_REQUIREMENT);
        }

        try {
            long index = getAtomicIndex(context, typedArray, args[1]);
            if (typedArray instanceof JSBigInt64Array || typedArray instanceof JSBigUint64Array) {
                JSBigInt returnValue = JSTypeConversions.toBigInt(context, args[2]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                long storedValue = returnValue.value().longValue();
                byte[] arr = requireAtomicArray(typedArray, index);
                int byteOffset = getWaitOffset(typedArray, index);
                ByteArrayAtomics.setVolatileLong(arr, byteOffset, storedValue);
                return returnValue;
            }
            double returnValue = JSTypeConversions.toInteger(context, args[2]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (returnValue == 0.0) {
                returnValue = 0.0;
            }
            int int32Value = JSTypeConversions.toInt32(context, JSNumber.of(returnValue));
            byte[] array = requireAtomicArray(typedArray, index);
            int offset = getWaitOffset(typedArray, index);
            switch (typedArray.getBytesPerElement()) {
                case 1 -> ByteArrayAtomics.setVolatileByte(array, offset, (byte) int32Value);
                case 2 -> ByteArrayAtomics.setVolatileShort(array, offset, (short) int32Value);
                case 4 -> ByteArrayAtomics.setVolatileInt(array, offset, int32Value);
                default -> throw new AssertionError(typedArray.getBytesPerElement());
            }
            return JSNumber.of(returnValue);
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
    }

    /**
     * Atomics.sub(typedArray, index, value) ES2017 24.4.12 Atomically subtracts value from the element at index and
     * returns the old value.
     */
    public JSValue sub(JSContext context, JSValue thisArg, JSValue[] args) {
        return readModifyWrite(context, args, AtomicOperation.SUB);
    }

    /**
     * Atomics.wait(typedArray, index, value, timeout) ES2017 24.4.13 Puts the agent to sleep until woken by notify or
     * timeout expires. Returns "ok" if woken by notify, "not-equal" if value doesn't match, or "timed-out" if timeout
     * expired.
     */
    public JSValue wait(JSContext context, JSValue thisArg, JSValue[] args) {
        return waitForValue(context, args, false);
    }

    /**
     * Atomics.waitAsync(typedArray, index, value, timeout) ES2024 Proposal Async version of wait that returns a result
     * object with async property. Returns {async: false, value: "not-equal"} if value doesn't match, or {async: true,
     * value: Promise} if waiting.
     */
    public JSValue waitAsync(JSContext context, JSValue thisArg, JSValue[] args) {
        return waitForValue(context, args, true);
    }

    private JSValue waitForValue(JSContext context, JSValue[] args, boolean async) {
        String method = async ? "Atomics.waitAsync" : "Atomics.wait";
        if (args.length < 3) {
            return context.throwTypeError(method + " requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError(method + " requires a TypedArray");
        }

        try {
            if (!(typedArray instanceof JSInt32Array) && !(typedArray instanceof JSBigInt64Array)) {
                return context.throwTypeError(method + " only works on Int32Array or BigInt64Array");
            }
            if (!typedArray.getBuffer().isShared()) {
                return context.throwTypeError(method + " requires a SharedArrayBuffer");
            }

            long index = getAtomicIndex(context, typedArray, args[1]);
            boolean int32 = typedArray instanceof JSInt32Array;
            long expected = int32
                    ? JSTypeConversions.toInt32(context, args[2])
                    : JSTypeConversions.toBigInt64(context, args[2]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            byte[] bytes = requireAtomicArray(typedArray, index);
            int offset = getWaitOffset(typedArray, index);
            long current = int32
                    ? ByteArrayAtomics.getVolatileInt(bytes, offset)
                    : ByteArrayAtomics.getVolatileLong(bytes, offset);
            if (current != expected) {
                return async ? createWaitAsyncSyncResult(context, "not-equal") : new JSString("not-equal");
            }

            double timeoutDouble = getAtomicsWaitTimeout(context, args, 3);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            if (!async && !context.isWaitable()) {
                return context.throwTypeError("Atomics.wait cannot be called in this context");
            }
            if (timeoutDouble <= 0.0) {
                return async ? createWaitAsyncSyncResult(context, "timed-out") : new JSString("timed-out");
            }

            long timeout = Double.isInfinite(timeoutDouble) ? -1L : (long) timeoutDouble;
            if (async) {
                return enqueueWait(context, typedArray, index, timeout);
            }
            WaitRegistration registration = registerWaiter(typedArray, index);
            String result;
            try {
                result = registration.waitList().await(registration.waiter(), timeout);
            } finally {
                releaseWaitList(registration);
            }
            return new JSString(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new JSString("timed-out");
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
    }

    /**
     * Atomics.xor(typedArray, index, value) ES2017 24.4.14 Atomically computes bitwise XOR and returns the old value.
     */
    public JSValue xor(JSContext context, JSValue thisArg, JSValue[] args) {
        return readModifyWrite(context, args, AtomicOperation.XOR);
    }

    private static JSValue atomicResult(JSTypedArray typedArray, long value) {
        if (typedArray instanceof JSBigUint64Array) {
            return JSBigInt.fromUnsignedLong(value);
        }
        if (typedArray instanceof JSBigInt64Array) {
            return new JSBigInt(BigInteger.valueOf(value));
        }
        if (typedArray instanceof JSUint32Array) {
            return JSNumber.of(Integer.toUnsignedLong((int) value));
        }
        if (typedArray instanceof JSUint16Array) {
            return JSNumber.of(Short.toUnsignedInt((short) value));
        }
        if (typedArray instanceof JSUint8Array) {
            return JSNumber.of(Byte.toUnsignedInt((byte) value));
        }
        return JSNumber.of((int) value);
    }

    private static JSObject createWaitAsyncSyncResult(JSContext context, String value) {
        JSObject result = context.createJSObject();
        result.set(PropertyKey.ASYNC, JSBoolean.FALSE);
        result.set(PropertyKey.VALUE, new JSString(value));
        return result;
    }

    private static long getAtomicIndex(JSContext context, JSTypedArray typedArray, JSValue indexValue) {
        if (typedArray.isOutOfBounds()) {
            throw new JSTypeErrorException("TypedArray buffer is detached or out of bounds");
        }
        long typedArrayLength = typedArray.getLength();
        long indexLong = JSTypeConversions.toIndex(context, indexValue);
        if (context.hasPendingException()) {
            throw new com.caoccao.qjs4j.exceptions.JSException(context.getPendingException());
        }
        if (indexLong >= typedArrayLength) {
            throw new JSRangeErrorException("Index out of bounds");
        }
        return indexLong;
    }

    private static double getAtomicsWaitTimeout(JSContext context, JSValue[] args, int timeoutArgIndex) {
        JSValue timeoutValue = args.length > timeoutArgIndex ? args[timeoutArgIndex] : JSUndefined.INSTANCE;
        double timeoutNumber = JSTypeConversions.toNumber(context, timeoutValue).value();
        if (Double.isNaN(timeoutNumber)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(timeoutNumber, 0.0);
    }

    /**
     * The byte offset of an element within its stable storage page.
     *
     * @param typedArray
     *            the view
     * @param index
     *            the element index
     * @return the byte offset within the page
     */
    private static int getWaitOffset(JSTypedArray typedArray, long index) {
        return typedArray.getBuffer().getBuffer()
                .arrayOffsetAt(typedArray.getByteOffset() + index * typedArray.getBytesPerElement());
    }

    private static JSValue readModifyWrite(JSContext context, JSValue[] args, AtomicOperation operation) {
        boolean compare = operation == AtomicOperation.COMPARE_EXCHANGE;
        if (args.length < (compare ? 4 : 3)) {
            return context.throwTypeError(operation.methodName + (compare
                    ? " requires typedArray, index, expectedValue, and replacementValue"
                    : " requires typedArray, index, and value"));
        }
        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError(operation.methodName + " requires a TypedArray");
        }
        if (!typedArray.isAtomicsReadableAndWriteable()) {
            return context.throwTypeError(operation.methodName + INTEGER_ARRAY_REQUIREMENT);
        }

        try {
            long index = getAtomicIndex(context, typedArray, args[1]);
            int width = typedArray.getBytesPerElement();
            long operand = toAtomicOperand(context, width, args[2]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            long replacement = 0;
            if (compare) {
                replacement = toAtomicOperand(context, width, args[3]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }
            // User code can detach or resize the buffer during either coercion.
            byte[] array = requireAtomicArray(typedArray, index);
            int offset = getWaitOffset(typedArray, index);
            long previous = switch (width) {
                case 1 -> operation.updateByte(array, offset, (byte) operand, (byte) replacement);
                case 2 -> operation.updateShort(array, offset, (short) operand, (short) replacement);
                case 4 -> operation.updateInt(array, offset, (int) operand, (int) replacement);
                case 8 -> operation.updateLong(array, offset, operand, replacement);
                default -> throw new AssertionError(width);
            };
            return atomicResult(typedArray, previous);
        } catch (JSErrorException e) {
            return context.throwError(e);
        }
    }

    private static byte[] requireAtomicArray(JSTypedArray typedArray, long index) {
        // Conversion of the index or value can resize or detach an ordinary ArrayBuffer.
        // An out-of-bounds or detached view has length zero.
        if (index >= typedArray.getLength()) {
            throw new JSTypeErrorException("TypedArray buffer is detached or out of bounds");
        }
        JSByteBuffer byteBuffer = typedArray.getBuffer().getBuffer();
        if (byteBuffer == null) {
            throw new JSTypeErrorException("TypedArray buffer is detached");
        }
        return byteBuffer.arrayAt(typedArray.getByteOffset() + index * typedArray.getBytesPerElement());
    }

    private static long toAtomicOperand(JSContext context, int width, JSValue value) {
        // The only 8-byte integer typed arrays are BigInt64Array and BigUint64Array.
        return width == Long.BYTES
                ? JSTypeConversions.toBigInt64(context, value)
                : JSTypeConversions.toInt32(context, value);
    }

    /**
     * An {@code Atomics.waitAsync} in flight, owned by the runtime whose script started it.
     * <p>
     * Registration is what lets {@link #cancelAsyncWaits(JSRuntime)} end an unbounded wait when its runtime closes.
     * Without it, {@code Atomics.waitAsync(i, 0, 0)} with no timeout held a daemon thread, a promise and that promise's
     * context for the life of the JVM, and closing the runtime changed nothing.
     */

    private static final class AsyncWaitRegistration {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final WaitList.Waiter waiter;
        private final WaitList waitList;

        private AsyncWaitRegistration(WaitList waitList, WaitList.Waiter waiter) {
            this.waitList = waitList;
            this.waiter = waiter;
        }

        private void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                waitList.cancel(waiter);
            }
        }

        private boolean isCancelled() {
            return cancelled.get();
        }
    }

    private enum AtomicOperation {
        ADD("add"), AND("and"), COMPARE_EXCHANGE("compareExchange"), EXCHANGE("exchange"), OR("or"), SUB("sub"), XOR(
                "xor");

        final String methodName;

        AtomicOperation(String name) {
            methodName = "Atomics." + name;
        }

        byte updateByte(byte[] array, int offset, byte operand, byte replacement) {
            return switch (this) {
                case ADD -> ByteArrayAtomics.getAndAddByte(array, offset, operand);
                case SUB -> ByteArrayAtomics.getAndAddByte(array, offset, (byte) -operand);
                case AND -> ByteArrayAtomics.getAndBitwiseAndByte(array, offset, operand);
                case OR -> ByteArrayAtomics.getAndBitwiseOrByte(array, offset, operand);
                case XOR -> ByteArrayAtomics.getAndBitwiseXorByte(array, offset, operand);
                case EXCHANGE -> ByteArrayAtomics.getAndSetByte(array, offset, operand);
                case COMPARE_EXCHANGE -> ByteArrayAtomics.compareAndExchangeByte(array, offset, operand, replacement);
            };
        }

        int updateInt(byte[] array, int offset, int operand, int replacement) {
            return switch (this) {
                case ADD -> ByteArrayAtomics.getAndAddInt(array, offset, operand);
                case SUB -> ByteArrayAtomics.getAndAddInt(array, offset, -operand);
                case AND -> ByteArrayAtomics.getAndBitwiseAndInt(array, offset, operand);
                case OR -> ByteArrayAtomics.getAndBitwiseOrInt(array, offset, operand);
                case XOR -> ByteArrayAtomics.getAndBitwiseXorInt(array, offset, operand);
                case EXCHANGE -> ByteArrayAtomics.getAndSetInt(array, offset, operand);
                case COMPARE_EXCHANGE -> ByteArrayAtomics.compareAndExchangeInt(array, offset, operand, replacement);
            };
        }

        long updateLong(byte[] array, int offset, long operand, long replacement) {
            return switch (this) {
                case ADD -> ByteArrayAtomics.getAndAddLong(array, offset, operand);
                case SUB -> ByteArrayAtomics.getAndAddLong(array, offset, -operand);
                case AND -> ByteArrayAtomics.getAndBitwiseAndLong(array, offset, operand);
                case OR -> ByteArrayAtomics.getAndBitwiseOrLong(array, offset, operand);
                case XOR -> ByteArrayAtomics.getAndBitwiseXorLong(array, offset, operand);
                case EXCHANGE -> ByteArrayAtomics.getAndSetLong(array, offset, operand);
                case COMPARE_EXCHANGE -> ByteArrayAtomics.compareAndExchangeLong(array, offset, operand, replacement);
            };
        }

        short updateShort(byte[] array, int offset, short operand, short replacement) {
            // Short views lack atomic read-modify-write operations. Storage pages are padded to
            // a multiple of four bytes so the CAS can preserve the neighboring halfword.
            int wordOffset = offset & ~3;
            int shift = (offset & 2) << 3;
            int mask = 0xFFFF << shift;
            while (true) {
                int word = ByteArrayAtomics.getVolatileInt(array, wordOffset);
                short previous = (short) (word >>> shift);
                if (this == COMPARE_EXCHANGE && previous != operand) {
                    return previous;
                }
                int next = switch (this) {
                    case ADD -> previous + operand;
                    case SUB -> previous - operand;
                    case AND -> previous & operand;
                    case OR -> previous | operand;
                    case XOR -> previous ^ operand;
                    case EXCHANGE -> operand;
                    case COMPARE_EXCHANGE -> replacement;
                };
                int updated = (word & ~mask) | ((next & 0xFFFF) << shift);
                if (ByteArrayAtomics.compareAndExchangeInt(array, wordOffset, word, updated) == word) {
                    return previous;
                }
            }
        }
    }

    /**
     * The agents waiting on one {@code SharedArrayBuffer} location.
     * <p>
     * Each wait is its own queued node with its own {@link Condition}, so {@code Atomics.notify} wakes exactly the
     * waiters it selected. The previous design counted notifications in a shared {@code pendingSignals} field, which
     * made a notification a token any waiter could spend: after {@code notifyWaiters} signalled an existing waiter and
     * released the lock, a waiter that arrived in between could take the lock first, see the token, consume it and
     * return {@code "ok"} — leaving the agent the notification was actually meant for still blocked.
     */
    static final class WaitList {
        private final Lock lock = new ReentrantLock();
        private boolean retired;
        private final List<Waiter> waiters = new ArrayList<>();

        /**
         * Block on a node until it is notified, cancelled, or the timeout expires.
         *
         * @param waiter
         *            the node from {@link #register()}
         * @param timeoutMs
         *            the timeout, or a negative value to wait forever
         * @return {@code "ok"} or {@code "timed-out"}
         * @throws InterruptedException
         *             if the waiting thread is interrupted
         */
        String await(Waiter waiter, long timeoutMs) throws InterruptedException {
            lock.lock();
            try {
                if (timeoutMs < 0) {
                    while (waiter.state == Waiter.State.WAITING) {
                        waiter.condition.await();
                    }
                } else {
                    long remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
                    while (waiter.state == Waiter.State.WAITING) {
                        if (remainingNanos <= 0) {
                            break;
                        }
                        remainingNanos = waiter.condition.awaitNanos(remainingNanos);
                    }
                }
                return waiter.state == Waiter.State.NOTIFIED ? "ok" : "timed-out";
            } finally {
                waiters.remove(waiter);
                lock.unlock();
            }
        }

        /**
         * Wake a node without notifying it, so its wait ends as a timeout. Used when the owning runtime closes.
         *
         * @param waiter
         *            the node to cancel
         */
        void cancel(Waiter waiter) {
            lock.lock();
            try {
                if (waiter.state == Waiter.State.WAITING) {
                    waiter.state = Waiter.State.CANCELLED;
                    waiter.condition.signal();
                }
                waiters.remove(waiter);
            } finally {
                lock.unlock();
            }
        }

        /**
         * Whether nobody is waiting here any more.
         *
         * @return true when the queue is empty
         */
        boolean isEmpty() {
            lock.lock();
            try {
                return waiters.isEmpty();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Wake the given number of waiters, oldest first.
         *
         * @param count
         *            how many to wake
         * @return how many were woken
         */
        int notifyWaiters(int count) {
            lock.lock();
            try {
                int notified = 0;
                for (Waiter waiter : waiters) {
                    if (notified >= count) {
                        break;
                    }
                    if (waiter.state == Waiter.State.WAITING) {
                        waiter.state = Waiter.State.NOTIFIED;
                        waiter.condition.signal();
                        notified++;
                    }
                }
                return notified;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Join the queue, unless this list has already been retired from the lookup.
         *
         * @return the caller's node, or {@code null} when the list is no longer reachable and the caller must take the
         *         one that replaced it
         */
        Waiter registerIfLive() {
            lock.lock();
            try {
                if (retired) {
                    return null;
                }
                Waiter waiter = new Waiter(lock.newCondition());
                waiters.add(waiter);
                return waiter;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Retire this list and remove it from the lookup, but only while it is still empty.
         * <p>
         * The removal runs under this list's lock so that retirement and unreachability are one step: a registration
         * that arrives in between blocks on the lock and is then refused, rather than joining a list nothing can find
         * any more.
         *
         * @param removeFromLookup
         *            removes this list from the wait-list map
         */
        void retireIfEmpty(Runnable removeFromLookup) {
            lock.lock();
            try {
                if (retired || !waiters.isEmpty()) {
                    return;
                }
                retired = true;
                removeFromLookup.run();
            } finally {
                lock.unlock();
            }
        }

        /**
         * One agent's place in the queue.
         */
        static final class Waiter {
            private final Condition condition;
            private State state = State.WAITING;

            private Waiter(Condition condition) {
                this.condition = condition;
            }

            private enum State {
                CANCELLED, NOTIFIED, WAITING
            }
        }
    }

    /**
     * One agent's place in a wait list, together with what it takes to drop that list again.
     *
     * @param waitList
     *            the list the waiter joined
     * @param waiter
     *            the waiter's node
     * @param block
     *            the data block the list belongs to
     * @param offset
     *            the byte offset within that block
     */
    record WaitRegistration(WaitList waitList, WaitList.Waiter waiter, byte[] block, int offset) {
    }
}
