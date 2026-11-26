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

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.utils.ByteArrayAtomics;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AtomicsObject synchronization methods.
 */
public class AtomicsObjectTest extends BaseJavetTest {

    private void assertAtomicUpdate(JSNativeCallback operation, JSInt32Array array, int index, int expectedResult,
            int expectedStored, int... operands) {
        JSValue[] args = new JSValue[2 + operands.length];
        args[0] = array;
        args[1] = JSNumber.of(index);
        for (int i = 0; i < operands.length; i++) {
            args[i + 2] = JSNumber.of(operands[i]);
        }
        assertNumberValue(operation.call(context, null, args), expectedResult);
        assertThat(array.getBuffer().getBuffer().getInt(index * Integer.BYTES)).isEqualTo(expectedStored);
    }

    private void assertWaitArgumentErrors(JSNativeCallback waitOperation, JSInt32Array arr) {
        // Test 3: Invalid arguments
        JSValue error = waitOperation.call(context, null, new JSValue[]{arr, new JSNumber(0)});
        assertTypeError(error);
        context.clearPendingException();

        // Test 4: Non-TypedArray argument
        error = waitOperation.call(context, null, numberArgs(1, 0, 0));
        assertTypeError(error);
        context.clearPendingException();

        // Test 5: Out of bounds index
        error = waitOperation.call(context, null, new JSValue[]{arr, new JSNumber(10), new JSNumber(0)});
        assertRangeError(error);
        context.clearPendingException();
    }

    private AtomicsObject atomics() {
        return context.getRuntime().getAtomicsObject();
    }

    private JSInt32Array sharedInts(int length, int... initialValues) {
        JSSharedArrayBuffer buffer = new JSSharedArrayBuffer(context, length * Integer.BYTES);
        JSInt32Array array = new JSInt32Array(context, buffer, 0, length);
        assertThat(array.getBuffer().getBuffer()).isNotNull();
        for (int index = 0; index < initialValues.length; index++) {
            array.getBuffer().getBuffer().putInt(index * Integer.BYTES, initialValues[index]);
        }
        return array;
    }

    @Test
    public void testAdd() {
        JSInt32Array arr = sharedInts(4, 10, 20);

        // Test normal case: add to index 0
        assertAtomicUpdate(atomics()::add, arr, 0, 10, 15, 5);

        // Test add to index 1
        assertAtomicUpdate(atomics()::add, arr, 1, 20, 17, -3);

        // Test edge cases
        JSValue error = atomics().add(context, null, new JSValue[]{arr}); // too few args
        assertTypeError(error);
        context.clearPendingException();

        error = atomics().add(context, null, numberArgs(1, 0, 5)); // not
                                                                   // typed
                                                                   // array
        assertTypeError(error);
        context.clearPendingException();

        error = atomics().add(context, null, new JSValue[]{arr, new JSNumber(10), new JSNumber(5)}); // out of bounds
        assertRangeError(error);
        context.clearPendingException();
    }

    @Test
    public void testAnd() {
        JSInt32Array arr = sharedInts(2, 15, 10);

        // Test AND operation: 15 & 10 = 10
        assertAtomicUpdate(atomics()::and, arr, 0, 15, 10, 10);

        // Test AND with 0: 10 & 0 = 0
        assertAtomicUpdate(atomics()::and, arr, 1, 10, 0, 0);
    }

    @Test
    public void testCompareExchange() {
        JSInt32Array arr = sharedInts(2, 42, 100);

        // Test successful exchange: expected == current
        assertAtomicUpdate(atomics()::compareExchange, arr, 0, 42, 99, 42, 99);

        // Test failed exchange: expected != current
        assertAtomicUpdate(atomics()::compareExchange, arr, 1, 100, 100, 50, 200);
    }

    @Test
    public void testExchange() {
        JSInt32Array arr = sharedInts(2, 123, 456);

        // Test exchange
        assertAtomicUpdate(atomics()::exchange, arr, 0, 123, 789, 789);

        assertAtomicUpdate(atomics()::exchange, arr, 1, 456, 0, 0);
    }

    @Test
    public void testIntegerOperationsAcrossElementWidths() {
        assertStringWithJavet("""
                (() => {
                    const results = [];
                    for (const Type of [Int8Array, Uint8Array, Int16Array, Uint16Array,
                                        Int32Array, Uint32Array, BigInt64Array, BigUint64Array]) {
                        const array = new Type(new SharedArrayBuffer(4 * Type.BYTES_PER_ELEMENT));
                        const value = Type === BigInt64Array || Type === BigUint64Array ? BigInt : Number;
                        array[0] = value(42);
                        array[2] = value(73);
                        results.push(Atomics.store(array, 1, value(-1)));
                        for (const op of ["add", "sub", "xor", "and", "or", "exchange"]) {
                            results.push(Atomics[op](array, 1, value(3)), Atomics.load(array, 1));
                        }
                        results.push(Atomics.compareExchange(array, 1, value(0), value(99)), array[1]);
                        results.push(Atomics.compareExchange(array, 1, value(3), value(-2)), array[1]);
                        results.push(array[0], array[2]);
                    }
                    return results.join(",");
                })()
                """);
    }

    @Test
    public void testIsLockFree() {
        // Test various sizes
        JSValue result = atomics().isLockFree(context, null, numberArgs(1));
        assertThat(result.isBoolean()).isTrue(); // Implementation dependent

        result = atomics().isLockFree(context, null, numberArgs(2));
        assertThat(result.isBoolean()).isTrue();

        result = atomics().isLockFree(context, null, numberArgs(4));
        assertThat(result.isBoolean()).isTrue();

        result = atomics().isLockFree(context, null, numberArgs(8));
        assertThat(result.isBoolean()).isTrue();

        // Test invalid size
        result = atomics().isLockFree(context, null, numberArgs(3));
        assertThat(result.isBooleanFalse()).isTrue(); // Should be false for unsupported sizes
    }

    @Test
    public void testIsLockFreeReportsThePathTheEngineActuallyTook() {
        // A capability query has to describe the implementation, not the width. JDK 25 withdrew the
        // atomic access modes from byte-array view VarHandles, so the engine serialises every width
        // on a lock there — while Atomics.isLockFree(4) still answered true, which is precisely the
        // premise guest code picks a lock-free algorithm on.
        boolean lockFree = ByteArrayAtomics.isLockFree();
        for (int size : new int[]{1, 2, 4, 8}) {
            assertThat(atomics().isLockFree(context, null, numberArgs(size))).as("size " + size)
                    .isEqualTo(JSBoolean.valueOf(lockFree));
            assertThat(context.eval("Atomics.isLockFree(" + size + ")").toString()).as("guest-visible size " + size)
                    .isEqualTo(String.valueOf(lockFree));
        }
        assertThat(context.eval("Atomics.isLockFree(3)").toString()).isEqualTo("false");
        assertThat(context.eval("Atomics.isLockFree(0)").toString()).isEqualTo("false");
    }

    @Test
    public void testIsLockFreeWithJavet() {
        assertBooleanWithJavet("Atomics.isLockFree('1') === Atomics.isLockFree(1)",
                "Atomics.isLockFree(true) === Atomics.isLockFree(1)",
                "Atomics.isLockFree({ valueOf() { return 3; } }) === Atomics.isLockFree(3)",
                "Atomics.isLockFree({ toString() { return '3'; } }) === Atomics.isLockFree(3)");
    }

    @Test
    public void testLoad() {
        JSInt32Array arr = sharedInts(2, 111, 222);

        // Test load
        JSValue result = atomics().load(context, null, new JSValue[]{arr, new JSNumber(0)});
        assertNumberValue(result, 111.0);

        result = atomics().load(context, null, new JSValue[]{arr, new JSNumber(1)});
        assertNumberValue(result, 222.0);
    }

    @Test
    public void testNotify() {
        JSInt32Array arr = sharedInts(1, 0);

        // Test 1: Notify with non-shared buffer - should still return 0
        JSValue result = atomics().notify(context, null, new JSValue[]{arr, new JSNumber(0)});
        assertThat(result.isNumber()).isTrue();
        assertNumberValue(result, 0.0);

        // Test 2: Invalid arguments
        JSValue error = atomics().notify(context, null, JSValue.NO_ARGS);
        assertTypeError(error);
        context.clearPendingException();

        // Test 3: Non-TypedArray argument
        error = atomics().notify(context, null, numberArgs(1));
        assertTypeError(error);
        context.clearPendingException();

        // Test 4: Out of bounds index
        error = atomics().notify(context, null, new JSValue[]{arr, new JSNumber(10)});
        assertRangeError(error);
        context.clearPendingException();

        // Test 5: Negative count is clamped to 0; non-shared buffers still return 0
        JSValue negativeCountResult = atomics().notify(context, null,
                new JSValue[]{arr, new JSNumber(0), new JSNumber(-1)});
        assertThat(negativeCountResult.isNumber()).isTrue();
        assertNumberValue(negativeCountResult, 0.0);
    }

    @Test
    public void testNotifyMultipleWaiters() throws InterruptedException {
        JSInt32Array arr = sharedInts(1, 200);

        int waiterCount = 3;
        CountDownLatch allWaitersStarted = new CountDownLatch(waiterCount);
        CountDownLatch allWaitersFinished = new CountDownLatch(waiterCount);
        AtomicInteger successCount = new AtomicInteger(0);
        JSRuntime sharedRuntime = context.getRuntime();

        // Create multiple waiters sharing the same runtime (agent cluster)
        for (int i = 0; i < waiterCount; i++) {
            Thread waiter = new Thread(() -> {
                JSContext waiterCtx = sharedRuntime.createContext();
                allWaitersStarted.countDown();
                JSValue result = sharedRuntime.getAtomicsObject().wait(waiterCtx, null,
                        new JSValue[]{arr, new JSNumber(0), new JSNumber(200), new JSNumber(5000)});

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
        JSValue notifyResult = atomics().notify(context, null,
                new JSValue[]{arr, new JSNumber(0), new JSNumber(waiterCount)});

        assertThat(notifyResult.isNumber()).isTrue();
        assertThat(notifyResult.asNumber().map(JSNumber::value).orElseThrow().intValue()).isEqualTo(waiterCount);

        // Wait for all waiters to finish
        assertThat(allWaitersFinished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(successCount.get()).isEqualTo(waiterCount); // All waiters should have been notified
    }

    @Test
    public void testNotifyWithInfinity() throws InterruptedException {
        JSInt32Array arr = sharedInts(1, 300);

        CountDownLatch waiterStarted = new CountDownLatch(1);
        CountDownLatch waiterFinished = new CountDownLatch(1);
        JSRuntime sharedRuntime = context.getRuntime();

        Thread waiter = new Thread(() -> {
            JSContext waiterCtx = sharedRuntime.createContext();
            waiterStarted.countDown();
            sharedRuntime.getAtomicsObject().wait(waiterCtx, null,
                    new JSValue[]{arr, new JSNumber(0), new JSNumber(300), new JSNumber(5000)});
            waiterFinished.countDown();
            waiterCtx.close();
        });

        waiter.start();
        assertThat(waiterStarted.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);

        // Notify with Integer.MAX_VALUE (equivalent to +Infinity in the spec)
        JSValue notifyResult = atomics().notify(context, null, new JSValue[]{arr, new JSNumber(0) // No count parameter
                                                                                                  // means notify all
        });

        assertThat(notifyResult.isNumber()).isTrue();
        assertThat(notifyResult.asNumber().map(JSNumber::value).orElseThrow().intValue()).isEqualTo(1);
        assertThat(waiterFinished.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testNotifyWithJavet() {
        assertIntegerWithJavet("Atomics.notify(new BigInt64Array(new ArrayBuffer(8)), 0, 0)",
                "Atomics.notify(new Int32Array(new ArrayBuffer(4)), 0, -1)");
        assertErrorWithJavet("Atomics.notify(new Uint8Array(new SharedArrayBuffer(8)), 0, 0)");
        assertErrorWithJavet(
                "Atomics.notify(new BigInt64Array(new ArrayBuffer(8)), { valueOf() { throw new Error('index'); } }, 0)");
        assertErrorWithJavet(
                "Atomics.notify(new BigInt64Array(new ArrayBuffer(8)), 0, { valueOf() { throw new Error('count'); } })");
    }

    @Test
    public void testOr() {
        JSInt32Array arr = sharedInts(2, 10, 12);

        // Test OR operation: 10 | 5 = 15 (0b1010 | 0b0101 = 0b1111)
        assertAtomicUpdate(atomics()::or, arr, 0, 10, 15, 5);

        // Test OR with all bits set: 12 | 3 = 15
        assertAtomicUpdate(atomics()::or, arr, 1, 12, 15, 3);
    }

    @Test
    public void testPause() {
        // Atomics.pause() should return undefined
        JSValue result = atomics().pause(context, null, JSValue.NO_ARGS);
        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    public void testStore() {
        JSInt32Array arr = sharedInts(2, 0, 0);

        // Test store
        assertAtomicUpdate(atomics()::store, arr, 0, 999, 999, 999);

        assertAtomicUpdate(atomics()::store, arr, 1, -123, -123, -123);
    }

    @Test
    public void testSub() {
        JSInt32Array arr = sharedInts(4, 50, 25);

        // Test normal case: subtract from index 0
        assertAtomicUpdate(atomics()::sub, arr, 0, 50, 35, 15);

        // Test subtract to index 1
        assertAtomicUpdate(atomics()::sub, arr, 1, 25, 15, 10);
    }

    @Test
    public void testWait() {
        JSInt32Array arr = sharedInts(1, 42);

        // Test 1: Wait with non-matching value - should return "not-equal"
        JSValue result = atomics().wait(context, null,
                new JSValue[]{arr, new JSNumber(0), new JSNumber(0), new JSNumber(0)});
        assertThat(result.isString()).isTrue();
        assertStringValue(result, "not-equal");

        // Test 2: Wait with matching value and immediate timeout - should return "timed-out"
        result = atomics().wait(context, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(42), new JSNumber(0)});
        assertThat(result.isString()).isTrue();
        assertStringValue(result, "timed-out");

        assertWaitArgumentErrors(atomics()::wait, arr);
    }

    @Test
    public void testWaitAndNotifyMultithreaded() throws InterruptedException {
        JSInt32Array arr = sharedInts(1, 100);

        CountDownLatch waitStarted = new CountDownLatch(1);
        CountDownLatch waitFinished = new CountDownLatch(1);
        AtomicBoolean waitSuccess = new AtomicBoolean(false);
        JSRuntime sharedRuntime = context.getRuntime();

        // Thread 1: Wait
        Thread waiter = new Thread(() -> {
            JSContext waiterCtx = sharedRuntime.createContext();
            waitStarted.countDown();
            JSValue result = sharedRuntime.getAtomicsObject().wait(waiterCtx, null,
                    new JSValue[]{arr, new JSNumber(0), new JSNumber(100), new JSNumber(5000) // 5 second timeout
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
        JSValue notifyResult = atomics().notify(context, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(1)});

        // Should notify 1 waiter
        assertThat(notifyResult.isNumber()).isTrue();
        assertThat(notifyResult.asNumber().map(JSNumber::value).orElseThrow().intValue()).isEqualTo(1);

        // Wait for waiter to finish
        assertThat(waitFinished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(waitSuccess.get()).isTrue(); // Wait should have been notified successfully
    }

    @Test
    public void testWaitAsync() {
        JSInt32Array arr = sharedInts(1, 42);

        // Test 1: waitAsync with non-matching value - should return {async: false, value: "not-equal"}
        JSValue result = atomics().waitAsync(context, null, new JSValue[]{arr, new JSNumber(0), new JSNumber(0)});
        JSObject resultObj = result.asObject().orElseThrow();
        assertThat(resultObj.get("async").isBoolean()).isTrue();
        assertThat(resultObj.get("async").asBoolean().map(JSBoolean::value).orElseThrow()).isFalse();
        assertThat(((JSString) resultObj.get("value")).value()).isEqualTo("not-equal");

        // Test 2: waitAsync with matching value and positive timeout - should return {async: true, value: Promise}
        result = atomics().waitAsync(context, null,
                new JSValue[]{arr, new JSNumber(0), new JSNumber(42), new JSNumber(1)});
        resultObj = result.asObject().orElseThrow();
        assertThat(resultObj.get("async").isBoolean()).isTrue();
        assertThat(resultObj.get("async").asBoolean().map(JSBoolean::value).orElseThrow()).isTrue();
        assertThat(resultObj.get("value")).isInstanceOf(JSPromise.class);
        JSPromise waitPromise = (JSPromise) resultObj.get("value");
        long deadline = System.currentTimeMillis() + 500;
        // The helper thread only enqueues the settling job; the context's own thread runs it.
        while (waitPromise.getState() == JSPromise.PromiseState.PENDING && System.currentTimeMillis() < deadline) {
            Thread.yield();
            context.processMicrotasks();
        }
        assertThat(waitPromise.getState()).isEqualTo(JSPromise.PromiseState.FULFILLED);
        assertStringValue(waitPromise.getResult(), "timed-out");

        assertWaitArgumentErrors(atomics()::waitAsync, arr);
    }

    @Test
    public void testXor() {
        JSInt32Array arr = sharedInts(2, 15, 10);

        // Test XOR operation: 15 ^ 10 = 5 (0b1111 ^ 0b1010 = 0b0101)
        assertAtomicUpdate(atomics()::xor, arr, 0, 15, 5, 10);

        // Test XOR with same value: 10 ^ 10 = 0
        assertAtomicUpdate(atomics()::xor, arr, 1, 10, 0, 10);
    }
}
