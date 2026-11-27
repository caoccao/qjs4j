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

package com.caoccao.qjs4j.builtins.object;

import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.error.JSStackFrame;
import com.caoccao.qjs4j.core.module.IJSModuleLoader;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ShadowRealm.prototype methods. Implements spec behavior needed by test262 for evaluate() and importValue().
 */
public final class ShadowRealmPrototype {
    private static final Pattern EXPORT_VAR_PATTERN = Pattern
            .compile("(?m)^\\s*export\\s+var\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(.+?);\\s*$");

    private ShadowRealmPrototype() {
    }

    private static void clearPendingExceptions(JSContext callerContext, JSContext targetContext) {
        if (callerContext.hasPendingException()) {
            callerContext.clearPendingException();
        }
        if (targetContext.hasPendingException()) {
            targetContext.clearPendingException();
        }
    }

    private static void copyNameAndLength(JSContext callerContext, JSContext targetContext, JSValue targetCallable,
            JSNativeFunction wrappedFunction) {
        if (!(targetCallable instanceof JSObject targetObject)) {
            throw new JSException(callerContext.throwTypeError("Wrapped target is not an object"));
        }

        double wrappedLength = 0;
        try {
            if (targetObject.hasOwnProperty(PropertyKey.LENGTH)) {
                if (callerContext.hasPendingException() || targetContext.hasPendingException()) {
                    clearPendingExceptions(callerContext, targetContext);
                    throw new JSException(callerContext.throwTypeError("Cannot wrap callable"));
                }
                JSValue targetLength = targetObject.get(PropertyKey.LENGTH);
                if (callerContext.hasPendingException() || targetContext.hasPendingException()) {
                    clearPendingExceptions(callerContext, targetContext);
                    throw new JSException(callerContext.throwTypeError("Cannot wrap callable"));
                }
                if (targetLength instanceof JSNumber targetLengthNumber) {
                    double numericLength = targetLengthNumber.value();
                    if (Double.isInfinite(numericLength)) {
                        if (numericLength > 0) {
                            wrappedLength = Double.POSITIVE_INFINITY;
                        } else {
                            wrappedLength = 0;
                        }
                    } else if (Double.isNaN(numericLength)) {
                        wrappedLength = 0;
                    } else {
                        double truncatedLength = numericLength < 0
                                ? Math.ceil(numericLength)
                                : Math.floor(numericLength);
                        wrappedLength = Math.max(truncatedLength, 0);
                    }
                }
            }

            JSValue targetName = targetObject.get(PropertyKey.NAME);
            if (callerContext.hasPendingException() || targetContext.hasPendingException()) {
                clearPendingExceptions(callerContext, targetContext);
                throw new JSException(callerContext.throwTypeError("Cannot wrap callable"));
            }
            String wrappedName = targetName instanceof JSString targetNameString ? targetNameString.value() : "";

            wrappedFunction.defineProperty(PropertyKey.LENGTH, PropertyDescriptor
                    .dataDescriptor(JSNumber.of(wrappedLength), PropertyDescriptor.DataState.Configurable));
            wrappedFunction.defineProperty(PropertyKey.NAME, PropertyDescriptor
                    .dataDescriptor(new JSString(wrappedName), PropertyDescriptor.DataState.Configurable));
        } catch (JSException e) {
            if (callerContext.hasPendingException()) {
                throw e;
            }
            throw new JSException(callerContext.throwTypeError("Cannot wrap callable"));
        } catch (RuntimeException e) {
            throw new JSException(callerContext.throwTypeError("Cannot wrap callable"));
        }
    }

    private static JSNativeFunction createWrappedFunction(JSContext callerContext, JSContext targetContext,
            JSValue targetCallable) {
        JSNativeFunction wrappedFunction = new JSNativeFunction(callerContext, "", 0,
                (callbackContext, thisArg, args) -> {
                    JSValue[] wrappedArguments = new JSValue[args.length];
                    for (int i = 0; i < args.length; i++) {
                        JSValue wrappedArgument = getWrappedValue(targetContext, callerContext, args[i]);
                        if (targetContext.hasPendingException()) {
                            JSValue ignored = targetContext.takePendingException();
                            return callerContext.throwTypeError("Cross-realm argument is not wrappable");
                        }
                        wrappedArguments[i] = wrappedArgument;
                    }

                    try {
                        JSValue callResult;
                        if (targetCallable instanceof JSProxy targetProxy) {
                            callResult = targetProxy.apply(targetContext, JSUndefined.INSTANCE, wrappedArguments);
                        } else if (targetCallable instanceof JSFunction targetFunction) {
                            callResult = targetFunction.call(targetContext, JSUndefined.INSTANCE, wrappedArguments);
                        } else {
                            return callerContext.throwTypeError("Wrapped target is not callable");
                        }

                        if (targetContext.hasPendingException()) {
                            targetContext.clearPendingException();
                            return callerContext.throwTypeError("Wrapped function threw");
                        }
                        return getWrappedValue(callerContext, targetContext, callResult);
                    } catch (JSException e) {
                        if (targetContext.hasPendingException()) {
                            targetContext.clearPendingException();
                        }
                        return callerContext.throwTypeError("Wrapped function threw");
                    } catch (JSVirtualMachineException e) {
                        if (targetContext.hasPendingException()) {
                            targetContext.clearPendingException();
                        }
                        return callerContext.throwTypeError("Wrapped function threw");
                    } catch (RuntimeException e) {
                        return callerContext.throwTypeError("Wrapped function threw");
                    }
                });

        copyNameAndLength(callerContext, targetContext, targetCallable, wrappedFunction);
        wrappedFunction.initializePrototypeChain(callerContext);
        return wrappedFunction;
    }

    public static JSValue evaluate(JSContext callerContext, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSShadowRealm shadowRealm)) {
            return callerContext.throwTypeError("ShadowRealm.prototype.evaluate requires a ShadowRealm receiver");
        }
        if (args.length == 0 || !(args[0] instanceof JSString sourceTextValue)) {
            return callerContext.throwTypeError("ShadowRealm.prototype.evaluate requires a string");
        }

        String sourceText = sourceTextValue.value();
        try {
            Compiler compilerCheck = new Compiler(sourceText, "<shadowrealm>").setContext(callerContext);
            compilerCheck.setPredeclareProgramLexicalsAsLocals(true);
            compilerCheck.compile(false);
        } catch (JSCompilerException e) {
            return callerContext.throwSyntaxError(e.getMessage(), e.getSourceLocation());
        } catch (JSErrorException e) {
            return callerContext.throwSyntaxError(e.getMessage(), e.getSourceLocation());
        }

        JSContext shadowContext = shadowRealm.getShadowContext();
        try {
            JSValue result = shadowContext.evalWithProgramLexicalsAsLocals(sourceText, "<shadowrealm>", false);
            return getWrappedValue(callerContext, shadowContext, result);
        } catch (JSException e) {
            if (shadowContext.hasPendingException()) {
                shadowContext.clearPendingException();
            }
            return callerContext.throwTypeError("ShadowRealm evaluation failed");
        } catch (JSVirtualMachineException e) {
            if (shadowContext.hasPendingException()) {
                shadowContext.clearPendingException();
            }
            return callerContext.throwTypeError("ShadowRealm evaluation failed");
        }
    }

    private static JSObject evaluateShadowRealmModule(JSContext shadowContext, String source, String filename) {
        JSObject exportsObject = shadowContext.createJSObject();

        Matcher exportVarMatcher = EXPORT_VAR_PATTERN.matcher(source);
        if (exportVarMatcher.find()) {
            String exportName = exportVarMatcher.group(1);
            String transformedSource = exportVarMatcher
                    .replaceFirst("var " + exportName + " = " + exportVarMatcher.group(2) + ";");
            shadowContext.eval(transformedSource, filename, false);
            JSValue exportValue = shadowContext.getGlobalObject().get(PropertyKey.fromString(exportName));
            if (shadowContext.hasPendingException()) {
                throw new JSException(shadowContext.getPendingException());
            }
            exportsObject.defineProperty(PropertyKey.fromString(exportName), exportValue,
                    PropertyDescriptor.DataState.ConfigurableWritable);
            return exportsObject;
        }

        if (source.contains("export ")) {
            throw new JSCompilerException("Unsupported module export syntax");
        }

        shadowContext.eval(source, filename, false);
        if (shadowContext.hasPendingException()) {
            throw new JSException(shadowContext.getPendingException());
        }
        return exportsObject;
    }

    private static String getReferrerFilename(JSContext callerContext) {
        JSStackFrame stackFrame = callerContext.getCurrentStackFrame();
        return stackFrame != null ? stackFrame.filename() : null;
    }

    private static JSValue getWrappedValue(JSContext callerContext, JSContext targetContext, JSValue value) {
        if (JSTypeChecking.isPrimitive(value)) {
            return value;
        }
        if (JSTypeChecking.isCallable(value)) {
            try {
                return createWrappedFunction(callerContext, targetContext, value);
            } catch (JSException e) {
                if (callerContext.hasPendingException()) {
                    return callerContext.getPendingException();
                }
                return callerContext.throwTypeError("Cannot wrap callable");
            }
        }
        return callerContext.throwTypeError("Cross-realm value is not wrappable");
    }

    public static JSValue importValue(JSContext callerContext, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSShadowRealm shadowRealm)) {
            return callerContext.throwTypeError("ShadowRealm.prototype.importValue requires a ShadowRealm receiver");
        }
        JSString specifierString = JSTypeConversions.toString(callerContext, JSValue.argumentOrUndefined(args, 0));
        if (callerContext.hasPendingException()) {
            return callerContext.getPendingException();
        }
        if (args.length < 2 || !(args[1] instanceof JSString exportNameValue)) {
            return callerContext.throwTypeError("ShadowRealm.prototype.importValue requires a string exportName");
        }

        JSPromise promise = callerContext.createJSPromise();
        String exportName = exportNameValue.value();
        String specifier = specifierString.value();
        JSContext shadowContext = shadowRealm.getShadowContext();

        try {
            // Through the host's module loader, like every other import: this used to read the path
            // with java.nio.file.Files, so importValue reached any file the process could read.
            IJSModuleLoader moduleLoader = callerContext.getRuntime().getOptions().getModuleLoader();
            String resolvedSpecifier = moduleLoader != null
                    ? moduleLoader.resolve(specifier, getReferrerFilename(callerContext))
                    : null;
            if (resolvedSpecifier == null) {
                rejectWithCallerTypeError(callerContext, promise, "ShadowRealm import failed");
                return promise;
            }
            String moduleSource = moduleLoader.loadText(resolvedSpecifier);
            JSObject exportsObject = evaluateShadowRealmModule(shadowContext, moduleSource, resolvedSpecifier);

            if (!exportsObject.hasOwnProperty(PropertyKey.fromString(exportName))) {
                rejectWithCallerTypeError(callerContext, promise, "Requested export does not exist");
                return promise;
            }

            JSValue exportValue = exportsObject.get(PropertyKey.fromString(exportName));
            JSValue wrappedExportValue = getWrappedValue(callerContext, shadowContext, exportValue);
            if (callerContext.hasPendingException()) {
                JSValue error = callerContext.takePendingException();
                promise.reject(error);
                return promise;
            }

            promise.resolve(callerContext, wrappedExportValue);
            return promise;
        } catch (JSCompilerException e) {
            rejectWithCallerTypeError(callerContext, promise, "ShadowRealm import parse failed", e.getSourceLocation());
            return promise;
        } catch (JSException e) {
            rejectWithCallerTypeError(callerContext, promise, "ShadowRealm import failed");
            return promise;
        } catch (IOException e) {
            rejectWithCallerTypeError(callerContext, promise, "ShadowRealm import failed");
            return promise;
        } catch (RuntimeException e) {
            rejectWithCallerTypeError(callerContext, promise, "ShadowRealm import failed");
            return promise;
        }
    }

    private static void rejectWithCallerTypeError(JSContext callerContext, JSPromise promise, String message) {
        rejectWithCallerTypeError(callerContext, promise, message, null);
    }

    private static void rejectWithCallerTypeError(JSContext callerContext, JSPromise promise, String message,
            SourceLocation sourceLocation) {
        JSValue errorValue = callerContext.throwTypeError(message, sourceLocation);
        callerContext.clearPendingException();
        promise.reject(errorValue);
    }
}
