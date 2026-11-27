package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.builtins.array.ArrayPrototype;
import com.caoccao.qjs4j.core.collection.JSMap;
import com.caoccao.qjs4j.core.collection.JSSet;
import com.caoccao.qjs4j.core.intl.JSIntlSegments;
import com.caoccao.qjs4j.exceptions.JSTerminationException;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Review02ResourcesTest extends BaseJavetTest {
    private WeakReference<JSObject> captureCycle() {
        JSObject object = context.createJSObject();
        context.captureStackTrace(object);
        object.set(PropertyKey.STACK, object);
        assertThat(object.get(PropertyKey.STACK)).isSameAs(object);
        return new WeakReference<>(object);
    }

    @Test
    void capturedStackCyclesAreNotRootedByContext() throws InterruptedException {
        WeakReference<JSObject> reference = captureCycle();
        for (int i = 0; i < 100 && reference.get() != null; i++) {
            System.gc();
            Thread.sleep(10);
        }
        assertThat(reference.get()).isNull();
        assertStringWithJavet("(()=>{const o={};Error.captureStackTrace(o);o.stack=o;return String(o.stack===o)})()");
    }

    @Test
    void collectionCursorsDoNotSnapshotAndTraverseGrowingFrontier() {
        JSMap map = context.createJSMap();
        JSMap.IterationCursor cursor = map.createIterationCursor();
        for (int i = 0; i < 20_000; i++) {
            map.mapSet(JSNumber.of(i), JSNumber.of(i));
            assertThat(map.nextIterationEntry(cursor).value()).isEqualTo(JSNumber.of(i));
        }
        assertThat(map.nextIterationEntry(cursor)).isNull();
        JSSet set = context.createJSSet();
        JSSet.IterationCursor setCursor = set.createIterationCursor();
        for (int i = 0; i < 20_000; i++) {
            set.setAdd(JSNumber.of(i));
            assertThat(set.nextIterationValue(setCursor)).isEqualTo(JSNumber.of(i));
        }
        assertThat(set.nextIterationValue(setCursor)).isNull();
    }

    @Test
    void interruptArrivingAfterNativeLoopStartsTerminatesIt() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        JSObject receiver = new JSObject(context) {
            @Override
            public JSValue get(PropertyKey key) {
                if (key.equals(PropertyKey.LENGTH))
                    return JSNumber.of(100_000_000);
                entered.countDown();
                return JSUndefined.INSTANCE;
            }
        };
        Thread worker = new Thread(() -> {
            try {
                ArrayPrototype.includes(context, receiver, new JSValue[]{JSNumber.of(1)});
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        worker.start();
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            context.getRuntime().requestInterrupt();
            worker.join(5000);
            assertThat(worker.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOf(JSTerminationException.class);
        } finally {
            context.getRuntime().requestInterrupt();
            worker.join(5000);
            context.getRuntime().clearInterrupt();
        }
    }

    @Test
    void mapAndSetIteratorsFollowMutation() {
        assertStringWithJavet("""
                (()=>{const out=[];for(const c of [new Map([[1,1],[2,2],[3,3]]),new Set([1,2,3])]) {
                  const it=c.values();out.push(it.next());c.delete(1);c.delete(2);
                  if(c instanceof Map)c.set(4,4);else c.add(4);
                  out.push(it.next());c.clear();if(c instanceof Map)c.set(5,5);else c.add(5);
                  out.push(it.next(),it.next());if(c instanceof Map)c.set(6,6);else c.add(6);
                  out.push(it.next());
                }return JSON.stringify(out)})()
                """, """
                (()=>{const out=[];for(const c of [new Map(),new Set()]) {
                  const i=c.values();if(c instanceof Map)c.set(1,1);else c.add(1);out.push(i.next());
                  c.delete(1);if(c instanceof Map)c.set(2,2);else c.add(2);out.push(i.next());
                }return JSON.stringify(out)})()
                """);
    }

    @Test
    void nativeLoopsObserveExpiredDeadline() {
        JSObject receiver = context.createJSObject();
        receiver.set("length", JSNumber.of(100_000_000));
        context.getVirtualMachine().setExecutionDeadline(System.currentTimeMillis() - 1);
        assertThatThrownBy(() -> ArrayPrototype.includes(context, receiver, new JSValue[]{JSNumber.of(1)}))
                .isInstanceOf(JSTerminationException.class);
    }

    @Test
    void nativeLoopsPollHostInterrupt() {
        context.getRuntime().requestInterrupt();
        JSObject receiver = context.createJSObject();
        receiver.set("length", JSNumber.of(100_000_000));
        assertThatThrownBy(() -> ArrayPrototype.indexOf(context, receiver, new JSValue[]{JSNumber.of(1)}))
                .isInstanceOf(JSTerminationException.class);
        context.getRuntime().clearInterrupt();
    }

    @Test
    void segmentsOnlyCacheRequestedPrefixAndShareBoundaries() throws Exception {
        JSIntlSegments segments = (JSIntlSegments) context.eval("new Intl.Segmenter('en').segment('x'.repeat(20000))");
        var rangesField = JSIntlSegments.class.getDeclaredField("segmentRanges");
        rangesField.setAccessible(true);
        var ranges = (java.util.List<?>) rangesField.get(segments);
        assertThat(ranges).isEmpty();
        segments.containing(context, JSNumber.of(0));
        assertThat(ranges).hasSize(1);
        segments.containing(context, JSNumber.of(999));
        assertThat(ranges).hasSize(1000);
        for (int i = 999; i >= 0; i--)
            segments.containing(context, JSNumber.of(i));
        assertThat(ranges).hasSize(1000);
        // Cached records hold offsets and word classification, never copied segment strings.
        assertThat(ranges.get(0).getClass().getRecordComponents())
                .allSatisfy(component -> assertThat(component.getType()).isNotEqualTo(String.class));
    }
}
