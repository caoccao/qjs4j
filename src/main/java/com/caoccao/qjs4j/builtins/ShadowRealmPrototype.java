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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ShadowRealm.prototype methods.
 * Implements spec behavior needed by test262 for evaluate() and importValue().
 */
public final class ShadowRealmPrototype {
    private static final Pattern EXPORT_VAR_PATTERN = Pattern.compile(
            "(?m)^\\s*export\\s+var\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(.+?);\\s*$");

    private ShadowRealmPrototype() {
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
            Compiler compilerCheck = new Compiler(sourceText, "<shadowrealm>");
            compilerCheck.setPredeclareProgramLexicalsAsLocals(true);
            compilerCheck.compile(false);
        } catch (JSCompilerException e) {
            return callerContext.throwSyntaxError(e.getMessage());
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

    public static JSValue importValue(JSContext callerContext, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSShadowRealm shadowRealm)) {
            return callerContext.throwTypeError("ShadowRealm.prototype.importValue requires a ShadowRealm receiver");
        }
        JSString specifierString = JSTypeConversions.toString(callerContext, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
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
            Path modulePath = resolveModulePath(callerContext, specifier);
            String moduleSource = Files.readString(modulePath);
            JSObject exportsObject = evaluateShadowRealmModule(shadowContext, moduleSource, modulePath.toString());

            if (!exportsObject.hasOwnProperty(PropertyKey.fromString(exportName))) {
                rejectWithCallerTypeError(callerContext, promise, "Requested export does not exist");
                return promise;
            }

            JSValue exportValue = exportsObject.get(callerContext, PropertyKey.fromString(exportName));
            JSValue wrappedExportValue = getWrappedValue(callerContext, shadowContext, exportValue);
            if (callerContext.hasPendingException()) {
                JSValue error = callerContext.getPendingException();
                callerContext.clearPendingException();
                promise.reject(error);
                return promise;
            }

            promise.resolve(callerContext, wrappedExportValue);
            return promise;
        } catch (JSCompilerException e) {
            rejectWithCallerTypeError(callerContext, promise, "ShadowRealm import parse failed");
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

    private static JSNativeFunction createWrappedFunction(JSContext callerContext, JSContext targetContext, JSValue targetCallable) {
        JSNativeFunction wrappedFunction = new JSNativeFunction("", 0, (callbackContext, thisArg, args) -> {
            JSValue[] wrappedArguments = new JSValue[args.length];
            for (int i = 0; i < args.length; i++) {
                JSValue wrappedArgument = getWrappedValue(targetContext, callerContext, args[i]);
                if (targetContext.hasPendingException()) {
                    JSValue ignored = targetContext.getPendingException();
                    targetContext.clearPendingException();
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

    private static void copyNameAndLength(JSContext callerContext, JSContext targetContext,
                                          JSValue targetCallable, JSNativeFunction wrappedFunction) {
        if (!(targetCallable instanceof JSObject targetObject)) {
            throw new JSException(callerContext.throwTypeError("Wrapped target is not an object"));
        }

        double wrappedLength = 0;
        try {
            if (targetObject.hasOwnProperty(PropertyKey.LENGTH)) {
                JSValue targetLength = targetObject.get(targetContext, PropertyKey.LENGTH);
                if (targetContext.hasPendingException()) {
                    targetContext.clearPendingException();
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
                        double truncatedLength = numericLength < 0 ? Math.ceil(numericLength) : Math.floor(numericLength);
                        wrappedLength = Math.max(truncatedLength, 0);
                    }
                }
            }

            JSValue targetName = targetObject.get(targetContext, PropertyKey.NAME);
            if (targetContext.hasPendingException()) {
                targetContext.clearPendingException();
                throw new JSException(callerContext.throwTypeError("Cannot wrap callable"));
            }
            String wrappedName = targetName instanceof JSString targetNameString ? targetNameString.value() : "";

            wrappedFunction.defineProperty(PropertyKey.LENGTH,
                    PropertyDescriptor.dataDescriptor(JSNumber.of(wrappedLength), PropertyDescriptor.DataState.Configurable));
            wrappedFunction.defineProperty(PropertyKey.NAME,
                    PropertyDescriptor.dataDescriptor(new JSString(wrappedName), PropertyDescriptor.DataState.Configurable));
        } catch (JSException e) {
            if (callerContext.hasPendingException()) {
                throw e;
            }
            throw new JSException(callerContext.throwTypeError("Cannot wrap callable"));
        } catch (RuntimeException e) {
            throw new JSException(callerContext.throwTypeError("Cannot wrap callable"));
        }
    }

    private static JSObject evaluateShadowRealmModule(JSContext shadowContext, String source, String filename) {
        JSObject exportsObject = shadowContext.createJSObject();

        Matcher exportVarMatcher = EXPORT_VAR_PATTERN.matcher(source);
        if (exportVarMatcher.find()) {
            String exportName = exportVarMatcher.group(1);
            String transformedSource = exportVarMatcher.replaceFirst("var " + exportName + " = " + exportVarMatcher.group(2) + ";");
            shadowContext.eval(transformedSource, filename, false);
            JSValue exportValue = shadowContext.getGlobalObject().get(shadowContext, PropertyKey.fromString(exportName));
            if (shadowContext.hasPendingException()) {
                throw new JSException(shadowContext.getPendingException());
            }
            exportsObject.defineProperty(PropertyKey.fromString(exportName), exportValue, PropertyDescriptor.DataState.ConfigurableWritable);
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

    private static void rejectWithCallerTypeError(JSContext callerContext, JSPromise promise, String message) {
        JSValue errorValue = callerContext.throwTypeError(message);
        callerContext.clearPendingException();
        promise.reject(errorValue);
    }

    private static Path resolveModulePath(JSContext callerContext, String specifier) {
        Path specifierPath = Paths.get(specifier);
        if (specifierPath.isAbsolute()) {
            return specifierPath.normalize();
        }

        JSContext.StackFrame stackFrame = callerContext.getCurrentStackFrame();
        if (stackFrame != null) {
            String filename = stackFrame.filename();
            if (filename != null && !filename.isEmpty() && !filename.startsWith("<")) {
                Path currentFile = Paths.get(filename);
                Path parent = currentFile.getParent();
                if (parent != null) {
                    return parent.resolve(specifier).normalize();
                }
            }
        }
        return specifierPath.normalize();
    }
}
