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

import static org.assertj.core.api.Assertions.assertThat;

class RealmIntrinsicsTest extends BaseTest {
    @Test
    void defaultArraySpeciesChecksTheUint32LengthLimit() {
        JSArray array = context.createJSArray();
        array.set(PropertyKey.CONSTRUCTOR, JSUndefined.INSTANCE);
        for (JSObject original : new JSObject[]{array, context.createJSObject()}) {
            assertThat(context.createJSArraySpecies(original, 0xFFFFFFFFL).asArray().orElseThrow().getLength())
                    .isEqualTo(0xFFFFFFFFL);
            assertPendingRangeError(context.createJSArraySpecies(original, 0x1_0000_0000L));
        }
    }

    @Test
    void defaultPrototypeComesFromTheConstructorsRealm() {
        try (JSContext foreign = context.getRuntime().createContext()) {
            JSFunction constructor = (JSFunction) foreign.eval("(function replacement() {})");
            constructor.set(PropertyKey.PROTOTYPE, JSNull.INSTANCE);
            RealmIntrinsics intrinsics = new RealmIntrinsics(context);
            JSObject arrayConstructor = (JSObject) foreign.getGlobalObject().get("Array");
            assertThat(intrinsics.getPrototypeFromConstructor(constructor, "Array"))
                    .isSameAs(arrayConstructor.get(PropertyKey.PROTOTYPE));
            assertThat(intrinsics.getPrototypeFromConstructor(constructor, "Missing"))
                    .isSameAs(foreign.getObjectPrototype());
            constructor.set(PropertyKey.PROTOTYPE, context.getObjectPrototype());
            assertThat(intrinsics.getPrototypeFromConstructor(constructor, "Array"))
                    .isSameAs(context.getObjectPrototype());
        }
    }

    @Test
    void defaultPrototypeNameFollowsTheConstructorSlot() {
        JSNativeFunction function = function(context, "Array");
        assertThat(context.getIntrinsicDefaultPrototypeName(function)).isEqualTo("Object");
        function.setConstructorType(JSConstructorType.ARRAY);
        assertThat(context.getIntrinsicDefaultPrototypeName(function)).isEqualTo("Array");
        function.setConstructorType(JSConstructorType.PROXY);
        assertThat(context.getIntrinsicDefaultPrototypeName(function)).isEqualTo("Object");
        function.setConstructorType(JSConstructorType.TYPED_ARRAY_BIGINT64);
        assertThat(context.getIntrinsicDefaultPrototypeName(function)).isEqualTo("BigInt64Array");
    }

    @Test
    void foreignIntrinsicArraySpeciesUsesTheRequestingRealm() {
        try (JSContext foreign = context.getRuntime().createContext()) {
            JSArray original = foreign.createJSArray();
            JSArray result = context.createJSArraySpecies(original, 3).asArray().orElseThrow();
            assertThat(result.getLength()).isEqualTo(3);
            assertThat(result.getPrototype()).isSameAs(context.createJSArray().getPrototype());
            assertThat(result.getPrototype()).isNotSameAs(original.getPrototype());
        }
    }

    @Test
    void generatorAndAsyncFallbacksHandleMissingIntrinsics() {
        RealmIntrinsics intrinsics = new RealmIntrinsics(context);
        assertThat(intrinsics.getIntrinsicPrototype(context, "GeneratorFunction"))
                .isSameAs(context.getGeneratorFunctionPrototype());
        assertThat(intrinsics.getIntrinsicPrototype(context, "AsyncGeneratorFunction"))
                .isSameAs(context.getAsyncGeneratorFunctionPrototype());
        assertThat(intrinsics.getIntrinsicPrototype(context, "AsyncFunction"))
                .isSameAs(context.getAsyncFunctionConstructor().get(PropertyKey.PROTOTYPE));

        context.setGeneratorFunctionPrototype(null);
        context.setAsyncGeneratorFunctionPrototype(null);
        context.setAsyncFunctionConstructor(null);
        assertThat(intrinsics.getIntrinsicPrototype(context, "GeneratorFunction"))
                .isSameAs(context.getObjectPrototype());
        assertThat(intrinsics.getIntrinsicPrototype(context, "AsyncGeneratorFunction"))
                .isSameAs(context.getObjectPrototype());
        JSObject functionConstructor = (JSObject) context.getGlobalObject().get("Function");
        assertThat(intrinsics.getIntrinsicPrototype(context, "AsyncFunction"))
                .isSameAs(functionConstructor.get(PropertyKey.PROTOTYPE));
        context.getGlobalObject().set("Function", JSUndefined.INSTANCE);
        assertThat(intrinsics.getIntrinsicPrototype(context, "AsyncFunction")).isSameAs(context.getObjectPrototype());
    }

    @Test
    void unmarkedFunctionConstructorsKeepTheirIntrinsicNames() {
        for (String name : new String[]{"Function", "GeneratorFunction", "AsyncFunction", "AsyncGeneratorFunction",
                "Iterator"}) {
            assertThat(context.getIntrinsicDefaultPrototypeName(function(context, name))).as(name).isEqualTo(name);
        }
    }

    private static JSNativeFunction function(JSContext realm, String name) {
        return new JSNativeFunction(realm, name, 0, (ctx, receiver, args) -> JSUndefined.INSTANCE);
    }
}
