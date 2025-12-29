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

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of Atomics object static methods.
 * Based on ES2017 Atomics specification.
 * <p>
 * The Atomics object provides atomic operations on SharedArrayBuffer and TypedArray views.
 * These operations guarantee atomic read-modify-write sequences and memory ordering.
 */
public final class AtomicsObject {

    // Global wait lists indexed by SharedArrayBuffer + index
    private static final Map<String, WaitList> waitLists = new ConcurrentHashMap<>();

    /**
     * Atomics.add(typedArray, index, value)
     * ES2017 24.4.3
     * Atomically adds value to the element at index and returns the old value.
     */
    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.add requires typedArray, index, and value");
        }

        // Validate typed array
        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.add requires a TypedArray");
        }

        // Only Int32Array and Uint32Array support atomic operations
        if (!(typedArray instanceof JSInt32Array) && !(typedArray instanceof JSUint32Array)) {
            return context.throwTypeError("Atomics.add only works on Int32Array or Uint32Array");
        }

        // Check if backed by SharedArrayBuffer
        if (!typedArray.getBuffer().isShared()) {
            return context.throwTypeError("Atomics operations require SharedArrayBuffer");
        }

        int index = (int) ((JSNumber) args[1]).value();
        int value = (int) ((JSNumber) args[2]).value();

        if (index < 0 || index >= typedArray.getLength()) {
            return context.throwRangeError("Index out of bounds");
        }

        // Perform atomic add
        ByteBuffer buffer = typedArray.getBuffer().getBuffer();
        int byteOffset = typedArray.getByteOffset() + (index * 4); // 4 bytes per int32

        synchronized (buffer) {
            int oldValue = buffer.getInt(byteOffset);
            buffer.putInt(byteOffset, oldValue + value);
            return new JSNumber(oldValue);
        }
    }

    /**
     * Atomics.and(typedArray, index, value)
     * ES2017 24.4.4
     * Atomically computes bitwise AND and returns the old value.
     */
    public static JSValue and(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.and requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.and requires a TypedArray");
        }

        if (!(typedArray instanceof JSInt32Array) && !(typedArray instanceof JSUint32Array)) {
            return context.throwTypeError("Atomics.and only works on Int32Array or Uint32Array");
        }

        if (!typedArray.getBuffer().isShared()) {
            return context.throwTypeError("Atomics operations require SharedArrayBuffer");
        }

        int index = (int) ((JSNumber) args[1]).value();
        int value = (int) ((JSNumber) args[2]).value();

        if (index < 0 || index >= typedArray.getLength()) {
            return context.throwRangeError("Index out of bounds");
        }

        ByteBuffer buffer = typedArray.getBuffer().getBuffer();
        int byteOffset = typedArray.getByteOffset() + (index * 4);

        synchronized (buffer) {
            int oldValue = buffer.getInt(byteOffset);
            buffer.putInt(byteOffset, oldValue & value);
            return new JSNumber(oldValue);
        }
    }

    /**
     * Atomics.compareExchange(typedArray, index, expectedValue, replacementValue)
     * ES2017 24.4.5
     * Atomically compares and exchanges if equal, returns the old value.
     */
    public static JSValue compareExchange(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 4) {
            return context.throwTypeError("Atomics.compareExchange requires typedArray, index, expectedValue, and replacementValue");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.compareExchange requires a TypedArray");
        }

        if (!(typedArray instanceof JSInt32Array) && !(typedArray instanceof JSUint32Array)) {
            return context.throwTypeError("Atomics.compareExchange only works on Int32Array or Uint32Array");
        }

        if (!typedArray.getBuffer().isShared()) {
            return context.throwTypeError("Atomics operations require SharedArrayBuffer");
        }

        int index = (int) ((JSNumber) args[1]).value();
        int expectedValue = (int) ((JSNumber) args[2]).value();
        int replacementValue = (int) ((JSNumber) args[3]).value();

        if (index < 0 || index >= typedArray.getLength()) {
            return context.throwRangeError("Index out of bounds");
        }

        ByteBuffer buffer = typedArray.getBuffer().getBuffer();
        int byteOffset = typedArray.getByteOffset() + (index * 4);

        synchronized (buffer) {
            int oldValue = buffer.getInt(byteOffset);
            if (oldValue == expectedValue) {
                buffer.putInt(byteOffset, replacementValue);
            }
            return new JSNumber(oldValue);
        }
    }

    /**
     * Atomics.exchange(typedArray, index, value)
     * ES2017 24.4.6
     * Atomically exchanges the value at index and returns the old value.
     */
    public static JSValue exchange(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.exchange requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.exchange requires a TypedArray");
        }

        if (!(typedArray instanceof JSInt32Array) && !(typedArray instanceof JSUint32Array)) {
            return context.throwTypeError("Atomics.exchange only works on Int32Array or Uint32Array");
        }

        if (!typedArray.getBuffer().isShared()) {
            return context.throwTypeError("Atomics operations require SharedArrayBuffer");
        }

        int index = (int) ((JSNumber) args[1]).value();
        int value = (int) ((JSNumber) args[2]).value();

        if (index < 0 || index >= typedArray.getLength()) {
            return context.throwRangeError("Index out of bounds");
        }

        ByteBuffer buffer = typedArray.getBuffer().getBuffer();
        int byteOffset = typedArray.getByteOffset() + (index * 4);

        synchronized (buffer) {
            int oldValue = buffer.getInt(byteOffset);
            buffer.putInt(byteOffset, value);
            return new JSNumber(oldValue);
        }
    }

    private static String getWaitKey(JSArrayBufferable buffer, int index) {
        return System.identityHashCode(buffer) + ":" + index;
    }

    /**
     * Atomics.isLockFree(size)
     * ES2017 24.4.2
     * Returns whether operations on a given size are lock-free.
     */
    public static JSValue isLockFree(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        int size = (int) ((JSNumber) args[0]).value();

        // In Java, operations on 1, 2, 4 bytes are typically lock-free on modern hardware
        // 8 bytes (long) is also lock-free with AtomicLong
        boolean lockFree = size == 1 || size == 2 || size == 4 || size == 8;
        return JSBoolean.valueOf(lockFree);
    }

    /**
     * Atomics.load(typedArray, index)
     * ES2017 24.4.7
     * Atomically loads and returns the value at index.
     */
    public static JSValue load(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return context.throwTypeError("Atomics.load requires typedArray and index");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.load requires a TypedArray");
        }

        if (!(typedArray instanceof JSInt32Array) && !(typedArray instanceof JSUint32Array)) {
            return context.throwTypeError("Atomics.load only works on Int32Array or Uint32Array");
        }

        if (!typedArray.getBuffer().isShared()) {
            return context.throwTypeError("Atomics operations require SharedArrayBuffer");
        }

        int index = (int) ((JSNumber) args[1]).value();

        if (index < 0 || index >= typedArray.getLength()) {
            return context.throwRangeError("Index out of bounds");
        }

        ByteBuffer buffer = typedArray.getBuffer().getBuffer();
        int byteOffset = typedArray.getByteOffset() + (index * 4);

        synchronized (buffer) {
            int value = buffer.getInt(byteOffset);
            return new JSNumber(value);
        }
    }

    /**
     * Atomics.notify(typedArray, index, count)
     * ES2017 24.4.11
     * Notifies some agents that are sleeping in a wait on the given index.
     * Returns the number of agents that were awoken.
     */
    public static JSValue notify(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 1) {
            return context.throwTypeError("Atomics.notify requires typedArray");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.notify requires a TypedArray");
        }

        if (!(typedArray instanceof JSInt32Array)) {
            return context.throwTypeError("Atomics.notify only works on Int32Array");
        }

        int index = args.length >= 2 ? (int) ((JSNumber) args[1]).value() : 0;
        int count = args.length >= 3 ? (int) ((JSNumber) args[2]).value() : Integer.MAX_VALUE;

        if (index < 0 || index >= typedArray.getLength()) {
            return context.throwRangeError("Index out of bounds");
        }

        if (count < 0) {
            return context.throwRangeError("Count must be non-negative");
        }

        // Get the buffer
        JSArrayBufferable buffer = typedArray.getBuffer();
        String waitKey = getWaitKey(buffer, index);

        WaitList waitList = waitLists.get(waitKey);
        if (waitList == null) {
            return new JSNumber(0);
        }

        int notified = waitList.notifyWaiters(count);
        return new JSNumber(notified);
    }

    /**
     * Atomics.or(typedArray, index, value)
     * ES2017 24.4.8
     * Atomically computes bitwise OR and returns the old value.
     */
    public static JSValue or(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.or requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.or requires a TypedArray");
        }

        if (!(typedArray instanceof JSInt32Array) && !(typedArray instanceof JSUint32Array)) {
            return context.throwTypeError("Atomics.or only works on Int32Array or Uint32Array");
        }

        if (!typedArray.getBuffer().isShared()) {
            return context.throwTypeError("Atomics operations require SharedArrayBuffer");
        }

        int index = (int) ((JSNumber) args[1]).value();
        int value = (int) ((JSNumber) args[2]).value();

        if (index < 0 || index >= typedArray.getLength()) {
            return context.throwRangeError("Index out of bounds");
        }

        ByteBuffer buffer = typedArray.getBuffer().getBuffer();
        int byteOffset = typedArray.getByteOffset() + (index * 4);

        synchronized (buffer) {
            int oldValue = buffer.getInt(byteOffset);
            buffer.putInt(byteOffset, oldValue | value);
            return new JSNumber(oldValue);
        }
    }

    /**
     * Atomics.pause()
     * ES2024 Proposal
     * Provides a hint to the runtime that it may be a good time to yield.
     * Useful in spin-wait loops.
     */
    public static JSValue pause(JSContext context, JSValue thisArg, JSValue[] args) {
        // Java 9+ Thread.onSpinWait() provides a hint to the JVM that we're in a spin-wait loop
        Thread.onSpinWait();
        return JSUndefined.INSTANCE;
    }

    /**
     * Atomics.store(typedArray, index, value)
     * ES2017 24.4.11
     * Atomically stores value at index and returns the value.
     */
    public static JSValue store(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.store requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.store requires a TypedArray");
        }

        if (!(typedArray instanceof JSInt32Array) && !(typedArray instanceof JSUint32Array)) {
            return context.throwTypeError("Atomics.store only works on Int32Array or Uint32Array");
        }

        if (!typedArray.getBuffer().isShared()) {
            return context.throwTypeError("Atomics operations require SharedArrayBuffer");
        }

        int index = (int) ((JSNumber) args[1]).value();
        int value = (int) ((JSNumber) args[2]).value();

        if (index < 0 || index >= typedArray.getLength()) {
            return context.throwRangeError("Index out of bounds");
        }

        ByteBuffer buffer = typedArray.getBuffer().getBuffer();
        int byteOffset = typedArray.getByteOffset() + (index * 4);

        synchronized (buffer) {
            buffer.putInt(byteOffset, value);
            return new JSNumber(value);
        }
    }

    /**
     * Atomics.sub(typedArray, index, value)
     * ES2017 24.4.12
     * Atomically subtracts value from the element at index and returns the old value.
     */
    public static JSValue sub(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.sub requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.sub requires a TypedArray");
        }

        if (!(typedArray instanceof JSInt32Array) && !(typedArray instanceof JSUint32Array)) {
            return context.throwTypeError("Atomics.sub only works on Int32Array or Uint32Array");
        }

        if (!typedArray.getBuffer().isShared()) {
            return context.throwTypeError("Atomics operations require SharedArrayBuffer");
        }

        int index = (int) ((JSNumber) args[1]).value();
        int value = (int) ((JSNumber) args[2]).value();

        if (index < 0 || index >= typedArray.getLength()) {
            return context.throwRangeError("Index out of bounds");
        }

        ByteBuffer buffer = typedArray.getBuffer().getBuffer();
        int byteOffset = typedArray.getByteOffset() + (index * 4);

        synchronized (buffer) {
            int oldValue = buffer.getInt(byteOffset);
            buffer.putInt(byteOffset, oldValue - value);
            return new JSNumber(oldValue);
        }
    }

    /**
     * Atomics.wait(typedArray, index, value, timeout)
     * ES2017 24.4.13
     * Puts the agent to sleep until woken by notify or timeout expires.
     * Returns "ok" if woken by notify, "not-equal" if value doesn't match,
     * or "timed-out" if timeout expired.
     */
    public static JSValue wait(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.wait requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.wait requires a TypedArray");
        }

        if (!(typedArray instanceof JSInt32Array)) {
            return context.throwTypeError("Atomics.wait only works on Int32Array");
        }

        int index = (int) ((JSNumber) args[1]).value();
        int expectedValue = (int) ((JSNumber) args[2]).value();
        long timeout = args.length >= 4 ? (long) ((JSNumber) args[3]).value() : -1;

        if (index < 0 || index >= typedArray.getLength()) {
            return context.throwRangeError("Index out of bounds");
        }

        ByteBuffer byteBuffer = typedArray.getBuffer().getBuffer();
        int byteOffset = typedArray.getByteOffset() + (index * 4);

        // Check if the value matches
        int currentValue;
        synchronized (byteBuffer) {
            currentValue = byteBuffer.getInt(byteOffset);
        }

        if (currentValue != expectedValue) {
            return new JSString("not-equal");
        }

        // Set up wait
        JSArrayBufferable buffer = typedArray.getBuffer();
        String waitKey = getWaitKey(buffer, index);
        WaitList waitList = waitLists.computeIfAbsent(waitKey, k -> new WaitList());

        try {
            String result = waitList.await(timeout);
            return new JSString(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new JSString("timed-out");
        }
    }

    /**
     * Atomics.waitAsync(typedArray, index, value, timeout)
     * ES2024 Proposal
     * Async version of wait that returns a result object with async property.
     * Returns {async: false, value: "not-equal"} if value doesn't match,
     * or {async: true, value: Promise} if waiting.
     */
    public static JSValue waitAsync(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.waitAsync requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.waitAsync requires a TypedArray");
        }

        if (!(typedArray instanceof JSInt32Array)) {
            return context.throwTypeError("Atomics.waitAsync only works on Int32Array");
        }

        int index = (int) ((JSNumber) args[1]).value();
        int expectedValue = (int) ((JSNumber) args[2]).value();
        long timeout = args.length >= 4 ? (long) ((JSNumber) args[3]).value() : -1;

        if (index < 0 || index >= typedArray.getLength()) {
            return context.throwRangeError("Index out of bounds");
        }

        ByteBuffer byteBuffer = typedArray.getBuffer().getBuffer();
        int byteOffset = typedArray.getByteOffset() + (index * 4);

        // Check if the value matches
        int currentValue;
        synchronized (byteBuffer) {
            currentValue = byteBuffer.getInt(byteOffset);
        }

        if (currentValue != expectedValue) {
            // Return {async: false, value: "not-equal"}
            JSObject result = new JSObject();
            result.set("async", JSBoolean.FALSE);
            result.set("value", new JSString("not-equal"));
            return result;
        }

        // Create a promise for async waiting
        JSArrayBufferable buffer = typedArray.getBuffer();
        String waitKey = getWaitKey(buffer, index);
        WaitList waitList = waitLists.computeIfAbsent(waitKey, k -> new WaitList());

        // For now, return a simplified version without actual Promise support
        // A full implementation would require Promise support in the runtime
        JSObject result = new JSObject();
        result.set("async", JSBoolean.TRUE);

        // TODO: Implement Promise-based async waiting when Promise support is available
        // For now, just return a result indicating async waiting was initiated
        result.set("value", new JSString("ok"));

        return result;
    }

    /**
     * Atomics.xor(typedArray, index, value)
     * ES2017 24.4.14
     * Atomically computes bitwise XOR and returns the old value.
     */
    public static JSValue xor(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 3) {
            return context.throwTypeError("Atomics.xor requires typedArray, index, and value");
        }

        if (!(args[0] instanceof JSTypedArray typedArray)) {
            return context.throwTypeError("Atomics.xor requires a TypedArray");
        }

        if (!(typedArray instanceof JSInt32Array) && !(typedArray instanceof JSUint32Array)) {
            return context.throwTypeError("Atomics.xor only works on Int32Array or Uint32Array");
        }

        if (!typedArray.getBuffer().isShared()) {
            return context.throwTypeError("Atomics operations require SharedArrayBuffer");
        }

        int index = (int) ((JSNumber) args[1]).value();
        int value = (int) ((JSNumber) args[2]).value();

        if (index < 0 || index >= typedArray.getLength()) {
            return context.throwRangeError("Index out of bounds");
        }

        ByteBuffer buffer = typedArray.getBuffer().getBuffer();
        int byteOffset = typedArray.getByteOffset() + (index * 4);

        synchronized (buffer) {
            int oldValue = buffer.getInt(byteOffset);
            buffer.putInt(byteOffset, oldValue ^ value);
            return new JSNumber(oldValue);
        }
    }

    /**
     * WaitList manages threads waiting on specific SharedArrayBuffer locations.
     * This is used by Atomics.wait() and Atomics.notify().
     */
    private static class WaitList {
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private int waitingCount = 0;

        public String await(long timeoutMs) throws InterruptedException {
            lock.lock();
            try {
                waitingCount++;
                boolean timedOut = false;

                if (timeoutMs < 0) {
                    // Wait indefinitely
                    condition.await();
                } else {
                    // Wait with timeout
                    timedOut = !condition.await(timeoutMs, TimeUnit.MILLISECONDS);
                }

                waitingCount--;
                return timedOut ? "timed-out" : "ok";
            } finally {
                lock.unlock();
            }
        }

        public int notifyWaiters(int count) {
            lock.lock();
            try {
                int toNotify = (count <= 0) ? waitingCount : Math.min(count, waitingCount);
                for (int i = 0; i < toNotify; i++) {
                    condition.signal();
                }
                return toNotify;
            } finally {
                lock.unlock();
            }
        }
    }
}
