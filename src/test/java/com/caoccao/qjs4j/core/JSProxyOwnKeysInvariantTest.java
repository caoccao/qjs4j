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

/**
 * The {@code ownKeys} proxy invariant check compares the trap result against the target's
 * non-configurable own keys through a {@code HashSet<PropertyKey>}.
 * <p>
 * The trap result is converted with {@code PropertyKey.fromString("0")} while the target's keys
 * arrive as {@code PropertyKey.fromIndex(0)}. Those two encodings used to be neither {@code equals}
 * nor hash-equal, so a perfectly conformant trap over a frozen array was rejected with a spurious
 * {@code TypeError: 'ownKeys' on proxy: trap result did not include '0'} — naming a key that was
 * right there in the set.
 */
public class JSProxyOwnKeysInvariantTest extends BaseJavetTest {

    @Test
    public void testDuplicateOwnKeysTrapResultReportsTypeError() {
        assertStringWithJavet(
                """
                        const proxy = new Proxy({}, { ownKeys() { return ['a', 'a'] } });
                        try { Object.getOwnPropertyNames(proxy); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testFrozenArrayTargetAcceptsConformantOwnKeysTrap() {
        assertStringWithJavet(
                """
                        const target = Object.freeze([1, 2]);
                        const proxy = new Proxy(target, { ownKeys() { return ['0', '1', 'length'] } });
                        Object.getOwnPropertyNames(proxy).join(',')""");
    }

    @Test
    public void testFrozenArrayTargetReportsMissingKey() {
        // The invariant must still fire when a key really is absent from the trap result.
        assertStringWithJavet(
                """
                        const target = Object.freeze([1, 2]);
                        const proxy = new Proxy(target, { ownKeys() { return ['1', 'length'] } });
                        try { Object.getOwnPropertyNames(proxy); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testFrozenArrayTargetSupportsObjectKeys() {
        assertStringWithJavet(
                """
                        const target = Object.freeze([1, 2, 3]);
                        const proxy = new Proxy(target, { ownKeys() { return ['0', '1', '2', 'length'] } });
                        JSON.stringify(Object.keys(proxy))""");
    }

    @Test
    public void testFrozenObjectWithNumericStringKeys() {
        assertStringWithJavet(
                """
                        const target = Object.freeze({ 0: 'a', 10: 'b', name: 'c' });
                        const proxy = new Proxy(target, { ownKeys() { return ['0', '10', 'name'] } });
                        Object.getOwnPropertyNames(proxy).join(',')""");
    }

    @Test
    public void testNonStringOwnKeysTrapResultReportsTypeError() {
        // A failing trap must surface its own TypeError. It used to return a null key list, which
        // made every caller of getOwnPropertyKeys() throw NullPointerException instead.
        assertStringWithJavet(
                """
                        const proxy = new Proxy({}, { ownKeys() { return [1] } });
                        try { Object.getOwnPropertyNames(proxy); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testOwnKeysTrapThatThrowsPropagatesTheThrownValue() {
        assertStringWithJavet(
                """
                        const proxy = new Proxy({}, { ownKeys() { throw new RangeError('from trap') } });
                        try { Object.getOwnPropertyNames(proxy); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name + ': ' + e.message }""");
    }

    @Test
    public void testRevokedProxyOwnKeysReportsTypeError() {
        assertStringWithJavet(
                """
                        const r = Proxy.revocable({}, {});
                        r.revoke();
                        try { Object.getOwnPropertyNames(r.proxy); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }""",
                """
                        const r = Proxy.revocable({}, {});
                        r.revoke();
                        try { Object.keys(r.proxy); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }""",
                """
                        const r = Proxy.revocable({}, {});
                        r.revoke();
                        try { JSON.stringify(r.proxy); 'NO ERROR' } catch (e) { 'CAUGHT ' + e.name }""");
    }

    @Test
    public void testSealedArrayTargetAcceptsConformantOwnKeysTrap() {
        assertStringWithJavet(
                """
                        const target = Object.seal([7, 8, 9]);
                        const proxy = new Proxy(target, { ownKeys() { return ['0', '1', '2', 'length'] } });
                        Object.getOwnPropertyNames(proxy).join(',')""");
    }
}
