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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bytecode constant objects — array literals, regexp literals, tagged-template objects — must not
 * outlive the bytecode that owns them.
 * <p>
 * The VM tracked "prototype already transferred" in a per-VM {@code Set<JSObject>} built on an
 * {@code IdentityHashMap}. Entries were added but never removed, so every constant object ever
 * evaluated stayed strongly reachable for the lifetime of the VM, along with everything it
 * transitively referenced. The bit now lives on the object itself, so it dies with the object.
 */
public class VirtualMachineConstantObjectTest extends BaseTest {

    private static boolean awaitCollection(WeakReference<?> reference) throws InterruptedException {
        for (int attempt = 0; attempt < 20 && reference.get() != null; attempt++) {
            System.gc();
            Thread.sleep(25);
        }
        return reference.get() == null;
    }

    /**
     * How many of these references still point at something, after asking the collector for a while.
     *
     * @param references the references to watch
     * @return the number still live
     */
    private static long liveCount(List<WeakReference<JSValue>> references) throws InterruptedException {
        long live = references.size();
        for (int attempt = 0; attempt < 20 && live > 0; attempt++) {
            System.gc();
            Thread.sleep(25);
            live = references.stream().filter(reference -> reference.get() != null).count();
        }
        return live;
    }

    private WeakReference<JSValue> evalToWeakReference(String code) {
        return new WeakReference<>(context.eval(code));
    }

    @Test
    @Timeout(60)
    public void testConstantObjectIsCollectableAfterItsBytecodeIsDropped() throws InterruptedException {
        context.eval("function tag(strings) { return strings }");
        // Built in a separate frame so the only strong reference to the result dies with that
        // frame; a local in this method would stay live in its stack slot.
        WeakReference<JSValue> templateReference = evalToWeakReference("tag`hello ${1} world`");
        // Evaluate a second tagged template so the engine's single-slot hold on the most recently
        // compiled program is released. With the side table this was still not enough: the set
        // held every constant object ever seen.
        context.eval("tag`goodbye ${2} world`");
        for (int index = 0; index < 20; index++) {
            context.eval("1");
        }
        assertThat(awaitCollection(templateReference))
                .as("a template object must not outlive the bytecode that owns it")
                .isTrue();
    }

    @Test
    public void testConstantObjectPrototypeIsStillTransferred() {
        // The side table existed to make the transfer happen exactly once. That must still hold.
        assertThat(context.eval(
                """
                        function tag(strings) { return Object.getPrototypeOf(strings) === Array.prototype }
                        tag`a ${1} b`""").toString())
                .isEqualTo("true");
        assertThat(context.eval("Object.getPrototypeOf(/x/g) === RegExp.prototype").toString())
                .isEqualTo("true");
        assertThat(context.eval(
                """
                        function tag(strings) { return Object.getPrototypeOf(strings.raw) === Array.prototype }
                        tag`a ${1} b`""").toString())
                .isEqualTo("true");
    }

    @Test
    public void testConstantObjectRecordsItsPrototypeInitialization() {
        JSValue templateObject = context.eval(
                """
                        function tag(strings) { return strings }
                        tag`a ${1} b`""");
        assertThat(templateObject).isInstanceOfSatisfying(JSObject.class,
                object -> assertThat(object.isConstantPrototypeInitialized()).isTrue());
        // A plain runtime object is not a bytecode constant and carries no such marking.
        assertThat(context.eval("({})")).isInstanceOfSatisfying(JSObject.class,
                object -> assertThat(object.isConstantPrototypeInitialized()).isFalse());
    }

    @Test
    @Timeout(120)
    public void testRepeatedConstantObjectEvaluationDoesNotRetainMemory() throws InterruptedException {
        // Sampled weak references rather than a heap measurement.
        //
        // This used to compare `totalMemory() - freeMemory()` before and against a 16 MB budget,
        // which was measured at ~46 MB with the leak and ~20 KB without — a threshold with three
        // orders of magnitude of headroom that still failed intermittently on CI, and only there.
        // The reason is that those two numbers are JVM-wide: they count every live object in the
        // process, and this suite deliberately leaves worker threads running — the abandoned-worker
        // cases in Test262RunnerOutcomeTest keep interpreting JavaScript on purpose, so a fork that
        // schedules them alongside this class measures their allocation as this class's retention.
        // Which classes share a fork depends on the host's processor count, which is why this failed
        // on macOS runners and passed everywhere else.
        //
        // Sampling the objects themselves states the invariant directly and cannot be moved by
        // anything else in the process: a constant object must not outlive the bytecode that owns
        // it, whatever else the JVM is doing. With the side table that leaked, every one of these
        // stays strongly reachable, so the failure is 200 live references rather than a number over
        // a budget.
        context.eval("function tag(strings) { return strings }");
        List<WeakReference<JSValue>> sampledTemplateObjects = new ArrayList<>();
        JSValue templateObject = null;
        for (int index = 0; index < 20000; index++) {
            templateObject = context.eval("tag`payload" + index + " ${1} ${2} ${3}`");
            if (index % 100 == 0) {
                sampledTemplateObjects.add(new WeakReference<>(templateObject));
            }
        }
        // The last one is still held by the local and by the engine's single-slot hold on the most
        // recently compiled program; drop both, the way the case above does.
        templateObject = null;
        for (int index = 0; index < 20; index++) {
            context.eval("1");
        }
        assertThat(templateObject).isNull();

        assertThat(liveCount(sampledTemplateObjects))
                .as("of %d template objects sampled across 20000 evaluations, none may outlive the"
                        + " bytecode that owns it", sampledTemplateObjects.size())
                .isZero();
    }
}
