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

/**
 * Builds the realm's error values and the stack traces attached to them — QuickJS's
 * {@code JS_ThrowError2} family, as a class.
 * <p>
 * Every {@code throwXxx} constructs the error, gives it the realm's prototype for its type, attaches
 * a stack trace, and records it as the context's pending exception. Setting that exception stays a
 * {@link JSContext} method this class calls: the pending-exception state belongs to the realm, not
 * to the reporter.
 * <p>
 * {@link JSContext} keeps every {@code throwXxx} as a public delegation, so the roughly two thousand
 * {@code context.throwTypeError(...)} call sites in builtins and the VM are untouched.
 */
final class JSErrorReporter {
    private final JSContext context;

    // Stack trace capture
    private final List<StackTraceElement> errorStackTrace = new ArrayList<>();

    JSErrorReporter(JSContext context) {
        this.context = context;
    }

    /**
     * Capture stack trace when exception is thrown.
     */
    void captureErrorStackTrace() {
        clearErrorStackTrace();
        for (JSStackFrame frame : context.callStackFrames()) {
            errorStackTrace.add(new StackTraceElement(
                    "JavaScript",
                    frame.functionName(),
                    frame.filename(),
                    frame.lineNumber()
            ));
        }
    }

    /**
     * Capture stack trace and attach to error object.
     */
    void captureStackTrace(JSObject error) {
        StringBuilder stackTrace = new StringBuilder();

        VirtualMachine virtualMachine = context.getVirtualMachine();
        StackFrame vmStackFrame = virtualMachine != null ? virtualMachine.getCurrentFrame() : null;
        while (vmStackFrame != null) {
            JSFunction frameFunction = vmStackFrame.getFunction();
            String functionName = "<anonymous>";
            if (frameFunction != null) {
                String candidateFunctionName = frameFunction.getName();
                if (candidateFunctionName != null && !candidateFunctionName.isEmpty()) {
                    functionName = candidateFunctionName;
                }
            }
            String filename = frameFunction != null ? frameFunction.getImportMetaFilename() : null;
            if (filename == null || filename.isEmpty()) {
                filename = "<eval>";
            }
            stackTrace.append("    at ")
                    .append(functionName)
                    .append(" (")
                    .append(filename)
                    .append(":")
                    .append(1)
                    .append(")\n");
            vmStackFrame = vmStackFrame.getCaller();
        }

        for (JSStackFrame frame : context.callStackFrames()) {
            stackTrace.append("    at ")
                    .append(frame.functionName())
                    .append(" (")
                    .append(frame.filename())
                    .append(":")
                    .append(frame.lineNumber())
                    .append(")\n");
        }

        error.defineProperty(
                PropertyKey.STACK,
                PropertyDescriptor.dataDescriptor(
                        new JSString(stackTrace.toString()),
                        PropertyDescriptor.DataState.ConfigurableWritable));
    }

    void clearErrorStackTrace() {
        errorStackTrace.clear();
    }

    /**
     * Get the error stack trace.
     */
    List<StackTraceElement> getErrorStackTrace() {
        return new ArrayList<>(errorStackTrace);
    }

    /**
     * Throw a AggregateError.
     *
     * @param message Error message
     * @return The error value
     */
    JSError throwAggregateError(String message) {
        return throwError(JSAggregateError.NAME, message);
    }

    /**
     * Throw a JavaScript error.
     * Creates an Error object and sets it as the pending exception.
     *
     * @param message Error message
     * @return The error value (for convenience in return statements)
     */
    JSError throwError(String message) {
        return throwError(JSError.NAME, message);
    }

    /**
     * Throw a JavaScript error of a specific type.
     *
     * @param errorType Error constructor name (Error, TypeError, RangeError, etc.)
     * @param message   Error message
     * @return The error value
     */
    JSError throwError(String errorType, String message) {
        return throwError(errorType, message, null);
    }

    /**
     * Throw a JavaScript error of a specific type at a source location.
     *
     * @param errorType      Error constructor name (Error, TypeError, RangeError, etc.)
     * @param message        Error message
     * @param sourceLocation Source location, or {@code null} when unavailable
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
        return throwError(
                jsErrorException.getErrorType().name(),
                jsErrorException.getMessage(),
                jsErrorException.getSourceLocation());
    }

    /**
     * Throw a EvalError.
     *
     * @param message Error message
     * @return The error value
     */
    JSError throwEvalError(String message) {
        return throwError(JSEvalError.NAME, message);
    }

    /**
     * Throw a RangeError.
     *
     * @param message Error message
     * @return The error value
     */
    JSError throwRangeError(String message) {
        return throwError(JSRangeError.NAME, message);
    }

    /**
     * Throw a ReferenceError.
     *
     * @param message Error message
     * @return The error value
     */
    JSError throwReferenceError(String message) {
        return throwError(JSReferenceError.NAME, message);
    }

    /**
     * Throw a SyntaxError.
     *
     * @param message Error message
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
     * @param message Error message
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
     * @param message Error message
     * @return The error value
     */
    JSError throwURIError(String message) {
        return throwError(JSURIError.NAME, message);
    }
}
