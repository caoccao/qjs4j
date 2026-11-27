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

import com.caoccao.qjs4j.builtins.buffer.*;
import com.caoccao.qjs4j.builtins.collection.*;
import com.caoccao.qjs4j.builtins.disposable.*;
import com.caoccao.qjs4j.builtins.iterator.IteratorPrototype;
import com.caoccao.qjs4j.builtins.object.*;
import com.caoccao.qjs4j.core.buffer.*;
import com.caoccao.qjs4j.core.collection.*;
import com.caoccao.qjs4j.core.disposable.*;
import java.util.function.Consumer;
import static com.caoccao.qjs4j.core.JSGlobalObject.*;

/** Global constructors that require new and have an ordinary, tagged prototype. */
enum GlobalConstructor {
    ARRAY_BUFFER(JSArrayBuffer.NAME, 1, JSConstructorType.ARRAY_BUFFER, ArrayBufferConstructor::call,
            IntrinsicFunctions.ARRAY_BUFFER_PROTOTYPE::install, constructor -> {
                defineMethod(constructor, "isView", 1, ArrayBufferConstructor::isView);

                defineSpeciesGetter(constructor, ArrayBufferConstructor::getSpecies);
            }),
    ASYNC_DISPOSABLE_STACK(JSAsyncDisposableStack.NAME, 0, JSConstructorType.ASYNC_DISPOSABLE_STACK,
            AsyncDisposableStackConstructor::call, prototype -> {
                installDisposable(prototype, "disposeAsync", PropertyKey.SYMBOL_ASYNC_DISPOSE,
                        AsyncDisposableStackPrototype::adopt, AsyncDisposableStackPrototype::defer,
                        AsyncDisposableStackPrototype::disposeAsync, AsyncDisposableStackPrototype::move,
                        AsyncDisposableStackPrototype::use, AsyncDisposableStackPrototype::getDisposed);
            }, null),
    DISPOSABLE_STACK(JSDisposableStack.NAME, 0, JSConstructorType.DISPOSABLE_STACK, DisposableStackConstructor::call,
            prototype -> {
                installDisposable(prototype, "dispose", PropertyKey.SYMBOL_DISPOSE, DisposableStackPrototype::adopt,
                        DisposableStackPrototype::defer, DisposableStackPrototype::dispose,
                        DisposableStackPrototype::move, DisposableStackPrototype::use,
                        DisposableStackPrototype::getDisposed);
            }, null),
    FINALIZATION_REGISTRY(JSFinalizationRegistry.NAME, 1, JSConstructorType.FINALIZATION_REGISTRY,
            FinalizationRegistryConstructor::call, prototype -> {
                defineMethod(prototype, "register", 2, FinalizationRegistryPrototype::register);
                defineMethod(prototype, "unregister", 1, FinalizationRegistryPrototype::unregister);
            }, null),
    MAP(JSMap.NAME, 0, JSConstructorType.MAP, MapConstructor::call, prototype -> {
        IntrinsicFunctions.MAP_PROTOTYPE.install(prototype);
        JSNativeFunction entriesFunction = new JSNativeFunction(prototype.getContext(), "entries", 0,
                IteratorPrototype::mapEntriesIterator);
        defineBuiltin(prototype, "entries", entriesFunction);
        defineMethod(prototype, "keys", 0, IteratorPrototype::mapKeysIterator);
        defineMethod(prototype, "values", 0, IteratorPrototype::mapValuesIterator);
        prototype.defineProperty(PropertyKey.SYMBOL_ITERATOR, entriesFunction,
                PropertyDescriptor.DataState.ConfigurableWritable);

        defineGetter(prototype, "size", MapPrototype::getSize);
    }, constructor -> {
        defineMethod(constructor, "groupBy", 2, MapConstructor::groupBy);
        defineSpeciesGetter(constructor, MapConstructor::getSpecies);
    }),
    SET(JSSet.NAME, 0, JSConstructorType.SET, SetConstructor::call, prototype -> {
        IntrinsicFunctions.SET_PROTOTYPE.install(prototype);

        JSNativeFunction valuesFunction = new JSNativeFunction(prototype.getContext(), "values", 0,
                SetPrototype::values);
        defineBuiltin(prototype, "values", valuesFunction);
        prototype.defineProperty(PropertyKey.KEYS, valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.SYMBOL_ITERATOR, valuesFunction,
                PropertyDescriptor.DataState.ConfigurableWritable);

        defineGetter(prototype, "size", SetPrototype::getSize);
    }, constructor -> {
        defineMethod(constructor, "groupBy", 2, SetConstructor::groupBy);
        defineSpeciesGetter(constructor, SetConstructor::getSpecies);
    }),
    SHADOW_REALM(JSShadowRealm.NAME, 0, null, ShadowRealmConstructor::call, prototype -> {
        defineMethod(prototype, "evaluate", 1, ShadowRealmPrototype::evaluate);
        defineMethod(prototype, "importValue", 2, ShadowRealmPrototype::importValue);
    }, null),
    SHARED_ARRAY_BUFFER(JSSharedArrayBuffer.NAME, 1, JSConstructorType.SHARED_ARRAY_BUFFER,
            SharedArrayBufferConstructor::call, IntrinsicFunctions.SHARED_ARRAY_BUFFER_PROTOTYPE::install,
            constructor -> {
                defineSpeciesGetter(constructor, SharedArrayBufferConstructor::getSpecies);
            }),
    WEAK_MAP(JSWeakMap.NAME, 0, JSConstructorType.WEAK_MAP, WeakMapConstructor::call,
            IntrinsicFunctions.WEAK_MAP_PROTOTYPE::install, null),
    WEAK_REF(JSWeakRef.NAME, 1, JSConstructorType.WEAK_REF, WeakRefConstructor::call, prototype -> {
        defineMethod(prototype, "deref", 0, WeakRefPrototype::deref);
    }, null),
    WEAK_SET(JSWeakSet.NAME, 0, JSConstructorType.WEAK_SET, WeakSetConstructor::call,
            IntrinsicFunctions.WEAK_SET_PROTOTYPE::install, null);

    private final JSNativeCallback call;
    private final int length;
    private final String name;
    private final Consumer<JSObject> prototypeProperties;
    private final Consumer<JSObject> staticProperties;
    private final JSConstructorType type;

    GlobalConstructor(String name, int length, JSConstructorType type, JSNativeCallback call,
            Consumer<JSObject> prototypeProperties, Consumer<JSObject> staticProperties) {
        this.name = name;
        this.length = length;
        this.type = type;
        this.call = call;
        this.prototypeProperties = prototypeProperties;
        this.staticProperties = staticProperties;
    }

    void install(JSObject global) {
        JSContext context = global.getContext();
        JSObject prototype = context.createJSObject();
        prototypeProperties.accept(prototype);
        defineToStringTag(prototype, name);
        JSNativeFunction constructor = new JSNativeFunction(context, name, length, call, true, true);
        linkConstructor(constructor, prototype, type);
        if (staticProperties != null) {
            staticProperties.accept(constructor);
        }
        defineBuiltin(global, name, constructor);
    }

    private static void installDisposable(JSObject prototype, String name, PropertyKey alias, JSNativeCallback adopt,
            JSNativeCallback defer, JSNativeCallback dispose, JSNativeCallback move, JSNativeCallback use,
            JSNativeCallback disposed) {
        defineMethod(prototype, "adopt", 2, adopt);
        defineMethod(prototype, "defer", 1, defer);
        JSNativeFunction function = new JSNativeFunction(prototype.getContext(), name, 0, dispose);
        defineBuiltin(prototype, name, function);
        defineMethod(prototype, "move", 0, move);
        defineMethod(prototype, "use", 1, use);
        prototype.defineProperty(alias, function, PropertyDescriptor.DataState.ConfigurableWritable);
        defineGetter(prototype, "disposed", disposed);
    }
}
