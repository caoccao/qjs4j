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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AtomicsObject synchronization methods.
 */
public class AtomicsObjectTest extends BaseTest {

    @Test
    public void testAdd() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(16); // 4 int32 values
        JSInt32Array arr = new JSInt32Array(ab, 0, 4);

        // Initialize values
        arr.getBuffer().getBuffer().putInt(0, 10);
        arr.getBuffer().getBuffer().putInt(4, 20);

        // Test normal case: add to index 0
        JSValue result = AtomicsObject.add(ctx, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(5)});
        assertEquals(10.0, result.asNumber().map(JSNumber::value).orElse(0D)); // returns old value
        assertEquals(15, arr.getBuffer().getBuffer().getInt(0)); // new value is 10 + 5

        // Test add to index 1
        result = AtomicsObject.add(ctx, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(-3)});
        assertEquals(20.0, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(17, arr.getBuffer().getBuffer().getInt(4));

        // Test edge cases
        JSValue error = AtomicsObject.add(ctx, null, new JSValue[]{arr}); // too few args
        assertTypeError(error);

        error = AtomicsObject.add(ctx, null, new JSValue[]{new JSNumber(1), new JSNumber(0), new JSNumber(5)}); // not typed array
        assertTypeError(error);

        error = AtomicsObject.add(ctx, null, new JSValue[]{arr, new JSNumber(10), new JSNumber(5)}); // out of bounds
        assertRangeError(error);
    }

    @Test
    public void testAnd() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(8);
        JSInt32Array arr = new JSInt32Array(ab, 0, 2);

        // Initialize values: 0b1111 (15) and 0b1010 (10)
        arr.getBuffer().getBuffer().putInt(0, 15);
        arr.getBuffer().getBuffer().putInt(4, 10);

        // Test AND operation: 15 & 10 = 10
        JSValue result = AtomicsObject.and(ctx, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(10)});
        assertEquals(15.0, result.asNumber().map(JSNumber::value).orElse(0D)); // returns old value
        assertEquals(10, arr.getBuffer().getBuffer().getInt(0)); // new value

        // Test AND with 0: 10 & 0 = 0
        result = AtomicsObject.and(ctx, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(0)});
        assertEquals(10.0, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(0, arr.getBuffer().getBuffer().getInt(4));
    }

    @Test
    public void testCompareExchange() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(8);
        JSInt32Array arr = new JSInt32Array(ab, 0, 2);

        arr.getBuffer().getBuffer().putInt(0, 42);
        arr.getBuffer().getBuffer().putInt(4, 100);

        // Test successful exchange: expected == current
        JSValue result = AtomicsObject.compareExchange(ctx, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(42), new JSNumber(99)});
        assertEquals(42.0, result.asNumber().map(JSNumber::value).orElse(0D)); // returns old value
        assertEquals(99, arr.getBuffer().getBuffer().getInt(0)); // new value

        // Test failed exchange: expected != current
        result = AtomicsObject.compareExchange(ctx, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(50), new JSNumber(200)});
        assertEquals(100.0, result.asNumber().map(JSNumber::value).orElse(0D)); // returns current value
        assertEquals(100, arr.getBuffer().getBuffer().getInt(4)); // unchanged
    }

    @Test
    public void testExchange() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(8);
        JSInt32Array arr = new JSInt32Array(ab, 0, 2);

        arr.getBuffer().getBuffer().putInt(0, 123);
        arr.getBuffer().getBuffer().putInt(4, 456);

        // Test exchange
        JSValue result = AtomicsObject.exchange(ctx, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(789)});
        assertEquals(123.0, result.asNumber().map(JSNumber::value).orElse(0D)); // returns old value
        assertEquals(789, arr.getBuffer().getBuffer().getInt(0)); // new value

        result = AtomicsObject.exchange(ctx, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(0)});
        assertEquals(456.0, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(0, arr.getBuffer().getBuffer().getInt(4));
    }

    @Test
    public void testIsLockFree() {
        // Test various sizes
        JSValue result = AtomicsObject.isLockFree(ctx, null, new JSValue[]{new JSNumber(1)});
        assertTrue(result.isBoolean()); // Implementation dependent

        result = AtomicsObject.isLockFree(ctx, null, new JSValue[]{new JSNumber(2)});
        assertTrue(result.isBoolean());

        result = AtomicsObject.isLockFree(ctx, null, new JSValue[]{new JSNumber(4)});
        assertTrue(result.isBoolean());

        result = AtomicsObject.isLockFree(ctx, null, new JSValue[]{new JSNumber(8)});
        assertTrue(result.isBoolean());

        // Test invalid size
        result = AtomicsObject.isLockFree(ctx, null, new JSValue[]{new JSNumber(3)});
        assertTrue(result.isBooleanFalse()); // Should be false for unsupported sizes
    }

    @Test
    public void testLoad() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(8);
        JSInt32Array arr = new JSInt32Array(ab, 0, 2);

        arr.getBuffer().getBuffer().putInt(0, 111);
        arr.getBuffer().getBuffer().putInt(4, 222);

        // Test load
        JSValue result = AtomicsObject.load(ctx, null, new JSValue[]{arr, new JSNumber(0)});
        assertEquals(111.0, result.asNumber().map(JSNumber::value).orElse(0D));

        result = AtomicsObject.load(ctx, null, new JSValue[]{arr, new JSNumber(1)});
        assertEquals(222.0, result.asNumber().map(JSNumber::value).orElse(0D));
    }

    @Test
    public void testNotify() {
        // Create ArrayBuffer and Int32Array
        JSArrayBuffer ab = new JSArrayBuffer(4);
        JSInt32Array arr = new JSInt32Array(ab, 0, 1);

        // Store initial value
        assertNotNull(arr.getBuffer().getBuffer());
        arr.getBuffer().getBuffer().putInt(0, 0);

        // Test 1: Notify with non-shared buffer - should still return 0
        JSValue result = AtomicsObject.notify(ctx, null, new JSValue[]{arr, new JSNumber(0)});
        assertTrue(result.isNumber());
        assertEquals(0, result.asNumber().map(JSNumber::value).orElse(-1D));

        // Test 2: Invalid arguments
        JSValue error = AtomicsObject.notify(ctx, null, new JSValue[]{});
        assertTypeError(error);

        // Test 3: Non-TypedArray argument
        error = AtomicsObject.notify(ctx, null, new JSValue[]{new JSNumber(1)});
        assertTypeError(error);

        // Test 4: Out of bounds index
        error = AtomicsObject.notify(ctx, null, new JSValue[]{arr, new JSNumber(10)});
        assertRangeError(error);

        // Test 5: Negative count
        error = AtomicsObject.notify(ctx, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(-1)});
        assertRangeError(error);
    }

    @Test
    public void testNotifyMultipleWaiters() throws InterruptedException {
        // Create ArrayBuffer and Int32Array
        JSArrayBuffer ab = new JSArrayBuffer(4);
        JSInt32Array arr = new JSInt32Array(ab, 0, 1);

        // Store initial value
        assertNotNull(arr.getBuffer().getBuffer());
        arr.getBuffer().getBuffer().putInt(0, 200);

        int waiterCount = 3;
        CountDownLatch allWaitersStarted = new CountDownLatch(waiterCount);
        CountDownLatch allWaitersFinished = new CountDownLatch(waiterCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Create multiple waiters
        for (int i = 0; i < waiterCount; i++) {
            Thread waiter = new Thread(() -> {
                JSContext waiterCtx = new JSContext(new JSRuntime());
                allWaitersStarted.countDown();
                JSValue result = AtomicsObject.wait(waiterCtx, null, new JSValue[]{
                        arr, new JSNumber(0), new JSNumber(200), new JSNumber(5000)
                });

                if (result instanceof JSString && "ok".equals(((JSString) result).value())) {
                    successCount.incrementAndGet();
                }
                allWaitersFinished.countDown();
                waiterCtx.close();
            });
            waiter.start();
        }

        // Wait for all waiters to start
        assertTrue(allWaitersStarted.await(2, TimeUnit.SECONDS));

        // Give waiters time to enter wait state
        Thread.sleep(100);

        // Notify all waiters
        JSValue notifyResult = AtomicsObject.notify(ctx, null, new JSValue[]{
                arr, new JSNumber(0), new JSNumber(waiterCount)
        });

        assertTrue(notifyResult.isNumber());
        assertEquals(waiterCount, notifyResult.asNumber().map(JSNumber::value).orElse(-1D).intValue());

        // Wait for all waiters to finish
        assertTrue(allWaitersFinished.await(2, TimeUnit.SECONDS));
        assertEquals(waiterCount, successCount.get(), "All waiters should have been notified");
    }

    @Test
    public void testNotifyWithInfinity() throws InterruptedException {
        // Create ArrayBuffer and Int32Array
        JSArrayBuffer ab = new JSArrayBuffer(4);
        JSInt32Array arr = new JSInt32Array(ab, 0, 1);

        assertNotNull(arr.getBuffer().getBuffer());
        arr.getBuffer().getBuffer().putInt(0, 300);

        CountDownLatch waiterStarted = new CountDownLatch(1);
        CountDownLatch waiterFinished = new CountDownLatch(1);

        Thread waiter = new Thread(() -> {
            JSContext waiterCtx = new JSContext(new JSRuntime());
            waiterStarted.countDown();
            AtomicsObject.wait(waiterCtx, null, new JSValue[]{
                    arr, new JSNumber(0), new JSNumber(300), new JSNumber(5000)
            });
            waiterFinished.countDown();
            waiterCtx.close();
        });

        waiter.start();
        assertTrue(waiterStarted.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);

        // Notify with Integer.MAX_VALUE (equivalent to +Infinity in the spec)
        JSValue notifyResult = AtomicsObject.notify(ctx, null, new JSValue[]{
                arr, new JSNumber(0) // No count parameter means notify all
        });

        assertTrue(notifyResult.isNumber());
        assertEquals(1, notifyResult.asNumber().map(JSNumber::value).orElse(0D).intValue());
        assertTrue(waiterFinished.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void testOr() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(8);
        JSInt32Array arr = new JSInt32Array(ab, 0, 2);

        // Initialize values: 0b1010 (10) and 0b1100 (12)
        arr.getBuffer().getBuffer().putInt(0, 10);
        arr.getBuffer().getBuffer().putInt(4, 12);

        // Test OR operation: 10 | 5 = 15 (0b1010 | 0b0101 = 0b1111)
        JSValue result = AtomicsObject.or(ctx, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(5)});
        assertEquals(10.0, result.asNumber().map(JSNumber::value).orElse(0D)); // returns old value
        assertEquals(15, arr.getBuffer().getBuffer().getInt(0)); // new value

        // Test OR with all bits set: 12 | 3 = 15
        result = AtomicsObject.or(ctx, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(3)});
        assertEquals(12.0, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(15, arr.getBuffer().getBuffer().getInt(4));
    }

    @Test
    public void testPause() {
        // Atomics.pause() should return undefined
        JSValue result = AtomicsObject.pause(ctx, null, new JSValue[]{});
        assertTrue(result.isUndefined());
    }

    @Test
    public void testStore() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(8);
        JSInt32Array arr = new JSInt32Array(ab, 0, 2);

        arr.getBuffer().getBuffer().putInt(0, 0);
        arr.getBuffer().getBuffer().putInt(4, 0);

        // Test store
        JSValue result = AtomicsObject.store(ctx, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(999)});
        assertEquals(999.0, result.asNumber().map(JSNumber::value).orElse(0D)); // returns stored value
        assertEquals(999, arr.getBuffer().getBuffer().getInt(0));

        result = AtomicsObject.store(ctx, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(-123)});
        assertEquals(-123.0, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(-123, arr.getBuffer().getBuffer().getInt(4));
    }

    @Test
    public void testSub() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(16);
        JSInt32Array arr = new JSInt32Array(ab, 0, 4);

        // Initialize values
        arr.getBuffer().getBuffer().putInt(0, 50);
        arr.getBuffer().getBuffer().putInt(4, 25);

        // Test normal case: subtract from index 0
        JSValue result = AtomicsObject.sub(ctx, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(15)});
        assertEquals(50.0, result.asNumber().map(JSNumber::value).orElse(0D)); // returns old value
        assertEquals(35, arr.getBuffer().getBuffer().getInt(0)); // new value is 50 - 15

        // Test subtract to index 1
        result = AtomicsObject.sub(ctx, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(10)});
        assertEquals(25.0, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(15, arr.getBuffer().getBuffer().getInt(4));
    }

    @Test
    public void testWait() {
        // Create ArrayBuffer and Int32Array
        JSArrayBuffer ab = new JSArrayBuffer(4);
        JSInt32Array arr = new JSInt32Array(ab, 0, 1);

        // Store initial value
        assertNotNull(arr.getBuffer().getBuffer());
        arr.getBuffer().getBuffer().putInt(0, 42);

        // Test 1: Wait with non-matching value - should return "not-equal"
        JSValue result = AtomicsObject.wait(ctx, null, new JSValue[]{
                arr, new JSNumber(0), new JSNumber(0), new JSNumber(0)
        });
        assertTrue(result.isString());
        assertEquals("not-equal", result.asString().map(JSString::value).orElse(""));

        // Test 2: Wait with matching value and immediate timeout - should return "timed-out"
        result = AtomicsObject.wait(ctx, null, new JSValue[]{
                arr, new JSNumber(0), new JSNumber(42), new JSNumber(0)
        });
        assertTrue(result.isString());
        assertEquals("timed-out", result.asString().map(JSString::value).orElse(""));

        // Test 3: Invalid arguments
        JSValue error = AtomicsObject.wait(ctx, null, new JSValue[]{arr, new JSNumber(0)});
        assertTypeError(error);

        // Test 4: Non-TypedArray argument
        error = AtomicsObject.wait(ctx, null, new JSValue[]{new JSNumber(1), new JSNumber(0), new JSNumber(0)});
        assertTypeError(error);

        // Test 5: Out of bounds index
        error = AtomicsObject.wait(ctx, null, new JSValue[]{arr, new JSNumber(10), new JSNumber(0)});
        assertRangeError(error);
    }

    @Test
    public void testWaitAndNotifyMultithreaded() throws InterruptedException {
        // Create ArrayBuffer and Int32Array
        JSArrayBuffer ab = new JSArrayBuffer(4);
        JSInt32Array arr = new JSInt32Array(ab, 0, 1);

        // Store initial value
        assertNotNull(arr.getBuffer().getBuffer());
        arr.getBuffer().getBuffer().putInt(0, 100);

        CountDownLatch waitStarted = new CountDownLatch(1);
        CountDownLatch waitFinished = new CountDownLatch(1);
        AtomicBoolean waitSuccess = new AtomicBoolean(false);

        // Thread 1: Wait
        Thread waiter = new Thread(() -> {
            JSContext waiterCtx = new JSContext(new JSRuntime());
            waitStarted.countDown();
            JSValue result = AtomicsObject.wait(waiterCtx, null, new JSValue[]{
                    arr, new JSNumber(0), new JSNumber(100), new JSNumber(5000) // 5 second timeout
            });

            if (result instanceof JSString && "ok".equals(((JSString) result).value())) {
                waitSuccess.set(true);
            }
            waitFinished.countDown();
            waiterCtx.close();
        });

        waiter.start();

        // Wait for waiter to start
        assertTrue(waitStarted.await(1, TimeUnit.SECONDS));

        // Give waiter time to enter wait state
        Thread.sleep(100);

        // Thread 2: Notify
        JSValue notifyResult = AtomicsObject.notify(ctx, null, new JSValue[]{
                arr, new JSNumber(0), new JSNumber(1)
        });

        // Should notify 1 waiter
        assertTrue(notifyResult.isNumber());
        assertEquals(1, notifyResult.asNumber().map(JSNumber::value).orElse(0D).intValue());

        // Wait for waiter to finish
        assertTrue(waitFinished.await(2, TimeUnit.SECONDS));
        assertTrue(waitSuccess.get(), "Wait should have been notified successfully");
    }

    @Test
    public void testWaitAsync() {
        // Create ArrayBuffer and Int32Array
        JSArrayBuffer ab = new JSArrayBuffer(4);
        JSInt32Array arr = new JSInt32Array(ab, 0, 1);

        // Store initial value
        assertNotNull(arr.getBuffer().getBuffer());
        arr.getBuffer().getBuffer().putInt(0, 42);

        // Test 1: waitAsync with non-matching value - should return {async: false, value: "not-equal"}
        JSValue result = AtomicsObject.waitAsync(ctx, null, new JSValue[]{
                arr, new JSNumber(0), new JSNumber(0)
        });
        JSObject resultObj = result.asObject().orElse(null);
        assertNotNull(resultObj);
        assertEquals(JSBoolean.FALSE, resultObj.get("async"));
        assertEquals("not-equal", ((JSString) resultObj.get("value")).value());

        // Test 2: waitAsync with matching value - should return {async: true, value: ...}
        result = AtomicsObject.waitAsync(ctx, null, new JSValue[]{
                arr, new JSNumber(0), new JSNumber(42)
        });
        resultObj = result.asObject().orElse(null);
        assertNotNull(resultObj);
        assertEquals(JSBoolean.TRUE, resultObj.get("async"));

        // Test 3: Invalid arguments
        JSValue error = AtomicsObject.waitAsync(ctx, null, new JSValue[]{arr, new JSNumber(0)});
        assertTypeError(error);

        // Test 4: Non-TypedArray argument
        error = AtomicsObject.waitAsync(ctx, null, new JSValue[]{new JSNumber(1), new JSNumber(0), new JSNumber(0)});
        assertTypeError(error);

        // Test 5: Out of bounds index
        error = AtomicsObject.waitAsync(ctx, null, new JSValue[]{arr, new JSNumber(10), new JSNumber(0)});
        assertRangeError(error);
    }

    @Test
    public void testXor() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(8);
        JSInt32Array arr = new JSInt32Array(ab, 0, 2);

        // Initialize values: 0b1111 (15) and 0b1010 (10)
        arr.getBuffer().getBuffer().putInt(0, 15);
        arr.getBuffer().getBuffer().putInt(4, 10);

        // Test XOR operation: 15 ^ 10 = 5 (0b1111 ^ 0b1010 = 0b0101)
        JSValue result = AtomicsObject.xor(ctx, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(10)});
        assertEquals(15.0, result.asNumber().map(JSNumber::value).orElse(0D)); // returns old value
        assertEquals(5, arr.getBuffer().getBuffer().getInt(0)); // new value

        // Test XOR with same value: 10 ^ 10 = 0
        result = AtomicsObject.xor(ctx, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(10)});
        assertEquals(10.0, result.asNumber().map(JSNumber::value).orElse(0D));
        assertEquals(0, arr.getBuffer().getBuffer().getInt(4));
    }
}
