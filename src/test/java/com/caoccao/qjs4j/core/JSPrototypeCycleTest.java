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

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code OrdinarySetPrototypeOf} must never install a cycle, at any chain depth.
 * <p>
 * The circularity check used to stop after 1,000 links and then install the prototype anyway, so
 * {@code Object.setPrototypeOf(a, chainEndingAtA)} succeeded whenever the chain was longer than
 * that. A resource cutoff is a false negative here, and a false negative installs a real cycle:
 * afterwards every property read on {@code a} either raised an artificial {@code RangeError} or ran
 * into other cycle-sensitive code.
 * <p>
 * The script-visible behaviour is asserted against V8; {@code ../quickjs/qjs} agrees, raising
 * {@code TypeError} at every depth. The three tests that reach for {@code JSObject} directly cannot
 * be: they use the raw {@code setPrototype} embedder API to build a cycle no JavaScript program can
 * construct, which is exactly the case the bounded walk was there to survive.
 */
public class JSPrototypeCycleTest extends BaseJavetTest {

    /**
     * Build source that proposes a prototype chain of {@code depth} links ending back at the
     * target.
     *
     * @param depth links between the target and the proposed prototype
     * @return source evaluating to {@code 'ALLOWED'} or the error name
     */
    private String cycleAttempt(int depth) {
        // An IIFE, so repeated calls in one test do not redeclare `a` in the same global scope.
        return """
                (function () {
                  let a = {};
                  let proposed = a;
                  for (let i = 0; i < %d; i++) proposed = Object.create(proposed);
                  try {
                    Object.setPrototypeOf(a, proposed);
                    return 'ALLOWED';
                  } catch (e) {
                    return e.name;
                  }
                })()""".formatted(depth);
    }

    @Test
    @Timeout(60)
    public void testAPreExistingCycleContainingTheTargetIsRejected() {
        JSObject target = context.createJSObject();
        JSObject middle = context.createJSObject();
        middle.setPrototype(target);
        target.setPrototype(middle);

        JSObject head = context.createJSObject();
        head.setPrototype(middle);
        assertThat(target.setPrototypeChecked(head)).isEqualTo(JSObject.SetPrototypeResult.CIRCULAR);
    }

    @Test
    @Timeout(60)
    public void testAPreExistingCycleDoesNotHangTheCheck() {
        // setPrototype is the raw embedder API and installs a cycle without checking. A chain
        // walk that met that cycle would loop forever without Floyd detection; since the cycle
        // does not contain the target, the assignment is not itself circular.
        JSObject first = context.createJSObject();
        JSObject second = context.createJSObject();
        first.setPrototype(second);
        second.setPrototype(first);

        JSObject target = context.createJSObject();
        assertThat(target.setPrototypeChecked(first)).isEqualTo(JSObject.SetPrototypeResult.SUCCESS);
    }

    @Test
    @Timeout(60)
    public void testCycleRejectedAtDepthOne() {
        assertStringWithJavet(cycleAttempt(1));
    }

    @Test
    @Timeout(60)
    public void testCycleRejectedAtTheOldCutoff() {
        assertStringWithJavet(cycleAttempt(1000));
    }

    @Test
    @Timeout(60)
    public void testCycleRejectedFarBeyondTheOldCutoff() {
        assertStringWithJavet(cycleAttempt(5000));
    }

    @Test
    @Timeout(60)
    public void testCycleRejectedJustBelowTheOldCutoff() {
        assertStringWithJavet(cycleAttempt(999));
    }

    @Test
    @Timeout(60)
    public void testCycleRejectedJustBeyondTheOldCutoff() {
        // 1,001 and 1,002 are the depths the bounded walk reported as acyclic.
        assertStringWithJavet(cycleAttempt(1001));
        assertStringWithJavet(cycleAttempt(1002));
    }

    @Test
    @Timeout(60)
    public void testDeepAcyclicChainIsStillAccepted() {
        // The complement: removing the cutoff must not start rejecting legitimate deep chains.
        assertStringWithJavet(
                """
                        (function () {
                          let a = {};
                          let proposed = {};
                          for (let i = 0; i < 5000; i++) proposed = Object.create(proposed);
                          try { Object.setPrototypeOf(a, proposed); return 'ALLOWED' } catch (e) { return e.name }
                        })()""");
    }

    @Test
    @Timeout(60)
    public void testDirectSelfAssignmentIsRejected() {
        assertStringWithJavet(
                """
                        (function () {
                          let a = {};
                          try { Object.setPrototypeOf(a, a); return 'ALLOWED' } catch (e) { return e.name }
                        })()""");
    }

    @Test
    @Timeout(60)
    public void testNullPrototypeIsNeverCircular() {
        assertStringWithJavet(
                """
                        (function () {
                          let a = { x: 1 };
                          try { Object.setPrototypeOf(a, null); return String(Object.getPrototypeOf(a)) }
                          catch (e) { return e.name }
                        })()""");
    }

    @Test
    @Timeout(60)
    public void testProxyInTheChainStopsTheWalk() {
        // ES2024 10.1.2 step 8.c: the walk stops at a non-ordinary object rather than running its
        // getPrototypeOf trap.
        assertStringWithJavet(
                """
                        (function () {
                          let a = {};
                          let p = new Proxy({}, { getPrototypeOf() { return a } });
                          let child = Object.create(p);
                          try { Object.setPrototypeOf(a, child); return 'ALLOWED' } catch (e) { return e.name }
                        })()""");
    }

    @Test
    @Timeout(60)
    public void testTheOtherSetPrototypeRejectionsStillApply() {
        // The circularity check is one of four outcomes; the rest must be unchanged by replacing it.
        JSObject target = context.createJSObject();
        JSObject proto = context.createJSObject();

        // Same prototype: a no-op success, checked before anything else.
        assertThat(target.setPrototypeChecked(target.getPrototype()))
                .isEqualTo(JSObject.SetPrototypeResult.SUCCESS);

        // Non-extensible.
        JSObject sealed = context.createJSObject();
        sealed.preventExtensions();
        assertThat(sealed.setPrototypeChecked(proto))
                .isEqualTo(JSObject.SetPrototypeResult.NOT_EXTENSIBLE);

        // Immutable prototype exotic object: Object.prototype.
        assertStringWithJavet(
                """
                        (function () {
                          try { Object.setPrototypeOf(Object.prototype, {}); return 'ALLOWED' }
                          catch (e) { return e.name }
                        })()""");
    }
}
