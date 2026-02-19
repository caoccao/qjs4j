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

import static org.assertj.core.api.Assertions.assertThat;

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
        JSValue result = AtomicsObject.add(context, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(5)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(10.0); // returns old value
        assertThat(arr.getBuffer().getBuffer().getInt(0)).isEqualTo(15); // new value is 10 + 5

        // Test add to index 1
        result = AtomicsObject.add(context, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(-3)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(20.0);
        assertThat(arr.getBuffer().getBuffer().getInt(4)).isEqualTo(17);

        // Test edge cases
        JSValue error = AtomicsObject.add(context, null, new JSValue[]{arr}); // too few args
        assertTypeError(error);

        error = AtomicsObject.add(context, null, new JSValue[]{new JSNumber(1), new JSNumber(0), new JSNumber(5)}); // not typed array
        assertTypeError(error);

        error = AtomicsObject.add(context, null, new JSValue[]{arr, new JSNumber(10), new JSNumber(5)}); // out of bounds
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
        JSValue result = AtomicsObject.and(context, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(10)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(15.0); // returns old value
        assertThat(arr.getBuffer().getBuffer().getInt(0)).isEqualTo(10); // new value

        // Test AND with 0: 10 & 0 = 0
        result = AtomicsObject.and(context, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(10.0);
        assertThat(arr.getBuffer().getBuffer().getInt(4)).isEqualTo(0);
    }

    @Test
    public void testCompareExchange() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(8);
        JSInt32Array arr = new JSInt32Array(ab, 0, 2);

        arr.getBuffer().getBuffer().putInt(0, 42);
        arr.getBuffer().getBuffer().putInt(4, 100);

        // Test successful exchange: expected == current
        JSValue result = AtomicsObject.compareExchange(context, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(42), new JSNumber(99)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0); // returns old value
        assertThat(arr.getBuffer().getBuffer().getInt(0)).isEqualTo(99); // new value

        // Test failed exchange: expected != current
        result = AtomicsObject.compareExchange(context, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(50), new JSNumber(200)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(100.0); // returns current value
        assertThat(arr.getBuffer().getBuffer().getInt(4)).isEqualTo(100); // unchanged
    }

    @Test
    public void testExchange() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(8);
        JSInt32Array arr = new JSInt32Array(ab, 0, 2);

        arr.getBuffer().getBuffer().putInt(0, 123);
        arr.getBuffer().getBuffer().putInt(4, 456);

        // Test exchange
        JSValue result = AtomicsObject.exchange(context, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(789)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(123.0); // returns old value
        assertThat(arr.getBuffer().getBuffer().getInt(0)).isEqualTo(789); // new value

        result = AtomicsObject.exchange(context, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(456.0);
        assertThat(arr.getBuffer().getBuffer().getInt(4)).isEqualTo(0);
    }

    @Test
    public void testIsLockFree() {
        // Test various sizes
        JSValue result = AtomicsObject.isLockFree(context, null, new JSValue[]{new JSNumber(1)});
        assertThat(result.isBoolean()).isTrue(); // Implementation dependent

        result = AtomicsObject.isLockFree(context, null, new JSValue[]{new JSNumber(2)});
        assertThat(result.isBoolean()).isTrue();

        result = AtomicsObject.isLockFree(context, null, new JSValue[]{new JSNumber(4)});
        assertThat(result.isBoolean()).isTrue();

        result = AtomicsObject.isLockFree(context, null, new JSValue[]{new JSNumber(8)});
        assertThat(result.isBoolean()).isTrue();

        // Test invalid size
        result = AtomicsObject.isLockFree(context, null, new JSValue[]{new JSNumber(3)});
        assertThat(result.isBooleanFalse()).isTrue(); // Should be false for unsupported sizes
    }

    @Test
    public void testLoad() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(8);
        JSInt32Array arr = new JSInt32Array(ab, 0, 2);

        arr.getBuffer().getBuffer().putInt(0, 111);
        arr.getBuffer().getBuffer().putInt(4, 222);

        // Test load
        JSValue result = AtomicsObject.load(context, null, new JSValue[]{arr, new JSNumber(0)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(111.0);

        result = AtomicsObject.load(context, null, new JSValue[]{arr, new JSNumber(1)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(222.0);
    }

    @Test
    public void testNotify() {
        // Create ArrayBuffer and Int32Array
        JSArrayBuffer ab = new JSArrayBuffer(4);
        JSInt32Array arr = new JSInt32Array(ab, 0, 1);

        // Store initial value
        assertThat(arr.getBuffer().getBuffer()).isNotNull();
        arr.getBuffer().getBuffer().putInt(0, 0);

        // Test 1: Notify with non-shared buffer - should still return 0
        JSValue result = AtomicsObject.notify(context, null, new JSValue[]{arr, new JSNumber(0)});
        assertThat(result.isNumber()).isTrue();
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(0.0);

        // Test 2: Invalid arguments
        JSValue error = AtomicsObject.notify(context, null, new JSValue[]{});
        assertTypeError(error);

        // Test 3: Non-TypedArray argument
        error = AtomicsObject.notify(context, null, new JSValue[]{new JSNumber(1)});
        assertTypeError(error);

        // Test 4: Out of bounds index
        error = AtomicsObject.notify(context, null, new JSValue[]{arr, new JSNumber(10)});
        assertRangeError(error);

        // Test 5: Negative count
        error = AtomicsObject.notify(context, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(-1)});
        assertRangeError(error);
    }

    @Test
    public void testNotifyMultipleWaiters() throws InterruptedException {
        // Create ArrayBuffer and Int32Array
        JSArrayBuffer ab = new JSArrayBuffer(4);
        JSInt32Array arr = new JSInt32Array(ab, 0, 1);

        // Store initial value
        assertThat(arr.getBuffer().getBuffer()).isNotNull();
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
        assertThat(allWaitersStarted.await(2, TimeUnit.SECONDS)).isTrue();

        // Give waiters time to enter wait state
        Thread.sleep(100);

        // Notify all waiters
        JSValue notifyResult = AtomicsObject.notify(context, null, new JSValue[]{
                arr, new JSNumber(0), new JSNumber(waiterCount)
        });

        assertThat(notifyResult.isNumber()).isTrue();
        assertThat(notifyResult.asNumber().map(JSNumber::value).orElseThrow().intValue()).isEqualTo(waiterCount);

        // Wait for all waiters to finish
        assertThat(allWaitersFinished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(successCount.get()).isEqualTo(waiterCount); // All waiters should have been notified
    }

    @Test
    public void testNotifyWithInfinity() throws InterruptedException {
        // Create ArrayBuffer and Int32Array
        JSArrayBuffer ab = new JSArrayBuffer(4);
        JSInt32Array arr = new JSInt32Array(ab, 0, 1);

        assertThat(arr.getBuffer().getBuffer()).isNotNull();
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
        assertThat(waiterStarted.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);

        // Notify with Integer.MAX_VALUE (equivalent to +Infinity in the spec)
        JSValue notifyResult = AtomicsObject.notify(context, null, new JSValue[]{
                arr, new JSNumber(0) // No count parameter means notify all
        });

        assertThat(notifyResult.isNumber()).isTrue();
        assertThat(notifyResult.asNumber().map(JSNumber::value).orElseThrow().intValue()).isEqualTo(1);
        assertThat(waiterFinished.await(2, TimeUnit.SECONDS)).isTrue();
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
        JSValue result = AtomicsObject.or(context, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(5)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(10.0); // returns old value
        assertThat(arr.getBuffer().getBuffer().getInt(0)).isEqualTo(15); // new value

        // Test OR with all bits set: 12 | 3 = 15
        result = AtomicsObject.or(context, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(3)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(12.0);
        assertThat(arr.getBuffer().getBuffer().getInt(4)).isEqualTo(15);
    }

    @Test
    public void testPause() {
        // Atomics.pause() should return undefined
        JSValue result = AtomicsObject.pause(context, null, new JSValue[]{});
        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    public void testStore() {
        // Create SharedArrayBuffer and Int32Array
        JSSharedArrayBuffer ab = new JSSharedArrayBuffer(8);
        JSInt32Array arr = new JSInt32Array(ab, 0, 2);

        arr.getBuffer().getBuffer().putInt(0, 0);
        arr.getBuffer().getBuffer().putInt(4, 0);

        // Test store
        JSValue result = AtomicsObject.store(context, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(999)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(999.0); // returns stored value
        assertThat(arr.getBuffer().getBuffer().getInt(0)).isEqualTo(999);

        result = AtomicsObject.store(context, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(-123)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-123.0);
        assertThat(arr.getBuffer().getBuffer().getInt(4)).isEqualTo(-123);
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
        JSValue result = AtomicsObject.sub(context, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(15)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(50.0); // returns old value
        assertThat(arr.getBuffer().getBuffer().getInt(0)).isEqualTo(35); // new value is 50 - 15

        // Test subtract to index 1
        result = AtomicsObject.sub(context, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(10)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(25.0);
        assertThat(arr.getBuffer().getBuffer().getInt(4)).isEqualTo(15);
    }

    @Test
    public void testWait() {
        // Create ArrayBuffer and Int32Array
        JSArrayBuffer ab = new JSArrayBuffer(4);
        JSInt32Array arr = new JSInt32Array(ab, 0, 1);

        // Store initial value
        assertThat(arr.getBuffer().getBuffer()).isNotNull();
        arr.getBuffer().getBuffer().putInt(0, 42);

        // Test 1: Wait with non-matching value - should return "not-equal"
        JSValue result = AtomicsObject.wait(context, null, new JSValue[]{
                arr, new JSNumber(0), new JSNumber(0), new JSNumber(0)
        });
        assertThat(result.isString()).isTrue();
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("not-equal");

        // Test 2: Wait with matching value and immediate timeout - should return "timed-out"
        result = AtomicsObject.wait(context, null, new JSValue[]{
                arr, new JSNumber(0), new JSNumber(42), new JSNumber(0)
        });
        assertThat(result.isString()).isTrue();
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("timed-out");

        // Test 3: Invalid arguments
        JSValue error = AtomicsObject.wait(context, null, new JSValue[]{arr, new JSNumber(0)});
        assertTypeError(error);

        // Test 4: Non-TypedArray argument
        error = AtomicsObject.wait(context, null, new JSValue[]{new JSNumber(1), new JSNumber(0), new JSNumber(0)});
        assertTypeError(error);

        // Test 5: Out of bounds index
        error = AtomicsObject.wait(context, null, new JSValue[]{arr, new JSNumber(10), new JSNumber(0)});
        assertRangeError(error);
    }

    @Test
    public void testWaitAndNotifyMultithreaded() throws InterruptedException {
        // Create ArrayBuffer and Int32Array
        JSArrayBuffer ab = new JSArrayBuffer(4);
        JSInt32Array arr = new JSInt32Array(ab, 0, 1);

        // Store initial value
        assertThat(arr.getBuffer().getBuffer()).isNotNull();
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
        assertThat(waitStarted.await(1, TimeUnit.SECONDS)).isTrue();

        // Give waiter time to enter wait state
        Thread.sleep(100);

        // Thread 2: Notify
        JSValue notifyResult = AtomicsObject.notify(context, null, new JSValue[]{
                arr, new JSNumber(0), new JSNumber(1)
        });

        // Should notify 1 waiter
        assertThat(notifyResult.isNumber()).isTrue();
        assertThat(notifyResult.asNumber().map(JSNumber::value).orElseThrow().intValue()).isEqualTo(1);

        // Wait for waiter to finish
        assertThat(waitFinished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(waitSuccess.get()).isTrue(); // Wait should have been notified successfully
    }

    @Test
    public void testWaitAsync() {
        // Create ArrayBuffer and Int32Array
        JSArrayBuffer ab = new JSArrayBuffer(4);
        JSInt32Array arr = new JSInt32Array(ab, 0, 1);

        // Store initial value
        assertThat(arr.getBuffer().getBuffer()).isNotNull();
        arr.getBuffer().getBuffer().putInt(0, 42);

        // Test 1: waitAsync with non-matching value - should return {async: false, value: "not-equal"}
        JSValue result = AtomicsObject.waitAsync(context, null, new JSValue[]{
                arr, new JSNumber(0), new JSNumber(0)
        });
        JSObject resultObj = result.asObject().orElseThrow();
        assertThat(resultObj.get("async")).isEqualTo(JSBoolean.FALSE);
        assertThat(((JSString) resultObj.get("value")).value()).isEqualTo("not-equal");

        // Test 2: waitAsync with matching value - should return {async: true, value: Promise}
        result = AtomicsObject.waitAsync(context, null, new JSValue[]{
                arr, new JSNumber(0), new JSNumber(42), new JSNumber(0)
        });
        resultObj = result.asObject().orElseThrow();
        assertThat(resultObj.get("async")).isEqualTo(JSBoolean.TRUE);
        assertThat(resultObj.get("value")).isInstanceOf(JSPromise.class);
        JSPromise waitPromise = (JSPromise) resultObj.get("value");
        long deadline = System.currentTimeMillis() + 500;
        while (waitPromise.getState() == JSPromise.PromiseState.PENDING && System.currentTimeMillis() < deadline) {
            Thread.yield();
        }
        assertThat(waitPromise.getState()).isEqualTo(JSPromise.PromiseState.FULFILLED);
        assertThat(waitPromise.getResult().asString().map(JSString::value).orElseThrow()).isEqualTo("timed-out");

        // Test 3: Invalid arguments
        JSValue error = AtomicsObject.waitAsync(context, null, new JSValue[]{arr, new JSNumber(0)});
        assertTypeError(error);

        // Test 4: Non-TypedArray argument
        error = AtomicsObject.waitAsync(context, null, new JSValue[]{new JSNumber(1), new JSNumber(0), new JSNumber(0)});
        assertTypeError(error);

        // Test 5: Out of bounds index
        error = AtomicsObject.waitAsync(context, null, new JSValue[]{arr, new JSNumber(10), new JSNumber(0)});
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
        JSValue result = AtomicsObject.xor(context, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(10)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(15.0); // returns old value
        assertThat(arr.getBuffer().getBuffer().getInt(0)).isEqualTo(5); // new value

        // Test XOR with same value: 10 ^ 10 = 0
        result = AtomicsObject.xor(context, null, new JSValue[]{arr, new JSNumber(1), new JSNumber(10)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(10.0);
        assertThat(arr.getBuffer().getBuffer().getInt(4)).isEqualTo(0);
    }
}
