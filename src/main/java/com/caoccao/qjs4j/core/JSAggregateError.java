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

/**
 * Represents a JavaScript AggregateError object.
 * AggregateError represents an error when multiple errors need to be wrapped in a single error.
 */
public final class JSAggregateError extends JSError {

    public static final String NAME = "AggregateError";

    /**
     * Create an AggregateError with an empty errors array.
     */
    public JSAggregateError(JSContext context) {
        this(context, context.createJSArray(), "");
    }

    /**
     * Create an AggregateError with a message.
     */
    public JSAggregateError(JSContext context, String message) {
        this(context, context.createJSArray(), message);
    }

    /**
     * Create an AggregateError with errors and a message.
     */
    public JSAggregateError(JSContext context, JSArray errors, String message) {
        super(context, NAME, message);
        set("errors", errors);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        // Step 2: OrdinaryCreateFromConstructor(newTarget, "%AggregateError.prototype%")
        JSObject obj = new JSObject();
        context.transferPrototype(obj, NAME);
        obj.set(PropertyKey.NAME, new JSString(NAME));

        // Step 3: If message is not undefined, CreateMethodProperty(O, "message", ToString(message))
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            String message = JSTypeConversions.toString(context, args[1]).value();
            obj.defineProperty(PropertyKey.MESSAGE,
                    PropertyDescriptor.dataDescriptor(new JSString(message), true, false, true));
        }

        // Step 4: InstallErrorCause(O, options)
        if (args.length > 2) {
            JSError.installErrorCause(obj, args[2]);
        }

        // Step 5: Let errorsList be ? IterableToList(errors).
        JSValue errorsArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSArray errorsList = JSIteratorHelper.iterableToList(context, errorsArg);
        if (context.hasPendingException()) {
            return (JSObject) context.getPendingException();
        }
        obj.set(PropertyKey.ERRORS, errorsList);

        return obj;
    }

    public static JSObject createPrototype(JSContext context, JSValue... args) {
        // Create AggregateError.prototype (not an error instance per spec)
        JSObject errorPrototype = new JSObject();
        context.transferPrototype(errorPrototype, JSError.NAME);
        errorPrototype.defineProperty(PropertyKey.fromString("name"),
                PropertyDescriptor.dataDescriptor(new JSString(NAME), true, false, true));
        errorPrototype.defineProperty(PropertyKey.MESSAGE,
                PropertyDescriptor.dataDescriptor(new JSString(""), true, false, true));
        errorPrototype.defineProperty(PropertyKey.fromString("toString"),
                PropertyDescriptor.dataDescriptor(new JSNativeFunction("toString", 0, JSError::errorToString), true, false, true));

        // AggregateError(errors, message)
        int length = 2;

        // Create Error constructor as a function (following QuickJS pattern)
        // QuickJS uses JS_NewCConstructor for error constructors
        JSNativeFunction errorConstructor = new JSNativeFunction(
                NAME,
                length,
                (childContext, thisObj, childArgs) -> {
                    // When called with 'new' or Reflect.construct, thisObj is a JSObject
                    // with the correct prototype already set by constructFunction.
                    // When called without 'new', thisObj is undefined — create a new object.
                    JSObject obj;
                    if (thisObj instanceof JSObject o) {
                        obj = o;
                    } else {
                        obj = new JSObject();
                        childContext.transferPrototype(obj, NAME);
                    }

                    obj.set(PropertyKey.NAME, new JSString(NAME));

                    // Step 3: If message is not undefined, CreateMethodProperty(O, "message", ToString(message))
                    if (childArgs.length > 1 && !(childArgs[1] instanceof JSUndefined)) {
                        String message = JSTypeConversions.toString(childContext, childArgs[1]).value();
                        obj.defineProperty(PropertyKey.MESSAGE,
                                PropertyDescriptor.dataDescriptor(new JSString(message), true, false, true));
                    }

                    // Step 4: InstallErrorCause(O, options)
                    if (childArgs.length > 2) {
                        JSError.installErrorCause(obj, childArgs[2]);
                    }

                    // Step 5: Let errorsList be ? IterableToList(errors).
                    JSValue errorsArg = childArgs.length > 0 ? childArgs[0] : JSUndefined.INSTANCE;
                    JSArray errorsList = JSIteratorHelper.iterableToList(childContext, errorsArg);
                    if (childContext.hasPendingException()) {
                        return childContext.getPendingException();
                    }
                    obj.set(PropertyKey.ERRORS, errorsList);

                    return obj;
                },
                true);
        errorConstructor.definePropertyReadonlyNonConfigurable("prototype", errorPrototype);

        // AggregateError.[[Prototype]] = Error (the constructor inherits from Error)
        JSValue errorCtor = context.getGlobalObject().get(JSError.NAME);
        if (errorCtor instanceof JSObject errorCtorObj) {
            errorConstructor.setPrototype(errorCtorObj);
        }

        // Set constructor property on prototype (non-enumerable per spec)
        errorPrototype.defineProperty(PropertyKey.CONSTRUCTOR,
                PropertyDescriptor.dataDescriptor(errorConstructor, true, false, true));

        return errorConstructor;
    }

    /**
     * Get the errors array.
     */
    public JSArray getErrors() {
        return get("errors").asArray().orElseGet(JSArray::new);
    }
}
