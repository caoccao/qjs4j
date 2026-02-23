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
import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSTypeConversions;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the Function constructor.
 * This is a placeholder implementation - creating functions dynamically
 * from strings would require the compiler.
 */
public final class FunctionConstructor {

    private FunctionConstructor() {
        // Private constructor to prevent instantiation
    }

    /**
     * Function constructor call.
     * new Function(arg1, arg2, ..., argN, functionBody)
     * <p>
     * Creates a new function from the given parameter names and function body.
     * All arguments except the last are treated as parameter names.
     * The last argument is the function body.
     * <p>
     * Examples:
     * - new Function('a', 'b', 'return a + b')
     * - new Function('x', 'console.log(x)')
     * - new Function('return 42')
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return callWithWrapper(context, args, "(function anonymous(", "\n) {\n", "\n})", "<Function>",
                "Failed to create function: ");
    }

    public static JSValue callAsync(JSContext context, JSValue thisArg, JSValue[] args) {
        return callWithWrapper(context, args, "(async function anonymous(", "\n) {\n", "\n})",
                "<AsyncFunction>", "Failed to create async function: ");
    }

    public static JSValue callAsyncGenerator(JSContext context, JSValue thisArg, JSValue[] args) {
        return callWithWrapper(context, args, "(async function* anonymous(", "\n) {\n", "\n})",
                "<AsyncGeneratorFunction>", "Failed to create async generator function: ");
    }

    public static JSValue callGenerator(JSContext context, JSValue thisArg, JSValue[] args) {
        return callWithWrapper(context, args, "(function* anonymous(", "\n) {\n", "\n})",
                "<GeneratorFunction>", "Failed to create generator function: ");
    }

    private static JSValue callWithWrapper(
            JSContext context,
            JSValue[] args,
            String sourcePrefix,
            String parameterSuffix,
            String bodySuffix,
            String filename,
            String errorPrefix) {
        // Extract parameter names and function body
        List<String> paramNames = new ArrayList<>();
        String body = "";

        if (args.length == 0) {
            // No arguments: function () {}
            body = "";
        } else if (args.length == 1) {
            // Only body provided: function () { body }
            body = toSourceString(context, args[0]);
            if (body == null) {
                return context.getPendingException();
            }
        } else {
            // Parameters + body: function (p1, p2, ...) { body }
            for (int i = 0; i < args.length - 1; i++) {
                String paramName = toSourceString(context, args[i]);
                if (paramName == null) {
                    return context.getPendingException();
                }
                paramNames.add(paramName);
            }
            body = toSourceString(context, args[args.length - 1]);
            if (body == null) {
                return context.getPendingException();
            }
        }

        // ES2024 CreateDynamicFunction step 30-31:
        // If AllPrivateIdentifiersValid of body/parameters with empty list is false, throw SyntaxError
        if (containsPrivateIdentifier(body)) {
            return context.throwSyntaxError("Unexpected private field");
        }
        for (String paramName : paramNames) {
            if (containsPrivateIdentifier(paramName)) {
                return context.throwSyntaxError("Unexpected private field");
            }
        }

        // Build the function source code
        StringBuilder functionSource = new StringBuilder(sourcePrefix);
        if (!paramNames.isEmpty()) {
            functionSource.append(String.join(",", paramNames));
        }
        functionSource.append(parameterSuffix);
        functionSource.append(body);
        functionSource.append(bodySuffix);

        try {
            // Compile the function
            JSBytecodeFunction func = new Compiler(functionSource.toString(), filename).compile(false).function();

            // Initialize the function's prototype chain
            func.initializePrototypeChain(context);

            // Execute the compiled code to get the function object
            JSValue result = context.getVirtualMachine().execute(func, context.getGlobalObject(), new JSValue[0]);

            return result;
        } catch (JSCompilerException | JSSyntaxErrorException e) {
            return context.throwSyntaxError(e.getMessage());
        } catch (JSException e) {
            return e.getErrorValue();
        } catch (Exception e) {
            return context.throwError(errorPrefix + e.getMessage());
        }
    }

    /**
     * Check if a source string contains a private identifier (#name).
     * Used for AllPrivateIdentifiersValid validation in CreateDynamicFunction.
     */
    private static boolean containsPrivateIdentifier(String source) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        for (int i = 0; i < source.length() - 1; i++) {
            char ch = source.charAt(i);
            if (ch == '#') {
                // Check if next char is a valid identifier start
                char next = source.charAt(i + 1);
                if (Character.isLetter(next) || next == '_' || next == '$') {
                    // Check if preceded by a dot (private field access like o.#f)
                    // or at start or after whitespace/operator
                    return true;
                }
            }
            // Skip string literals
            if (ch == '\'' || ch == '"' || ch == '`') {
                char quote = ch;
                i++;
                while (i < source.length()) {
                    char c = source.charAt(i);
                    if (c == '\\') {
                        i++; // skip escaped character
                    } else if (c == quote) {
                        break;
                    }
                    i++;
                }
            }
            // Skip single-line comments
            if (ch == '/' && i + 1 < source.length()) {
                if (source.charAt(i + 1) == '/') {
                    i += 2;
                    while (i < source.length() && source.charAt(i) != '\n') {
                        i++;
                    }
                } else if (source.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < source.length() && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                        i++;
                    }
                    i++; // skip the closing /
                }
            }
        }
        return false;
    }

    private static String toSourceString(JSContext context, JSValue value) {
        try {
            String result = JSTypeConversions.toString(context, value).value();
            if (context.hasPendingException()) {
                return null;
            }
            return result;
        } catch (JSException e) {
            return null;
        }
    }
}
