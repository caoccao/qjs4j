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
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every prototype chain walk must be bounded and must keep proxy traps in play.
 * <p>
 * {@code getWithReceiver} was bounded at 10,000 links, but the walk is recursive — so a proxy in
 * the chain still gets its traps — and the Java stack cannot hold 10,000 of those frames: a deep
 * chain died with a {@code StackOverflowError} before the guard fired. {@code has} had no bound at
 * all, so the same prototype graph was safe to read from and unsafe for {@code in}.
 * <p>
 * The script-observable walks are asserted against V8. The cyclic and very deep chains are not: they
 * are built with the raw {@code setPrototype} embedder API, which has no JavaScript equivalent —
 * no script can construct a prototype cycle — and the bound they hit is a Java stack limit.
 */
public class JSPrototypeChainWalkTest extends BaseJavetTest {

    private JSObject buildChain(int depth) {
        JSObject object = context.createJSObject();
        for (int index = 0; index < depth; index++) {
            JSObject child = context.createJSObject();
            child.setPrototype(object);
            object = child;
        }
        return object;
    }

    @Test
    @Timeout(60)
    public void testCyclicPrototypeChainRaisesRangeErrorForGet() {
        JSObject first = context.createJSObject();
        JSObject second = context.createJSObject();
        first.setPrototype(second);
        second.setPrototype(first);

        assertThatThrownBy(() -> first.get(PropertyKey.fromString("missing")))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("Maximum prototype chain depth exceeded");
    }

    @Test
    @Timeout(60)
    public void testCyclicPrototypeChainRaisesRangeErrorForHas() {
        // setPrototype is the raw embedder API and does not run the cycle check, so a cycle is
        // reachable. It used to recurse until the Java stack was exhausted.
        JSObject first = context.createJSObject();
        JSObject second = context.createJSObject();
        first.setPrototype(second);
        second.setPrototype(first);

        assertThatThrownBy(() -> first.has(PropertyKey.fromString("missing")))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("Maximum prototype chain depth exceeded");
    }

    @Test
    @Timeout(60)
    public void testDeepPrototypeChainRaisesRangeErrorRatherThanStackOverflow() {
        JSObject deep = buildChain(5000);
        PropertyKey missing = PropertyKey.fromString("missing");
        assertThatThrownBy(() -> deep.get(missing))
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("Maximum prototype chain depth exceeded");
        assertThatThrownBy(() -> deep.has(missing))
                .as("`in` must be bounded exactly like a property read")
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessageContaining("Maximum prototype chain depth exceeded");
    }

    @Test
    public void testInheritedPropertyIsFoundThroughTheChain() {
        JSObject base = context.createJSObject();
        base.set(PropertyKey.fromString("inherited"), new JSString("from base"));
        JSObject derived = context.createJSObject();
        derived.setPrototype(base);

        assertThat(derived.has(PropertyKey.fromString("inherited"))).isTrue();
        assertThat(derived.get(PropertyKey.fromString("inherited")).toString()).isEqualTo("from base");
    }

    @Test
    public void testInstanceofInvokesTheGetPrototypeOfTrap() {
        // OrdinaryHasInstance step 4.b calls O.[[GetPrototypeOf]] on each step, so a Proxy in the
        // chain must be consulted rather than traversed as its target.
        assertStringWithJavet(
                """
                        (function () {
                          let trapCalls = 0;
                          function Ctor() {}
                          const proxyProto = new Proxy({}, {
                            getPrototypeOf() { trapCalls++; return Ctor.prototype },
                          });
                          const obj = Object.create(proxyProto);
                          const result = obj instanceof Ctor;
                          return 'result=' + result + ' trapCalls=' + trapCalls;
                        })()""");
    }

    @Test
    public void testProxyInThePrototypeChainStillSeesTheHasTrap() {
        assertStringWithJavet(
                """
                        (function () {
                          let trapKeys = [];
                          const proxyProto = new Proxy({}, {
                            has(target, key) { trapKeys.push(key); return key === 'viaTrap' },
                          });
                          const obj = Object.create(proxyProto);
                          return ('viaTrap' in obj) + ',' + ('absent' in obj) + ',' + trapKeys.join('|');
                        })()""");
    }

    @Test
    public void testSetPrototypeOfStillRejectsACycle() {
        assertStringWithJavet(
                """
                        (function () {
                          const a = {};
                          const b = Object.create(a);
                          try { Object.setPrototypeOf(a, b); return 'NO ERROR' }
                          catch (e) { return 'CAUGHT ' + e.name }
                        })()""");
    }

    @Test
    public void testShallowPrototypeChainsAreUnaffected() {
        JSObject shallow = buildChain(100);
        shallow.set(PropertyKey.fromString("own"), JSNumber.of(1));
        assertThat(shallow.get(PropertyKey.fromString("own"))).isEqualTo(JSNumber.of(1));
        assertThat(shallow.has(PropertyKey.fromString("own"))).isTrue();
        assertThat(shallow.has(PropertyKey.fromString("missing"))).isFalse();
        assertThat(shallow.get(PropertyKey.fromString("missing"))).isEqualTo(JSUndefined.INSTANCE);
    }

    @Test
    public void testTypedArrayInheritsTheDepthBoundedWalk() {
        // JSTypedArray overrides the walk; it must carry the depth through to super.
        assertStringWithJavet(
                """
                        (function () {
                          const a = new Int32Array([1, 2, 3]);
                          return (0 in a) + ',' + (3 in a) + ',' + ('length' in a) + ',' + ('map' in a);
                        })()""");
    }
}
