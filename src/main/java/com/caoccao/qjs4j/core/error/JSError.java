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

package com.caoccao.qjs4j.core.error;

import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.core.*;

import java.util.function.BiFunction;

/**
 * Represents a JavaScript Error object. Base class for all JavaScript error types.
 * <p>
 * <strong>Identity, not value.</strong> This class deliberately does not override {@code equals} or {@code hashCode}.
 * It used to, over its own guest-visible {@code name} and {@code message} properties, which broke three things at once:
 * two distinct errors with the same message collapsed into one entry in any Java collection holding {@link JSObject}s,
 * an error's hash changed when a script assigned to {@code message}, and computing that hash invoked whatever accessor
 * the script had installed. ECMAScript objects compare by identity; every {@code JSObject} subclass must leave
 * {@code equals} and {@code hashCode} alone.
 */
public sealed class JSError extends JSObject permits JSAggregateError, JSRangeError, JSReferenceError, JSSyntaxError,
        JSTypeError, JSEvalError, JSURIError, JSSuppressedError {
    /** V8's default {@code Error.stackTraceLimit}. */
    public static final int DEFAULT_STACK_TRACE_LIMIT = 10;
    public static final String NAME = "Error";
    public static final PropertyKey STACK_TRACE_LIMIT = PropertyKey.fromString("stackTraceLimit");
    private final String errorName;
    private final SourceLocation sourceLocation;
    /**
     * The name of the source {@link #getSourceLocation()} indexes into, when it is not the source the embedder handed
     * to {@code eval}.
     * <p>
     * Set after construction rather than taken as a constructor parameter because every error class in this hierarchy
     * would otherwise need another constructor, and only module linking — which is the one place that reports a
     * position in a file the caller never passed in — has anything to put here.
     */
    private String sourceName;
    private JSStackTrace stackTrace;
    private String vmMessage;

    /**
     * Create an Error with a message.
     */
    public JSError(JSContext context, String message) {
        this(context, message, null);
    }

    /**
     * Create an Error with a message and source location.
     */
    public JSError(JSContext context, String message, SourceLocation sourceLocation) {
        this(context, NAME, message, sourceLocation);
    }

    JSError(JSContext context, String errorName, String message, SourceLocation sourceLocation) {
        super(context);
        this.errorName = errorName;
        this.sourceLocation = sourceLocation;
        if (message != null && !message.isEmpty()) {
            // A fresh error has no existing descriptor to validate or merge.
            definePropertyInternal(PropertyKey.MESSAGE, PropertyDescriptor.dataDescriptor(new JSString(message),
                    PropertyDescriptor.DataState.ConfigurableWritable));
        }
    }

    public String getErrorName() {
        return errorName;
    }

    /**
     * Get the error message.
     */
    public JSString getMessage() {
        JSValue msgValue = get(PropertyKey.MESSAGE);
        if (msgValue.isUndefined()) {
            return JSString.EMPTY;
        }
        return JSTypeConversions.toString(context, msgValue);
    }

    /**
     * Get the error name.
     */
    public JSString getName() {
        JSValue nameValue = get(PropertyKey.NAME);
        if (nameValue.isUndefined()) {
            return new JSString(getErrorName());
        }
        return JSTypeConversions.toString(context, nameValue);
    }

    /**
     * Get the source location associated with this error.
     *
     * @return the source location, or {@code null} when it is unavailable
     */

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    /**
     * The name of the source {@link #getSourceLocation()} indexes into.
     *
     * @return the source name, or null when the location belongs to the source the embedder supplied, or when there is
     *         no location
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * The stack trace captured when this error was created.
     *
     * @return the trace, or null when none was captured
     */
    public JSStackTrace getStackTrace() {
        return stackTrace;
    }

    public String getVmMessage() {
        return vmMessage;
    }

    /**
     * Record which source this error's location indexes into.
     *
     * @param sourceName
     *            the source name
     */
    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public void setStackTrace(JSStackTrace stackTrace) {
        this.stackTrace = stackTrace;
    }

    public void setVmMessage(String vmMessage) {
        this.vmMessage = vmMessage;
    }

    @Override
    public String toString() {
        String name = getName().value();
        String message = getMessage().value();
        if (message == null || message.isEmpty()) {
            return name;
        }
        return name + ": " + message;
    }

    /**
     * {@code Error.captureStackTrace(targetObject[, constructorOpt])}, the V8 extension Node and most error-wrapping
     * libraries rely on: gives {@code targetObject} a {@code stack} accessor holding the current stack, leaving out the
     * frames above and including the most recent call of {@code constructorOpt}.
     *
     * @param context
     *            the realm
     * @param thisArg
     *            unused
     * @param args
     *            the target object and the optional function to start below
     * @return {@code undefined}
     */
    public static JSValue captureStackTrace(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSObject targetObject)) {
            // V8's message, verbatim.
            return context.throwTypeError("invalid_argument");
        }
        JSObject skipFunction = args.length > 1 && args[1] instanceof JSFunction function ? function : null;
        if (!context.captureStackTrace(targetObject, skipFunction)) {
            return context.throwTypeError("Cannot define property stack, object is not extensible");
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * Leave the frames of a subclass's own constructors out of the stack of an error it constructs, as V8 does: when
     * {@code new MyError()} reaches an error constructor through {@code super()}, the frames above and including
     * {@code MyError}'s are not part of the trace.
     *
     * @param context
     *            the realm
     * @param error
     *            the error just constructed
     */
    static void captureStackTraceBelowNewTarget(JSContext context, JSObject error) {
        if (context.getNativeConstructorNewTarget() instanceof JSBytecodeFunction newTarget) {
            context.captureStackTrace(error, newTarget);
        }
    }

    public static JSValue create(JSContext context, JSValue... args) {
        return createNativeError(context, args, JSContext::createJSError);
    }

    static JSValue createNativeError(JSContext context, JSValue[] args,
            BiFunction<JSContext, String, JSError> factory) {
        return createNativeError(context, args, factory, null);
    }

    static JSValue createNativeError(JSContext context, JSValue[] args, BiFunction<JSContext, String, JSError> factory,
            String prototypeToRefresh) {
        String message = "";
        if (args.length > 0 && !args[0].isUndefined()) {
            message = JSTypeConversions.toJavaString(context, args[0]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        JSError jsError = factory.apply(context, message);
        if (args.length > 0 && !args[0].isUndefined()) {
            defineEmptyMessage(jsError, message);
        }
        if (prototypeToRefresh != null) {
            context.transferPrototype(jsError, prototypeToRefresh);
        }
        if (args.length > 1 && !installErrorCause(context, jsError, args[1])) {
            return JSUndefined.INSTANCE;
        }
        captureStackTraceBelowNewTarget(context, jsError);
        return jsError;
    }

    public static JSObject createNativeErrorPrototype(JSContext context, String name, int length,
            JSNativeCallback constructor) {
        JSObject errorPrototype = newErrorPrototype(context, name, NAME);
        JSNativeFunction errorConstructor = new JSNativeFunction(context, name, length, constructor, true);
        errorConstructor.defineProperty(PropertyKey.PROTOTYPE, errorPrototype, PropertyDescriptor.DataState.None);
        errorPrototype.defineProperty(PropertyKey.CONSTRUCTOR, errorConstructor,
                PropertyDescriptor.DataState.ConfigurableWritable);

        return errorConstructor;
    }

    public static JSObject createPrototype(JSContext context, JSValue... args) {
        JSObject errorPrototype = newErrorPrototype(context, NAME, JSObject.NAME);
        errorPrototype.defineProperty(PropertyKey.TO_STRING,
                new JSNativeFunction(context, "toString", 0, JSError::errorToString),
                PropertyDescriptor.DataState.ConfigurableWritable);

        // Standard Error(message, options) — length = 1
        JSNativeFunction errorConstructor = new JSNativeFunction(context, NAME, 1,
                (childContext, thisObj, childArgs) -> create(childContext, childArgs), true);
        errorConstructor.defineProperty(PropertyKey.PROTOTYPE, errorPrototype, PropertyDescriptor.DataState.None);

        // Error.isError static method (ES2024)
        errorConstructor.defineProperty(PropertyKey.fromString("isError"),
                new JSNativeFunction(context, "isError", 1, JSError::isError),
                PropertyDescriptor.DataState.ConfigurableWritable);

        // V8 extensions: Error.captureStackTrace and Error.stackTraceLimit
        errorConstructor.defineProperty(PropertyKey.fromString("captureStackTrace"),
                new JSNativeFunction(context, "captureStackTrace", 2, JSError::captureStackTrace),
                PropertyDescriptor.DataState.ConfigurableWritable);
        errorConstructor.createDataProperty(STACK_TRACE_LIMIT, JSNumber.of(DEFAULT_STACK_TRACE_LIMIT));

        // Constructor property on prototype (writable, non-enumerable, configurable)
        errorPrototype.defineProperty(PropertyKey.CONSTRUCTOR, errorConstructor,
                PropertyDescriptor.DataState.ConfigurableWritable);

        return errorConstructor;
    }

    /**
     * Give an error constructed with an empty message its own {@code message} property, as the constructors do for any
     * message argument that is not {@code undefined}. The constructor defines the property only for a non-empty text,
     * so {@code new Error("")} used to inherit {@code message} from the prototype.
     *
     * @param error
     *            the error just constructed
     * @param message
     *            the message text
     */
    static void defineEmptyMessage(JSError error, String message) {
        if (message.isEmpty()) {
            error.defineProperty(PropertyKey.MESSAGE, PropertyDescriptor.dataDescriptor(new JSString(message),
                    PropertyDescriptor.DataState.ConfigurableWritable));
        }
    }

    /**
     * Error.prototype.toString() — ES2024 20.5.3.4 Following QuickJS js_error_toString.
     */
    public static JSValue errorToString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject error)) {
            return context.throwTypeError("Error.prototype.toString requires that 'this' be an Object");
        }

        JSValue nameValue = error.get(PropertyKey.NAME);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String name;
        if (nameValue instanceof JSUndefined) {
            name = NAME;
        } else {
            name = JSTypeConversions.toJavaString(context, nameValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue messageValue = error.get(PropertyKey.MESSAGE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String message;
        if (messageValue instanceof JSUndefined) {
            message = "";
        } else {
            message = JSTypeConversions.toJavaString(context, messageValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (name.isEmpty()) {
            return new JSString(message);
        }
        if (message.isEmpty()) {
            return new JSString(name);
        }
        return new JSString(name + ": " + message);
    }

    /**
     * InstallErrorCause ( O, options ) ES2022: If options is an object with a "cause" property, install it as a
     * non-enumerable, writable, configurable own property on the error object. Returns true on normal completion, false
     * on abrupt completion (exception pending in context).
     */
    public static boolean installErrorCause(JSContext context, JSObject obj, JSValue options) {
        if (options instanceof JSObject optionsObj) {
            if (optionsObj.has("cause")) {
                JSValue cause = optionsObj.get(PropertyKey.CAUSE);
                if (context.hasPendingException()) {
                    return false;
                }
                obj.defineProperty(PropertyKey.CAUSE,
                        PropertyDescriptor.dataDescriptor(cause, PropertyDescriptor.DataState.ConfigurableWritable));
            }
        }
        return true;
    }

    /**
     * Error.isError(arg) — ES2024. Returns true if arg is an error object (has [[ErrorData]] internal slot). Following
     * QuickJS js_error_isError which checks class_id == JS_CLASS_ERROR.
     */
    public static JSValue isError(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue arg = JSValue.argumentOrUndefined(args, 0);
        return JSBoolean.valueOf(arg instanceof JSError);
    }

    private static JSObject newErrorPrototype(JSContext context, String name, String parent) {
        JSObject errorPrototype = new JSObject(context);
        context.transferPrototype(errorPrototype, parent);

        errorPrototype.defineProperty(PropertyKey.NAME, new JSString(name),
                PropertyDescriptor.DataState.ConfigurableWritable);
        errorPrototype.defineProperty(PropertyKey.MESSAGE, JSString.EMPTY,
                PropertyDescriptor.DataState.ConfigurableWritable);
        return errorPrototype;
    }
}
