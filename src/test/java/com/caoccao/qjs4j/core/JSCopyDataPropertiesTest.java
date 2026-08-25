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
 * Object spread ({@code {...src}}) and object rest ({@code {a, ...rest}}) must copy properties with
 * CreateDataProperty, not with {@code [[Set]]}.
 * <p>
 * ES2024 7.3.25 CopyDataProperties step 4.c.ii specifies CreateDataPropertyOrThrow — an own
 * property definition that ignores the prototype chain. Copying with {@code [[Set]]} instead let a
 * setter or a non-writable data property on {@code Object.prototype} intercept the copy: the setter
 * ran (an unexpected side channel with prototype-pollution flavour) and the property never landed
 * on the result object.
 * <p>
 * {@code Object.assign} genuinely does use {@code [[Set]]}, so it is asserted here too — the two
 * must stay distinguishable.
 */
public class JSCopyDataPropertiesTest extends BaseJavetTest {

    @Test
    public void testObjectAssignStillUsesSet() {
        assertStringWithJavet(
                """
                        let hit = 'no';
                        Object.defineProperty(Object.prototype, 'assigned', {
                            set() { hit = 'yes' },
                            configurable: true,
                        });
                        const target = Object.assign({}, { assigned: 5 });
                        try {
                            'hit=' + hit + ' value=' + target.assigned;
                        } finally {
                            delete Object.prototype.assigned;
                        }""");
    }

    @Test
    public void testSpreadCopiesGetterResultOnce() {
        assertStringWithJavet(
                """
                        let reads = 0;
                        const source = { get counted() { reads++; return 7 } };
                        const target = { ...source };
                        'reads=' + reads + ' value=' + target.counted""");
    }

    @Test
    public void testSpreadIgnoresPrototypeAccessor() {
        assertStringWithJavet(
                """
                        let hit = 'no';
                        Object.defineProperty(Object.prototype, 'spreadAccessor', {
                            set() { hit = 'yes' },
                            configurable: true,
                        });
                        const target = { ...{ spreadAccessor: 5 } };
                        try {
                            'hit=' + hit + ' value=' + target.spreadAccessor;
                        } finally {
                            delete Object.prototype.spreadAccessor;
                        }""");
    }

    @Test
    public void testSpreadIgnoresPrototypeNonWritableDataProperty() {
        assertStringWithJavet(
                """
                        Object.defineProperty(Object.prototype, 'spreadFrozen', {
                            value: 0,
                            writable: false,
                            configurable: true,
                        });
                        const target = { ...{ spreadFrozen: 5 } };
                        try {
                            'value=' + target.spreadFrozen;
                        } finally {
                            delete Object.prototype.spreadFrozen;
                        }""");
    }

    @Test
    public void testSpreadResultPropertiesAreFullyConfigurable() {
        assertStringWithJavet(
                """
                        const target = { ...{ a: 1 } };
                        const descriptor = Object.getOwnPropertyDescriptor(target, 'a');
                        JSON.stringify(descriptor)""");
    }

    @Test
    public void testSpreadWithIndexKeysIgnoresPrototypeAccessor() {
        assertStringWithJavet(
                """
                        let hit = 'no';
                        Object.defineProperty(Object.prototype, '0', {
                            set() { hit = 'yes' },
                            configurable: true,
                        });
                        const target = { ...['x'] };
                        try {
                            'hit=' + hit + ' value=' + target[0];
                        } finally {
                            delete Object.prototype['0'];
                        }""");
    }

    @Test
    public void testObjectRestIgnoresPrototypeAccessor() {
        assertStringWithJavet(
                """
                        let hit = 'no';
                        Object.defineProperty(Object.prototype, 'restAccessor', {
                            set() { hit = 'yes' },
                            configurable: true,
                        });
                        const { a, ...rest } = { a: 1, restAccessor: 5 };
                        try {
                            'hit=' + hit + ' a=' + a + ' value=' + rest.restAccessor;
                        } finally {
                            delete Object.prototype.restAccessor;
                        }""");
    }

    @Test
    public void testObjectRestIgnoresPrototypeNonWritableDataProperty() {
        assertStringWithJavet(
                """
                        Object.defineProperty(Object.prototype, 'restFrozen', {
                            value: 0,
                            writable: false,
                            configurable: true,
                        });
                        const { a, ...rest } = { a: 1, restFrozen: 5 };
                        try {
                            'value=' + rest.restFrozen;
                        } finally {
                            delete Object.prototype.restFrozen;
                        }""");
    }
}
