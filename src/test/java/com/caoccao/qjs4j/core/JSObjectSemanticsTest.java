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

public class JSObjectSemanticsTest extends BaseJavetTest {
    @Test
    public void testDeleteOnSealedAndFrozenObjects() {
        assertStringWithJavet(
                "(() => { const o = { a: 1 }; Object.seal(o); return String(Reflect.deleteProperty(o, 'missing')) + ',' + String(Reflect.deleteProperty(o, 'a')); })()",
                "(() => { const o = { a: 1 }; Object.freeze(o); return String(Reflect.deleteProperty(o, 'missing')) + ',' + String(Reflect.deleteProperty(o, 'a')); })()"
        );
    }

    @Test
    public void testOwnKeyOrdering() {
        assertStringWithJavet(
                "(() => { const o = {}; o.b = 1; o['2'] = 2; o.a = 3; o['1'] = 4; return JSON.stringify(Object.keys(o)); })()",
                "(() => { const o = {}; o.b = 1; o['2'] = 2; o.a = 3; o['1'] = 4; return JSON.stringify(Object.getOwnPropertyNames(o)); })()",
                "(() => { const o = {}; const s = Symbol('s'); o.b = 1; o['2'] = 2; o.a = 3; o['1'] = 4; o[s] = 5; return Reflect.ownKeys(o).map(k => typeof k === 'symbol' ? k.toString() : k).join(','); })()",
                "(() => { const o = {}; o.b = 1; o['2'] = 2; o.a = 3; o['1'] = 4; const keys = []; for (const key in o) if (Object.prototype.hasOwnProperty.call(o, key)) keys.push(key); return keys.join(','); })()"
        );
    }

    @Test
    public void testSetSemanticsWithPrototypeConstraints() {
        assertStringWithJavet(
                "(() => { const p = {}; Object.defineProperty(p, 'x', { value: 1, writable: false }); const o = Object.create(p); return String(Reflect.set(o, 'x', 2)) + ',' + String(o.hasOwnProperty('x')); })()",
                "(() => { const p = {}; Object.defineProperty(p, 'x', { get: function() { return 1; } }); const o = Object.create(p); return String(Reflect.set(o, 'x', 2)); })()",
                "(() => { const p = {}; Object.defineProperty(p, 'x', { value: 1, writable: false }); try { (function() { 'use strict'; const o = Object.create(p); o.x = 2; })(); return 'no-error'; } catch (e) { return e.name; } })()",
                "(() => { const p = {}; Object.defineProperty(p, 'x', { get: function() { return 1; } }); try { (function() { 'use strict'; const o = Object.create(p); o.x = 2; })(); return 'no-error'; } catch (e) { return e.name; } })()"
        );
    }

    @Test
    public void testSetWithExplicitReceiver() {
        assertStringWithJavet(
                "(() => { const target = {}; Object.defineProperty(target, 'x', { value: 1, writable: true }); const receiver = {}; Reflect.set(target, 'x', 7, receiver); return String(target.x) + ',' + String(receiver.x) + ',' + String(receiver.hasOwnProperty('x')); })()"
        );
    }
}
