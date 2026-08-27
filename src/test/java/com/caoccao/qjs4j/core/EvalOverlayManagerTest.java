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

import com.caoccao.qjs4j.BaseTest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct tests for {@link EvalOverlayManager}, the stack of temporary global-object overlays a
 * module's imports are installed as.
 * <p>
 * Everything here used to be reachable only by evaluating a module that imports another module,
 * which is why the half of this class that matters most — suspend, resume, and the deferred restore
 * a top-level-await module needs — was the least covered code in the realm package. A frame that is
 * put back wrongly does not fail here; it fails much later, in some unrelated script that finds a
 * global it never declared, or has lost one it did.
 * <p>
 * The manager is package-private state with no source of its own, so these cases drive it directly
 * and read the answers off the realm's global object.
 */
public class EvalOverlayManagerTest extends BaseTest {
    private static final PropertyKey X = PropertyKey.fromString("x");
    private static final PropertyKey Y = PropertyKey.fromString("y");

    private static Map<String, JSValue> savedGlobals(String name, JSValue value) {
        Map<String, JSValue> savedGlobals = new LinkedHashMap<>();
        savedGlobals.put(name, value);
        return savedGlobals;
    }

    private String globalText(PropertyKey key) {
        JSObject globalObject = context.getGlobalObject();
        return globalObject.has(key) ? globalObject.get(key).toString() : null;
    }

    private EvalOverlayManager manager() {
        return context.evalOverlayManager();
    }

    private void setGlobal(PropertyKey key, String value) {
        context.getGlobalObject().set(key, new JSString(value));
    }

    @Test
    public void testClearDropsEveryFrameWithoutTouchingTheRealm() {
        // Closing a context clears the stack; it must not also try to put the realm back, because
        // by then there may be no realm left to put anything back into.
        setGlobal(X, "overlay");
        manager().push(savedGlobals("x", new JSString("outer")), Set.of());
        manager().push(savedGlobals("x", new JSString("outer")), Set.of());
        manager().clear();
        assertThat(manager().hasFrames()).isFalse();
        assertThat(globalText(X)).as("clear() drops bookkeeping, it does not restore").isEqualTo("overlay");
    }

    @Test
    public void testDeferredRestoreRunsWhenTheModulePromiseRejects() {
        // A module body that throws still has to give the realm back, or a failed import leaves its
        // bindings installed for whatever runs next.
        setGlobal(X, "overlay");
        EvalOverlayManager.Frame frame =
                new EvalOverlayManager.Frame(savedGlobals("x", new JSString("outer")), Set.of());
        JSPromise asyncModulePromise = context.createJSPromise();
        manager().registerDeferredRestore(asyncModulePromise, frame);

        asyncModulePromise.reject(new JSString("boom"));
        context.processMicrotasks();
        assertThat(globalText(X)).isEqualTo("outer");
    }

    @Test
    public void testDeferredRestoreWaitsForTheModulePromiseToSettle() {
        // A top-level-await module's body is still running when eval returns its promise, so the
        // overlay has to outlive the call: taking it away at return would remove the module's own
        // imports from underneath it.
        setGlobal(X, "outer");
        EvalOverlayManager.Frame frame = new EvalOverlayManager.Frame(
                savedGlobals("x", new JSString("outer")), new LinkedHashSet<>(Set.of("y")));
        setGlobal(X, "overlay");
        setGlobal(Y, "overlay");
        JSPromise asyncModulePromise = context.createJSPromise();
        manager().registerDeferredRestore(asyncModulePromise, frame);

        context.processMicrotasks();
        assertThat(globalText(X)).as("a pending module still owns its overlay").isEqualTo("overlay");

        asyncModulePromise.resolve(context, JSUndefined.INSTANCE);
        context.processMicrotasks();
        assertThat(globalText(X)).isEqualTo("outer");
        assertThat(context.getGlobalObject().has(Y)).isFalse();
    }

    @Test
    public void testDeferredRestoreWithNothingToRestoreIsANoOp() {
        // Both arguments are optional at the call site: a module without imports has no frame, and
        // a body that finished synchronously has no promise.
        EvalOverlayManager.Frame frame =
                new EvalOverlayManager.Frame(savedGlobals("x", new JSString("outer")), Set.of());
        manager().registerDeferredRestore(null, frame);
        JSPromise asyncModulePromise = context.createJSPromise();
        manager().registerDeferredRestore(asyncModulePromise, null);
        setGlobal(X, "overlay");
        asyncModulePromise.resolve(context, JSUndefined.INSTANCE);
        context.processMicrotasks();
        assertThat(globalText(X)).isEqualTo("overlay");
    }

    @Test
    public void testLookupSuppressionHidesTheOverlayWithoutUnwindingIt() {
        // An indirect eval has to reach the real global scope, so it suppresses the overlay rather
        // than popping it — the module body underneath is still running and still needs it.
        manager().push(savedGlobals("x", new JSString("outer")), new LinkedHashSet<>(Set.of("y")));
        assertThat(manager().hasFrames()).isTrue();
        assertThat(manager().hasBinding("x")).isTrue();

        manager().pushLookupSuppression();
        manager().pushLookupSuppression();
        assertThat(manager().hasFrames()).as("suppressed, not unwound").isFalse();
        assertThat(manager().hasBinding("x")).isFalse();
        assertThat(manager().hasBinding("y")).isFalse();

        manager().popLookupSuppression();
        assertThat(manager().hasBinding("x")).as("the outer suppression is still in force").isFalse();
        manager().popLookupSuppression();
        assertThat(manager().hasBinding("x")).isTrue();
        assertThat(manager().hasFrames()).isTrue();

        // Unbalanced pops cannot drive the depth negative, which would make the next push read as
        // "not suppressed".
        manager().popLookupSuppression();
        manager().pushLookupSuppression();
        assertThat(manager().hasBinding("x")).isFalse();
        manager().resetLookupSuppression();
        assertThat(manager().hasBinding("x")).isTrue();
    }

    @Test
    public void testPushAndPopTrackWhichNamesAnOverlayOwns() {
        assertThat(manager().hasFrames()).isFalse();
        assertThat(manager().hasBinding("x")).isFalse();

        manager().push(savedGlobals("x", new JSString("outer")), new LinkedHashSet<>(Set.of("y")));
        assertThat(manager().hasFrames()).isTrue();
        // A name the overlay displaced and a name it invented are both the overlay's.
        assertThat(manager().hasBinding("x")).isTrue();
        assertThat(manager().hasBinding("y")).isTrue();
        assertThat(manager().hasBinding("z")).isFalse();

        manager().pop();
        assertThat(manager().hasFrames()).isFalse();
        assertThat(manager().hasBinding("x")).isFalse();
        // Popping an empty stack is a no-op rather than an error: the eval pipeline pops in a
        // finally block that also runs on paths that never pushed.
        manager().pop();
        assertThat(manager().hasFrames()).isFalse();
    }

    @Test
    public void testRestoreFrameDefinesOverAnAccessorInstalledForALiveBinding() {
        // An import binding is installed as an accessor so it can follow the exporting module's
        // value. Restoring has to define over it; assigning would call the setter instead, and the
        // realm would keep the accessor.
        JSNativeFunction getter = new JSNativeFunction(
                context, "x", 0, (ctx, thisArg, args) -> new JSString("overlay"));
        getter.initializePrototypeChain(context);
        context.getGlobalObject().defineProperty(X, getter, null, PropertyDescriptor.AccessorState.All);
        assertThat(globalText(X)).isEqualTo("overlay");

        manager().restoreFrame(
                new EvalOverlayManager.Frame(savedGlobals("x", new JSString("outer")), Set.of()));
        assertThat(globalText(X)).isEqualTo("outer");
        assertThat(context.getGlobalObject().getOwnPropertyDescriptor(X).isDataDescriptor()).isTrue();
    }

    @Test
    public void testRestoreFramePutsTheRealmBackExactlyAsItWas() {
        setGlobal(X, "outer");
        EvalOverlayManager.Frame frame = new EvalOverlayManager.Frame(
                savedGlobals("x", new JSString("outer")), new LinkedHashSet<>(Set.of("y")));
        setGlobal(X, "overlay");
        setGlobal(Y, "overlay");

        manager().restoreFrame(frame);
        assertThat(globalText(X)).as("a displaced value comes back").isEqualTo("outer");
        assertThat(context.getGlobalObject().has(Y))
                .as("a name that was not there is deleted, not set to undefined")
                .isFalse();
    }

    @Test
    public void testRestoreFrameWithNoFrameIsANoOp() {
        setGlobal(X, "overlay");
        manager().restoreFrame(null);
        assertThat(globalText(X)).isEqualTo("overlay");
    }

    @Test
    public void testResumeWithNoSnapshotIsANoOp() {
        // suspend() answers null when there was nothing to suspend, and that null comes straight
        // back here on the way out of the nested evaluation.
        setGlobal(X, "outer");
        manager().resume(null);
        assertThat(globalText(X)).isEqualTo("outer");
    }

    @Test
    public void testSuspendAndResumeSpanEveryFrameOnTheStack() {
        // Suspension is per stack, not per frame: a nested evaluation has to see through all of
        // them, and resuming has to put every name back that any of them owns.
        setGlobal(X, "outer");
        manager().push(savedGlobals("x", new JSString("outer")), Set.of());
        setGlobal(X, "first");
        manager().push(new LinkedHashMap<>(), new LinkedHashSet<>(Set.of("y")));
        setGlobal(Y, "second");

        JSGlobalObject.EvalOverlaySnapshot snapshot = manager().suspend();
        assertThat(globalText(X)).as("the first frame's name is restored").isEqualTo("outer");
        assertThat(context.getGlobalObject().has(Y)).as("the second frame's name is deleted").isFalse();
        assertThat(snapshot.absentKeys()).isEmpty();

        manager().resume(snapshot);
        assertThat(globalText(X)).isEqualTo("first");
        assertThat(globalText(Y)).isEqualTo("second");
    }

    @Test
    public void testSuspendDefinesOverANonWritableOverlaidGlobal() {
        // Assignment cannot put a value back over a non-writable property, so suspend defines over
        // it — otherwise a module that overlaid a read-only global would silently keep its own
        // value installed for the nested evaluation. Configurable, because a property that is
        // neither writable nor configurable cannot be redefined either, and no overlay can put that
        // one back by any means.
        context.getGlobalObject().defineProperty(
                X, new JSString("overlay"), PropertyDescriptor.DataState.Configurable);
        manager().push(savedGlobals("x", new JSString("outer")), Set.of());

        JSGlobalObject.EvalOverlaySnapshot snapshot = manager().suspend();
        assertThat(globalText(X)).isEqualTo("outer");
        assertThat(snapshot.values().get("x")).hasToString("overlay");
    }

    @Test
    public void testSuspendTakesTheOverlayAwayAndResumePutsItBack() {
        setGlobal(X, "outer");
        manager().push(savedGlobals("x", new JSString("outer")), new LinkedHashSet<>(Set.of("y")));
        setGlobal(X, "overlay");
        setGlobal(Y, "overlay");

        JSGlobalObject.EvalOverlaySnapshot snapshot = manager().suspend();
        assertThat(globalText(X)).as("the nested evaluation sees the realm").isEqualTo("outer");
        assertThat(context.getGlobalObject().has(Y)).isFalse();
        assertThat(manager().hasFrames()).as("suspending does not unwind the stack").isTrue();

        manager().resume(snapshot);
        assertThat(globalText(X)).isEqualTo("overlay");
        assertThat(globalText(Y)).isEqualTo("overlay");
    }

    @Test
    public void testSuspendWithNoFramesAnswersNothing() {
        assertThat(manager().suspend()).isNull();
    }

    @Test
    public void testSuspendedNamesThatAreAbsentComeBackAbsent() {
        // A name the overlay deleted rather than set has to be recorded as absent, or resuming
        // would reinstate it as undefined and a `typeof` check would answer differently.
        manager().push(new LinkedHashMap<>(), new LinkedHashSet<>(Set.of("y")));
        assertThat(context.getGlobalObject().has(Y)).isFalse();

        JSGlobalObject.EvalOverlaySnapshot snapshot = manager().suspend();
        assertThat(snapshot.absentKeys()).containsExactly("y");
        assertThat(snapshot.values()).isEmpty();

        setGlobal(Y, "leaked");
        manager().resume(snapshot);
        assertThat(context.getGlobalObject().has(Y)).isFalse();
    }
}
