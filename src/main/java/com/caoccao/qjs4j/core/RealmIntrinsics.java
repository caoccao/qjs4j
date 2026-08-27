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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * One realm's intrinsic objects, and the rules for finding the right prototype for a new object.
 * <p>
 * QuickJS keeps these on {@code struct JSContext} as {@code class_proto[]} plus a handful of named
 * fields; this is the same set, held together rather than scattered through the context. It holds
 * the prototypes that are looked up often enough to be worth caching ({@code Object},
 * {@code Date}, {@code Promise}, {@code RegExp}), the ones that are deliberately not reachable from
 * the global object at all (the generator and async-function chains, {@code %ThrowTypeError%}), and
 * the shared iterator prototypes keyed by their {@code Symbol.toStringTag}.
 * <p>
 * It also implements OrdinaryCreateFromConstructor's prototype lookup —
 * {@code getPrototypeFromConstructor} and the {@code getIntrinsicDefaultPrototypeName} mapping
 * behind it — and GetFunctionRealm, both of which have to be able to ask a <em>different</em> realm
 * for its intrinsics, which is why those take a {@link JSContext} rather than reading this one.
 */
final class RealmIntrinsics {
    private final JSContext context;
    // Shared iterator prototypes by toStringTag (e.g., "Array Iterator" → %ArrayIteratorPrototype%)
    private final Map<String, JSObject> iteratorPrototypes = new HashMap<>();
    // Internal constructor references (not exposed in global scope)
    private JSObject asyncFunctionConstructor;
    private JSObject asyncGeneratorFunctionPrototype;
    // Async generator prototype chain (not exposed in global scope)
    private JSObject asyncGeneratorPrototype;
    private JSObject cachedDatePrototype;
    // Cached Object.prototype for fast internal object creation
    private JSObject cachedObjectPrototype;
    private JSObject cachedPromisePrototype;
    private JSObject cachedRegExpConstructor;
    private JSObject cachedRegExpPrototype;
    // Generator prototype chain (not exposed in global scope)
    private JSObject generatorFunctionPrototype;
    // The %ThrowTypeError% intrinsic (shared across Function.prototype and strict arguments)
    private JSNativeFunction throwTypeErrorIntrinsic;

    RealmIntrinsics(JSContext context) {
        this.context = context;
    }

    /**
     * Cache the prototypes that are looked up often enough to be worth it.
     * <p>
     * Called once the global object has been populated: each of these is reachable through
     * {@code globalThis} and would otherwise cost two property lookups per allocation.
     *
     * @param globalObject the realm's global object
     */
    void cacheFromGlobalObject(JSObject globalObject) {
        JSValue objectCtor = globalObject.get(JSObject.NAME);
        if (objectCtor instanceof JSObject objCtorObj) {
            JSValue proto = objCtorObj.get(PropertyKey.PROTOTYPE);
            if (proto instanceof JSObject protoObj) {
                cachedObjectPrototype = protoObj;
            }
        }
        JSValue dateCtor = globalObject.get(JSDate.NAME);
        if (dateCtor instanceof JSObject dateCtorObj) {
            JSValue proto = dateCtorObj.get(PropertyKey.PROTOTYPE);
            if (proto instanceof JSObject protoObj) {
                cachedDatePrototype = protoObj;
            }
        }
        JSValue promiseCtor = globalObject.get(JSPromise.NAME);
        if (promiseCtor instanceof JSObject promiseCtorObject) {
            JSValue proto = promiseCtorObject.get(PropertyKey.PROTOTYPE);
            if (proto instanceof JSObject protoObj) {
                cachedPromisePrototype = protoObj;
            }
        }
        JSValue regExpCtor = globalObject.get(JSRegExp.NAME);
        if (regExpCtor instanceof JSObject regExpCtorObject) {
            cachedRegExpConstructor = regExpCtorObject;
            JSValue proto = regExpCtorObject.get(PropertyKey.PROTOTYPE);
            if (proto instanceof JSObject protoObj) {
                cachedRegExpPrototype = protoObj;
            }
        }
    }

    /**
     * Drop every intrinsic this holds.
     * <p>
     * Called from {@link JSContext#close()}: each cached prototype is a live handle on the realm.
     */
    void release() {
        iteratorPrototypes.clear();
        asyncFunctionConstructor = null;
        asyncGeneratorFunctionPrototype = null;
        asyncGeneratorPrototype = null;
        cachedDatePrototype = null;
        cachedObjectPrototype = null;
        cachedPromisePrototype = null;
        cachedRegExpConstructor = null;
        cachedRegExpPrototype = null;
        generatorFunctionPrototype = null;
        throwTypeErrorIntrinsic = null;
    }

    /**
     * Get the AsyncFunction constructor (internal use only).
     * Used for setting up prototype chains for async functions.
     */
    JSObject getAsyncFunctionConstructor() {
        return asyncFunctionConstructor;
    }

    JSObject getAsyncGeneratorFunctionPrototype() {
        return asyncGeneratorFunctionPrototype;
    }

    JSObject getAsyncGeneratorPrototype() {
        return asyncGeneratorPrototype;
    }

    JSObject getCachedDatePrototype() {
        return cachedDatePrototype;
    }

    JSObject getCachedPromisePrototype() {
        return cachedPromisePrototype;
    }

    JSObject getCachedRegExpConstructor() {
        return cachedRegExpConstructor;
    }

    JSObject getCachedRegExpPrototype() {
        return cachedRegExpPrototype;
    }

    JSContext getFunctionRealm(JSObject constructor) {
        return getFunctionRealmInternal(constructor, 0);
    }

    JSContext getFunctionRealmInternal(JSValue value, int depth) {
        if (depth > 1000) {
            context.throwTypeError("too much recursion");
            return context;
        }
        if (value instanceof JSBoundFunction boundFunction) {
            return getFunctionRealmInternal(boundFunction.getTarget(), depth + 1);
        }
        if (value instanceof JSProxy proxy) {
            if (proxy.isRevoked()) {
                context.throwTypeError("Cannot perform 'get' on a proxy that has been revoked");
                return context;
            }
            return getFunctionRealmInternal(proxy.getTarget(), depth + 1);
        }
        if (value instanceof JSFunction function) {
            JSContext functionContext = function.getHomeContext();
            if (functionContext != null) {
                return functionContext;
            }
        }
        return context;
    }

    /**
     * Get the GeneratorFunction prototype (internal use only).
     * Used for setting up prototype chains for generator functions.
     */
    JSObject getGeneratorFunctionPrototype() {
        return generatorFunctionPrototype;
    }

    String getIntrinsicDefaultPrototypeName(JSFunction function) {
        JSConstructorType constructorType = function.getConstructorType();
        if (constructorType != null) {
            return switch (constructorType) {
                case AGGREGATE_ERROR -> JSAggregateError.NAME;
                case ARRAY -> JSArray.NAME;
                case ARRAY_BUFFER -> JSArrayBuffer.NAME;
                case ASYNC_DISPOSABLE_STACK -> JSAsyncDisposableStack.NAME;
                case BIG_INT_OBJECT -> JSBigIntObject.NAME;
                case BOOLEAN_OBJECT -> JSBooleanObject.NAME;
                case DATA_VIEW -> JSDataView.NAME;
                case DATE -> JSDate.NAME;
                case DISPOSABLE_STACK -> JSDisposableStack.NAME;
                case ERROR -> JSError.NAME;
                case EVAL_ERROR -> JSEvalError.NAME;
                case FINALIZATION_REGISTRY -> JSFinalizationRegistry.NAME;
                case MAP -> JSMap.NAME;
                case NUMBER_OBJECT -> JSNumberObject.NAME;
                case PROMISE -> JSPromise.NAME;
                case PROXY -> JSObject.NAME;
                case RANGE_ERROR -> JSRangeError.NAME;
                case REFERENCE_ERROR -> JSReferenceError.NAME;
                case REGEXP -> JSRegExp.NAME;
                case SET -> JSSet.NAME;
                case SHARED_ARRAY_BUFFER -> JSSharedArrayBuffer.NAME;
                case STRING_OBJECT -> JSStringObject.NAME;
                case SUPPRESSED_ERROR -> JSSuppressedError.NAME;
                case SYMBOL_OBJECT -> JSSymbolObject.NAME;
                case SYNTAX_ERROR -> JSSyntaxError.NAME;
                case TYPED_ARRAY_BIGINT64 -> JSBigInt64Array.NAME;
                case TYPED_ARRAY_BIGUINT64 -> JSBigUint64Array.NAME;
                case TYPED_ARRAY_FLOAT16 -> JSFloat16Array.NAME;
                case TYPED_ARRAY_FLOAT32 -> JSFloat32Array.NAME;
                case TYPED_ARRAY_FLOAT64 -> JSFloat64Array.NAME;
                case TYPED_ARRAY_INT16 -> JSInt16Array.NAME;
                case TYPED_ARRAY_INT32 -> JSInt32Array.NAME;
                case TYPED_ARRAY_INT8 -> JSInt8Array.NAME;
                case TYPED_ARRAY_UINT16 -> JSUint16Array.NAME;
                case TYPED_ARRAY_UINT32 -> JSUint32Array.NAME;
                case TYPED_ARRAY_UINT8 -> JSUint8Array.NAME;
                case TYPED_ARRAY_UINT8_CLAMPED -> JSUint8ClampedArray.NAME;
                case TYPE_ERROR -> JSTypeError.NAME;
                case URI_ERROR -> JSURIError.NAME;
                case WEAK_MAP -> JSWeakMap.NAME;
                case WEAK_REF -> JSWeakRef.NAME;
                case WEAK_SET -> JSWeakSet.NAME;
            };
        }
        if (function instanceof JSClass) {
            return JSObject.NAME;
        }
        String functionName = function.getName();
        if (JSFunction.NAME.equals(functionName)) {
            return JSFunction.NAME;
        }
        if ("GeneratorFunction".equals(functionName)) {
            return "GeneratorFunction";
        }
        if ("AsyncFunction".equals(functionName)) {
            return "AsyncFunction";
        }
        if ("AsyncGeneratorFunction".equals(functionName)) {
            return "AsyncGeneratorFunction";
        }
        if (JSIterator.NAME.equals(functionName)) {
            return JSIterator.NAME;
        }
        return JSObject.NAME;
    }

    JSObject getIntrinsicPrototype(JSContext realmContext, String intrinsicDefaultPrototypeName) {
        if (JSObject.NAME.equals(intrinsicDefaultPrototypeName)) {
            return realmContext.getObjectPrototype();
        }
        if ("GeneratorFunction".equals(intrinsicDefaultPrototypeName)) {
            JSObject generatorFunctionPrototype = realmContext.getGeneratorFunctionPrototype();
            if (generatorFunctionPrototype != null) {
                return generatorFunctionPrototype;
            }
            return realmContext.getObjectPrototype();
        }
        if ("AsyncGeneratorFunction".equals(intrinsicDefaultPrototypeName)) {
            JSObject asyncGeneratorFunctionPrototype = realmContext.getAsyncGeneratorFunctionPrototype();
            if (asyncGeneratorFunctionPrototype != null) {
                return asyncGeneratorFunctionPrototype;
            }
            return realmContext.getObjectPrototype();
        }
        if ("AsyncFunction".equals(intrinsicDefaultPrototypeName)) {
            JSObject asyncFunctionConstructor = realmContext.getAsyncFunctionConstructor();
            if (asyncFunctionConstructor != null) {
                JSValue asyncFunctionPrototype = asyncFunctionConstructor.get(PropertyKey.PROTOTYPE);
                if (asyncFunctionPrototype instanceof JSObject asyncFunctionPrototypeObject) {
                    return asyncFunctionPrototypeObject;
                }
            }
            JSValue fallbackFunctionConstructor = realmContext.getGlobalObject().get(JSFunction.NAME);
            if (fallbackFunctionConstructor instanceof JSObject fallbackFunctionObject) {
                JSValue fallbackFunctionPrototype = fallbackFunctionObject.get(PropertyKey.PROTOTYPE);
                if (fallbackFunctionPrototype instanceof JSObject fallbackFunctionPrototypeObject) {
                    return fallbackFunctionPrototypeObject;
                }
            }
            return realmContext.getObjectPrototype();
        }

        JSValue intrinsicConstructor = realmContext.getGlobalObject().get(intrinsicDefaultPrototypeName);
        if (intrinsicConstructor instanceof JSObject intrinsicObject) {
            JSValue intrinsicPrototype = intrinsicObject.get(PropertyKey.PROTOTYPE);
            if (intrinsicPrototype instanceof JSObject intrinsicPrototypeObject) {
                return intrinsicPrototypeObject;
            }
        }
        return realmContext.getObjectPrototype();
    }

    JSObject getIteratorPrototype(String tag) {
        return iteratorPrototypes.get(tag);
    }

    Collection<JSObject> getIteratorPrototypes() {
        return iteratorPrototypes.values();
    }

    JSObject getObjectPrototype() {
        return cachedObjectPrototype;
    }

    JSObject getPrototypeFromConstructor(JSObject constructor, String intrinsicDefaultPrototypeName) {
        JSValue prototype = constructor.get(PropertyKey.PROTOTYPE);
        if (context.hasPendingException()) {
            return null;
        }
        if (prototype instanceof JSObject prototypeObject) {
            return prototypeObject;
        }

        JSContext functionRealm = getFunctionRealm(constructor);
        if (context.hasPendingException()) {
            return null;
        }
        return getIntrinsicPrototype(functionRealm, intrinsicDefaultPrototypeName);
    }

    /**
     * Get the %ThrowTypeError% intrinsic function.
     * This is the single shared function used for Function.prototype caller/arguments
     * and strict mode arguments.callee per ES spec.
     */
    JSNativeFunction getThrowTypeErrorIntrinsic() {
        return throwTypeErrorIntrinsic;
    }

    /**
     * Register a module in the cache.
     */
    void registerIteratorPrototype(String tag, JSObject prototype) {
        iteratorPrototypes.put(tag, prototype);
    }

    /**
     * Set the AsyncFunction constructor (internal use only).
     * Called during global object initialization.
     */
    void setAsyncFunctionConstructor(JSObject asyncFunctionConstructor) {
        this.asyncFunctionConstructor = asyncFunctionConstructor;
    }

    void setAsyncGeneratorFunctionPrototype(JSObject asyncGeneratorFunctionPrototype) {
        this.asyncGeneratorFunctionPrototype = asyncGeneratorFunctionPrototype;
    }

    void setAsyncGeneratorPrototype(JSObject asyncGeneratorPrototype) {
        this.asyncGeneratorPrototype = asyncGeneratorPrototype;
    }

    /**
     * Set the GeneratorFunction prototype (internal use only).
     * Called during global object initialization.
     */
    void setGeneratorFunctionPrototype(JSObject generatorFunctionPrototype) {
        this.generatorFunctionPrototype = generatorFunctionPrototype;
    }

    /**
     * Set the %ThrowTypeError% intrinsic function.
     * Called during global object initialization.
     */
    void setThrowTypeErrorIntrinsic(JSNativeFunction throwTypeError) {
        this.throwTypeErrorIntrinsic = throwTypeError;
    }

    boolean transferPrototype(JSObject receiver, String constructorName) {
        JSValue constructor = context.getGlobalObject().get(constructorName);
        if (constructor instanceof JSObject jsObject) {
            return transferPrototype(receiver, jsObject);
        }
        return false;
    }

    boolean transferPrototype(JSObject receiver, JSObject constructor) {
        JSValue prototype = constructor.get(PropertyKey.PROTOTYPE);
        if (prototype instanceof JSObject) {
            receiver.setPrototype((JSObject) prototype);
            return true;
        }
        return false;
    }

    /**
     * Transfer prototype using Get(constructor, "prototype") with full JS semantics.
     * This is used by constructor paths that must observe accessors and propagate abrupt completions.
     */
    boolean transferPrototypeFromConstructor(JSObject receiver, JSObject constructor) {
        JSObject prototype = getPrototypeFromConstructor(constructor, JSObject.NAME);
        if (prototype == null) {
            return false;
        }
        receiver.setPrototype(prototype);
        return true;
    }
}
