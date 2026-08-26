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
 * Every prototype chain walk must terminate and must keep proxy traps in play.
 * <p>
 * The walk was recursive — so a proxy in the chain still gets its traps — and bounded by a depth
 * threshold, first 10,000 links and then 1,000. Neither number was right: 10,000 recursive frames
 * exhausted the Java stack before the guard fired, and 1,000 turned a valid program into a
 * {@code RangeError}, because {@code for (let i = 0; i < 1001; i++) o = Object.create(o)} is
 * legal and reading through it must work.
 * <p>
 * The walk is now a loop, so its length is bounded by memory rather than by a threshold, and only a
 * prototype that <em>replaces</em> the lookup — a Proxy, a deferred module namespace, a typed array
 * asked for a canonical numeric index — costs a Java frame. Termination on a corrupt graph comes
 * from Floyd's cycle detection rather than from a count.
 * <p>
 * The loop first ran only while the link's class was exactly {@code JSObject}, which left arrays,
 * functions and every other ordinary built-in subclass on the recursive path with the old
 * thousand-link cutoff: a chain of {@code Object.create} worked while the same chain of arrays was
 * a {@code RangeError}. Own lookup is a virtual call now, so those links are walked like any other.
 * <p>
 * The script-observable walks are asserted against V8. The cyclic chains are not: they are built
 * with the raw {@code setPrototype} embedder API, which has no JavaScript equivalent — no script
 * can construct a prototype cycle.
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
    public void testArrayElementsAreVisibleThroughAPrototypeChain() {
        assertStringWithJavet("""
                const base = [10, 20];
                const derived = Object.create(base);
                [derived[0], derived[1], String(derived[2]), 0 in derived, 5 in derived].join(',');""");
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
                .hasMessageContaining("Cyclic prototype chain");
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
                .hasMessageContaining("Cyclic prototype chain");
    }

    @Test
    @Timeout(120)
    public void testDeepChainOfAlternatingObjectsAndArrays() {
        assertStringWithJavet("""
                let o = { x: 'found' };
                for (let i = 0; i < 1002; i++) {
                  const link = i % 2 === 0 ? [] : {};
                  Object.setPrototypeOf(link, o);
                  o = link;
                }
                [o.x, 'x' in o, 'absent' in o, String(o.absent)].join(',');""");
    }

    @Test
    @Timeout(120)
    public void testDeepChainOfArrays() {
        // The review's reproducer. Every link is a JSArray, which used to mean one Java frame and
        // one unit of the thousand-link budget each.
        assertStringWithJavet("""
                let o = { x: 'found' };
                for (let i = 0; i < 1002; i++) {
                  const link = [];
                  Object.setPrototypeOf(link, o);
                  o = link;
                }
                [o.x, 'x' in o, 'absent' in o].join(',');""");
    }

    @Test
    @Timeout(120)
    public void testDeepChainOfFunctions() {
        assertStringWithJavet("""
                let o = { x: 'found' };
                for (let i = 0; i < 1002; i++) {
                  const link = function () {};
                  Object.setPrototypeOf(link, o);
                  o = link;
                }
                [o.x, 'x' in o, 'absent' in o].join(',');""");
    }

    @Test
    @Timeout(120)
    public void testDeepChainOfTypedArrays() {
        assertStringWithJavet("""
                let o = { x: 'found' };
                for (let i = 0; i < 1002; i++) {
                  const link = new Uint8Array(0);
                  Object.setPrototypeOf(link, o);
                  o = link;
                }
                [o.x, 'x' in o, 'absent' in o].join(',');""");
    }

    @Test
    @Timeout(60)
    public void testDeepPrototypeChainResolvesWithoutAThresholdError() {
        // 5,000 links is far past the old 1,000-link cutoff and far past what the Java stack would
        // hold if the walk still recursed per link.
        JSObject base = context.createJSObject();
        base.set(PropertyKey.fromString("found"), new JSString("value"));
        JSObject deep = base;
        for (int index = 0; index < 5000; index++) {
            JSObject child = context.createJSObject();
            child.setPrototype(deep);
            deep = child;
        }
        PropertyKey missing = PropertyKey.fromString("missing");
        PropertyKey found = PropertyKey.fromString("found");
        assertThat(deep.get(found)).isEqualTo(new JSString("value"));
        assertThat(deep.has(found)).isTrue();
        assertThat(deep.get(missing)).isEqualTo(JSUndefined.INSTANCE);
        assertThat(deep.has(missing)).as("`in` must behave exactly like a property read").isFalse();
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
    public void testStringWrapperInAPrototypeChainStillIndexesItsCharacters() {
        assertStringWithJavet("""
                const wrapper = new String('abc');
                const derived = Object.create(wrapper);
                [derived[0], derived[2], String(derived[9]), derived.length, 1 in derived].join(',');""");
    }

    @Test
    public void testTypedArrayElementsAreVisibleThroughAPrototypeChain() {
        // A canonical numeric index on an integer-indexed exotic object stops the walk, so an
        // out-of-range index reads undefined rather than continuing up the chain.
        assertStringWithJavet("""
                const base = new Uint8Array([1, 2, 3]);
                Object.getPrototypeOf(base).nine = 'inherited';
                const derived = Object.create(base);
                [derived[0], String(derived[9]), 0 in derived, 9 in derived].join(',');""");
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
