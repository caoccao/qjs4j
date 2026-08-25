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

package com.caoccao.qjs4j.embedder;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An exotic {@link JSObject} written outside {@code com.caoccao.qjs4j.core} must be honoured by
 * every engine path that looks at properties.
 * <p>
 * The extension points moved: the engine now dispatches on the protected
 * {@code getOwnPropertyDescriptorRaw} and {@code has(key, depth)} hooks, while the public
 * {@code getOwnPropertyDescriptor(key)}, {@code isOwnPropertyEnumerable(key)} and {@code has(key)}
 * became views over them. A subclass that overrode only the old public methods would still compile
 * and still answer direct calls, but {@code Object.keys}, {@code Object.assign},
 * {@code for}-{@code in} and inherited {@code in} would all bypass it — a behavioural break with no
 * compile-time signal. The public methods are therefore {@code final}, so that mistake is a
 * compiler error, and this test pins the supported hooks to the behaviour they must produce.
 * <p>
 * This test lives outside the {@code core} package deliberately: {@code protected} access from a
 * different package is exactly the embedder's position.
 */
public class JSObjectExoticSubclassTest extends BaseTest {

    private static final PropertyKey VIRTUAL = PropertyKey.fromString("virtual");
    private static final JSNumber VIRTUAL_VALUE = JSNumber.of(42);

    private VirtualPropertyObject install(String globalName) {
        VirtualPropertyObject exotic = new VirtualPropertyObject(context);
        exotic.setPrototype(context.getObjectPrototype());
        context.getGlobalObject().set(PropertyKey.fromString(globalName), exotic);
        return exotic;
    }

    @Test
    public void testDescriptorViewReturnsACopy() {
        // The public view copies, so a caller cannot rewrite the subclass's attributes through it.
        VirtualPropertyObject exotic = install("exotic");
        PropertyDescriptor first = exotic.getOwnPropertyDescriptor(VIRTUAL);
        first.setEnumerable(false);
        assertThat(exotic.getOwnPropertyDescriptor(VIRTUAL).isEnumerable()).isTrue();
    }

    @Test
    public void testDirectDescriptorAccessSeesTheOverride() {
        VirtualPropertyObject exotic = install("exotic");
        PropertyDescriptor descriptor = exotic.getOwnPropertyDescriptor(VIRTUAL);
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getValue()).isEqualTo(VIRTUAL_VALUE);
        assertThat(descriptor.isEnumerable()).isTrue();
    }

    @Test
    public void testDirectHasSeesTheOverride() {
        VirtualPropertyObject exotic = install("exotic");
        assertThat(exotic.has(VIRTUAL)).isTrue();
        assertThat(exotic.has(PropertyKey.fromString("absent"))).isFalse();
    }

    @Test
    public void testEnumerabilityViewSeesTheOverride() {
        VirtualPropertyObject exotic = install("exotic");
        assertThat(exotic.isOwnPropertyEnumerable(VIRTUAL)).isTrue();
        assertThat(exotic.isOwnPropertyEnumerable(PropertyKey.fromString("absent"))).isFalse();
    }

    @Test
    public void testForInSeesTheOverride() {
        install("exotic");
        assertThat(context.eval(
                        "(function () { const seen = []; for (const k in exotic) seen.push(k); return seen.join(',') })()")
                .toString())
                .isEqualTo("virtual");
    }

    @Test
    public void testInOperatorSeesTheOverride() {
        install("exotic");
        assertThat(context.eval("('virtual' in exotic) + ',' + ('absent' in exotic)").toString())
                .isEqualTo("true,false");
    }

    @Test
    public void testInheritedInOperatorSeesTheOverride() {
        VirtualPropertyObject exotic = install("exotic");
        JSObject child = context.createJSObject();
        child.setPrototype(exotic);
        context.getGlobalObject().set(PropertyKey.fromString("child"), child);
        assertThat(context.eval("('virtual' in child) + ',' + ('absent' in child)").toString())
                .isEqualTo("true,false");
    }

    @Test
    public void testObjectAssignSeesTheOverride() {
        install("exotic");
        assertThat(context.eval("JSON.stringify(Object.assign({}, exotic))").toString())
                .isEqualTo("{\"virtual\":42}");
    }

    @Test
    public void testObjectGetOwnPropertyDescriptorSeesTheOverride() {
        install("exotic");
        assertThat(context.eval("Object.getOwnPropertyDescriptor(exotic, 'virtual').value").toString())
                .isEqualTo("42");
    }

    @Test
    public void testObjectKeysSeesTheOverride() {
        install("exotic");
        assertThat(context.eval("JSON.stringify(Object.keys(exotic))").toString())
                .isEqualTo("[\"virtual\"]");
    }

    @Test
    public void testPropertyReadSeesTheOverride() {
        install("exotic");
        assertThat(context.eval("exotic.virtual").toString()).isEqualTo("42");
    }

    /**
     * An object with one property that exists in no physical storage.
     */
    private static final class VirtualPropertyObject extends JSObject {
        private VirtualPropertyObject(JSContext context) {
            super(context);
        }

        @Override
        protected PropertyDescriptor getOwnPropertyDescriptorRaw(PropertyKey key) {
            if (VIRTUAL.equals(key)) {
                return PropertyDescriptor.dataDescriptor(
                        VIRTUAL_VALUE,
                        PropertyDescriptor.DataState.All);
            }
            return super.getOwnPropertyDescriptorRaw(key);
        }

        @Override
        public List<PropertyKey> getOwnPropertyKeys() {
            List<PropertyKey> keys = new ArrayList<>(super.getOwnPropertyKeys());
            keys.add(VIRTUAL);
            return keys;
        }

        @Override
        protected JSValue getWithReceiver(PropertyKey key, JSValue receiver, int depth) {
            if (VIRTUAL.equals(key)) {
                return VIRTUAL_VALUE;
            }
            return super.getWithReceiver(key, receiver, depth);
        }

        @Override
        public boolean hasOwnProperty(PropertyKey key) {
            return VIRTUAL.equals(key) || super.hasOwnProperty(key);
        }
    }
}
