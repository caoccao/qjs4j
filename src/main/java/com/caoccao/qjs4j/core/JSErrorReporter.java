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

import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.vm.StackFrame;
import com.caoccao.qjs4j.vm.VirtualMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds the realm's error values and the stack traces attached to them — QuickJS's {@code JS_ThrowError2} family, as a
 * class.
 * <p>
 * Every {@code throwXxx} constructs the error, gives it the realm's prototype for its type, attaches a stack trace, and
 * records it as the context's pending exception. Setting that exception stays a {@link JSContext} method this class
 * calls: the pending-exception state belongs to the realm, not to the reporter.
 * <p>
 * {@link JSContext} keeps every {@code throwXxx} as a public delegation, so the roughly two thousand
 * {@code context.throwTypeError(...)} call sites in builtins and the VM are untouched.
 */
final class JSErrorReporter {
    /**
     * V8's default {@code Error.stackTraceLimit}.
     */
    static final int DEFAULT_STACK_TRACE_LIMIT = 10;
    static final PropertyKey STACK_TRACE_LIMIT = PropertyKey.fromString("stackTraceLimit");
    private static final Set<String> STATIC_INITIALIZER_NAMES = Set.of("<static initializer>",
            "<static field initializer>");
    private final JSContext context;
    // Stack trace capture
    private final List<StackTraceElement> errorStackTrace = new ArrayList<>();
    private JSNativeFunction stackGetter;
    private JSNativeFunction stackSetter;

    JSErrorReporter(JSContext context) {
        this.context = context;
    }

    /**
     * Capture stack trace when exception is thrown.
     */
    void captureErrorStackTrace() {
        clearErrorStackTrace();
        for (JSStackFrame frame : context.callStackFrames()) {
            errorStackTrace.add(
                    new StackTraceElement("JavaScript", frame.functionName(), frame.filename(), frame.lineNumber()));
        }
    }

    /**
     * Capture the stack and attach it to an error as V8 does: an own, non-enumerable {@code stack} accessor whose value
     * is assembled on first read.
     * <p>
     * Every frame used to report line 1, the string had no {@code Name: message} first line, and the eval pipeline's
     * bookkeeping frames were appended to it as {@code at <eval>}.
     *
     * @param error
     *            the error, or any object {@code Error.captureStackTrace} is applied to
     */
    void captureStackTrace(JSObject error) {
        captureStackTrace(error, null);
    }

    /**
     * Capture the stack for {@code Error.captureStackTrace(target, constructorOpt)}.
     *
     * @param target
     *            the object to give a {@code stack}
     * @param skipFunction
     *            the frames above and including the most recent call of this function are left out, or null
     * @return whether {@code stack} could be defined; it cannot on a non-extensible object
     */
    boolean captureStackTrace(JSObject target, JSObject skipFunction) {
        JSStackTrace stackTrace = new JSStackTrace(formatStackFrames(skipFunction), getMessageForStackTrace(target));
        if (!target.defineProperty(PropertyKey.STACK, PropertyDescriptor.accessorDescriptor(getStackGetter(),
                getStackSetter(), PropertyDescriptor.AccessorState.Configurable))) {
            return false;
        }
        if (target instanceof JSError jsError) {
            jsError.setStackTrace(stackTrace);
        } else {
            target.setCapturedStackTrace(stackTrace);
        }
        return true;
    }

    void clearErrorStackTrace() {
        errorStackTrace.clear();
    }

    /**
     * Where the code about to be evaluated by {@code eval} is evaluated, as V8 writes it: {@code eval at caller
     * (location)}, the caller being the function calling {@code eval}, or {@code <anonymous>} for top-level code.
     *
     * @return the origin, or null when no script frame is calling
     */
    String describeEvalOrigin() {
        StackFrame caller = context.getVirtualMachine().getCurrentFrame();
        if (caller == null) {
            return null;
        }
        JSBytecodeFunction function = (JSBytecodeFunction) caller.getFunction();
        // V8 names the caller by the name it was compiled with, which survives `delete f.name`; a method
        // compiled without one is named by its property.
        String callerName = function.isProgram() || function.getName().isEmpty()
                ? getFunctionDisplayName(function)
                : function.getName();
        // An eval called from eval code is located by that code's own origin, without a position.
        String callerLocation = function.getEvalOrigin() != null
                ? function.getEvalOrigin()
                : formatFrameLocation(caller, function);
        return "eval at " + (callerName.isEmpty() ? "<anonymous>" : callerName) + " (" + callerLocation + ")";
    }

    /**
     * Format the frames of the current stack, most recent first, at most {@code Error.stackTraceLimit} of them.
     *
     * @param skipFunction
     *            the frames above and including the most recent call of this function are left out, or null; when it is
     *            not on the stack, no frame is
     * @return the frames, or null when {@code Error.stackTraceLimit} is not a number
     */
    private String formatStackFrames(JSObject skipFunction) {
        long limit = getStackTraceLimit();
        if (limit < 0) {
            return null;
        }
        StringBuilder stackTrace = new StringBuilder();
        VirtualMachine virtualMachine = context.getVirtualMachine();
        boolean skipping = skipFunction != null;
        long count = 0;
        for (StackFrame frame = virtualMachine.getCurrentFrame(); frame != null
                && count < limit; frame = frame.getCaller()) {
            if (skipping) {
                skipping = frame.getFunction() != skipFunction;
                continue;
            }
            if (count > 0) {
                stackTrace.append('\n');
            }
            appendStackFrame(stackTrace, frame, context.getGlobalObject());
            count++;
        }
        return stackTrace.toString();
    }

    /**
     * Get the error stack trace.
     */
    List<StackTraceElement> getErrorStackTrace() {
        return new ArrayList<>(errorStackTrace);
    }

    /**
     * The shared getter of every error's own {@code stack} accessor, like V8's: nameless, length 0, and
     * {@code undefined} for anything without a captured trace.
     */
    JSNativeFunction getStackGetter() {
        if (stackGetter == null) {
            stackGetter = new JSNativeFunction(context, "", 0, (childContext, thisArg, args) -> {
                if (thisArg instanceof JSObject owner) {
                    JSStackTrace stackTrace = getStackTrace(owner);
                    if (stackTrace != null) {
                        return stackTrace.getValue(childContext, owner);
                    }
                }
                return JSUndefined.INSTANCE;
            }, false);
            context.transferPrototype(stackGetter, JSFunction.NAME);
        }
        return stackGetter;
    }

    /**
     * The shared setter of every error's own {@code stack} accessor: an assignment replaces the value for good.
     */
    JSNativeFunction getStackSetter() {
        if (stackSetter == null) {
            stackSetter = new JSNativeFunction(context, "", 1, (childContext, thisArg, args) -> {
                if (thisArg instanceof JSObject owner) {
                    JSStackTrace stackTrace = getStackTrace(owner);
                    if (stackTrace != null) {
                        stackTrace.setValue(JSValue.argumentOrUndefined(args, 0));
                    }
                }
                return JSUndefined.INSTANCE;
            }, false);
            context.transferPrototype(stackSetter, JSFunction.NAME);
        }
        return stackSetter;
    }

    /**
     * The trace captured for an object, if any.
     *
     * @param owner
     *            the object
     * @return the trace, or null
     */
    JSStackTrace getStackTrace(JSObject owner) {
        if (owner instanceof JSError jsError) {
            return jsError.getStackTrace();
        }
        return owner.getCapturedStackTrace();
    }

    /**
     * {@code Error.stackTraceLimit}, read from the realm's {@code %Error%} without running script, as V8 does.
     *
     * @return the number of frames to capture, or -1 when the limit is not a number
     */
    private long getStackTraceLimit() {
        JSObject errorConstructor = context.getCachedErrorConstructor();
        if (errorConstructor == null) {
            return DEFAULT_STACK_TRACE_LIMIT;
        }
        if (!(errorConstructor.getOwnDataPropertyForDiagnostics(STACK_TRACE_LIMIT) instanceof JSNumber limit)) {
            return -1;
        }
        double value = limit.value();
        if (Double.isNaN(value) || value <= 0) {
            return 0;
        }
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) value;
    }

    /**
     * Throw a AggregateError.
     *
     * @param message
     *            Error message
     * @return The error value
     */
    JSError throwAggregateError(String message) {
        return throwError(JSAggregateError.NAME, message);
    }

    JSError throwError(JSError jsError) {
        context.transferPrototype(jsError, jsError.getErrorName());
        // Capture stack trace
        captureStackTrace(jsError);
        // Set as pending exception
        context.setPendingException(jsError);
        return jsError;
    }

    JSError throwError(JSErrorException jsErrorException) {
        if (jsErrorException == null) {
            return throwError("Unknown error");
        }
        return throwError(jsErrorException.getErrorType().name(), jsErrorException.getMessage(),
                jsErrorException.getSourceLocation());
    }

    /**
     * Throw a JavaScript error. Creates an Error object and sets it as the pending exception.
     *
     * @param message
     *            Error message
     * @return The error value (for convenience in return statements)
     */
    JSError throwError(String message) {
        return throwError(JSError.NAME, message);
    }

    /**
     * Throw a JavaScript error of a specific type.
     *
     * @param errorType
     *            Error constructor name (Error, TypeError, RangeError, etc.)
     * @param message
     *            Error message
     * @return The error value
     */
    JSError throwError(String errorType, String message) {
        return throwError(errorType, message, null);
    }

    /**
     * Throw a JavaScript error of a specific type at a source location.
     *
     * @param errorType
     *            Error constructor name (Error, TypeError, RangeError, etc.)
     * @param message
     *            Error message
     * @param sourceLocation
     *            Source location, or {@code null} when unavailable
     * @return The error value
     */
    JSError throwError(String errorType, String message, SourceLocation sourceLocation) {
        // Create error object using the proper error class
        JSError jsError = switch (errorType) {
            case JSAggregateError.NAME -> new JSAggregateError(context, message, sourceLocation);
            case JSEvalError.NAME -> new JSEvalError(context, message, sourceLocation);
            case JSRangeError.NAME -> new JSRangeError(context, message, sourceLocation);
            case JSReferenceError.NAME -> new JSReferenceError(context, message, sourceLocation);
            case JSSyntaxError.NAME -> new JSSyntaxError(context, message, sourceLocation);
            case JSTypeError.NAME -> new JSTypeError(context, message, sourceLocation);
            case JSURIError.NAME -> new JSURIError(context, message, sourceLocation);
            default -> new JSError(context, message, sourceLocation);
        };
        return throwError(jsError);
    }

    /**
     * Throw a EvalError.
     *
     * @param message
     *            Error message
     * @return The error value
     */
    JSError throwEvalError(String message) {
        return throwError(JSEvalError.NAME, message);
    }

    /**
     * Throw a RangeError.
     *
     * @param message
     *            Error message
     * @return The error value
     */
    JSError throwRangeError(String message) {
        return throwError(JSRangeError.NAME, message);
    }

    /**
     * Throw a ReferenceError.
     *
     * @param message
     *            Error message
     * @return The error value
     */
    JSError throwReferenceError(String message) {
        return throwError(JSReferenceError.NAME, message);
    }

    /**
     * Throw a SyntaxError.
     *
     * @param message
     *            Error message
     * @return The error value
     */
    JSError throwSyntaxError(String message) {
        return throwError(JSSyntaxError.NAME, message);
    }

    JSError throwSyntaxError(String message, SourceLocation sourceLocation) {
        return throwError(JSSyntaxError.NAME, message, sourceLocation);
    }

    /**
     * Throw a TypeError.
     *
     * @param message
     *            Error message
     * @return The error value
     */
    JSError throwTypeError(String message) {
        return throwError(JSTypeError.NAME, message);
    }

    JSError throwTypeError(String message, SourceLocation sourceLocation) {
        return throwError(JSTypeError.NAME, message, sourceLocation);
    }

    /**
     * Throw a URIError.
     *
     * @param message
     *            Error message
     * @return The error value
     */
    JSError throwURIError(String message) {
        return throwError(JSURIError.NAME, message);
    }

    private static void appendStackFrame(StringBuilder stackTrace, StackFrame frame, JSObject globalObject) {
        // Only bytecode functions get a frame of their own.
        JSBytecodeFunction function = (JSBytecodeFunction) frame.getFunction();
        String location = formatFrameLocation(frame, function);
        String frameName = getStackFrameName(frame, function, globalObject);
        stackTrace.append("    at ");
        if (frameName == null) {
            stackTrace.append(location);
        } else {
            stackTrace.append(frameName).append(" (").append(location).append(')');
        }
    }

    /**
     * Where a frame is, in V8's form: {@code file:line:column}, with the file of code that came from {@code eval} given
     * as {@code eval at caller (location), <anonymous>}.
     */
    private static String formatFrameLocation(StackFrame frame, JSBytecodeFunction function) {
        StringBuilder location = new StringBuilder();
        if (function.getEvalOrigin() != null) {
            location.append(function.getEvalOrigin()).append(", <anonymous>");
        } else {
            String filename = function.getImportMetaFilename();
            location.append(filename == null || filename.isEmpty() ? "<anonymous>" : filename);
        }
        int[] sourcePosition = function.getBytecode().getSourcePosition(frame.getProgramCounter());
        if (sourcePosition != null) {
            location.append(':').append(sourcePosition[0]).append(':').append(sourcePosition[1]);
        }
        return location.toString();
    }

    /**
     * The name a function is known by in a stack trace, as V8 reads it: its own {@code name} data property, which holds
     * what SetFunctionName gave a method, accessor or anonymous function expression, and nothing once that property is
     * gone. Script, module and eval bodies have none.
     */
    private static String getFunctionDisplayName(JSBytecodeFunction function) {
        return function.isProgram() ? "" : getNameProperty(function);
    }

    /**
     * The {@code message} a captured trace's first line uses, read from storage along the prototype chain so capturing
     * runs no script.
     */
    private static JSValue getMessageForStackTrace(JSObject target) {
        for (JSObject object = target; object != null && !object.isProxy(); object = object.getPrototype()) {
            JSValue message = object.getOwnDataPropertyForDiagnostics(PropertyKey.MESSAGE);
            if (message != null) {
                return message;
            }
        }
        return JSUndefined.INSTANCE;
    }

    private static String getNameProperty(JSFunction function) {
        return function.getOwnDataPropertyForDiagnostics(PropertyKey.NAME) instanceof JSString name ? name.value() : "";
    }

    /**
     * V8's {@code GetConstructorName}, read from storage so formatting a frame runs no script: a function receiver is
     * named by its own name, an object by the nearest {@code constructor} on its prototype chain.
     */
    private static String getReceiverTypeName(JSObject receiver) {
        if (receiver instanceof JSFunction function && !getNameProperty(function).isEmpty()) {
            return getNameProperty(function);
        }
        for (JSObject object = receiver.getPrototype(); object != null; object = object.getPrototype()) {
            if (object.getOwnDataPropertyForDiagnostics(PropertyKey.CONSTRUCTOR) instanceof JSFunction constructor) {
                // The name the constructor was compiled with survives `delete C.name`, as V8's does.
                String constructorName = getNameProperty(constructor).isEmpty()
                        ? constructor.getName()
                        : getNameProperty(constructor);
                if (!constructorName.isEmpty()) {
                    return constructorName;
                }
            }
        }
        return "Object";
    }

    /**
     * The name a frame is printed under, following V8: {@code new F} for a construct call, {@code Type.method} for a
     * call on an object, the function's name otherwise, {@code eval} for an eval body, and none for top-level code or
     * an anonymous function.
     */
    private static String getStackFrameName(StackFrame frame, JSBytecodeFunction function, JSObject globalObject) {
        if (function.isProgram()) {
            return function.getEvalOrigin() != null ? "eval" : null;
        }
        String functionName = getFunctionDisplayName(function);
        if (!(frame.getNewTarget() instanceof JSUndefined)) {
            return "new " + (functionName.isEmpty() ? "<anonymous>" : functionName);
        }
        if (STATIC_INITIALIZER_NAMES.contains(functionName)) {
            // V8 runs static blocks and static field initializers as one `<static_initializer>`.
            return "<static_initializer>";
        }
        if (!function.isArrow() && frame.getThisArg() instanceof JSObject receiver && receiver != globalObject
                && !functionName.startsWith("get ") && !functionName.startsWith("set ")) {
            return getReceiverTypeName(receiver) + "." + (functionName.isEmpty() ? "<anonymous>" : functionName);
        }
        return functionName.isEmpty() ? null : functionName;
    }
}
