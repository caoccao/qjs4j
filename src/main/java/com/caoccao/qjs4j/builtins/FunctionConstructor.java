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

        // Build the function source code
        StringBuilder functionSource = new StringBuilder("(function anonymous(");
        if (!paramNames.isEmpty()) {
            functionSource.append(String.join(", ", paramNames));
        }
        functionSource.append("\n) {\n");
        functionSource.append(body);
        functionSource.append("\n})");

        try {
            // Compile the function
            JSBytecodeFunction func = new Compiler(functionSource.toString(), "<Function>").compile(false).function();

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
            return context.throwError("Failed to create function: " + e.getMessage());
        }
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
