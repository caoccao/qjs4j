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

package com.caoccao.qjs4j.compilation.parser;

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.*;

final class ParameterEarlyErrors {
    private ParameterEarlyErrors() {
    }

    private static void checkStrictBindingName(ParserContext parserContext, String name) {
        if (JSKeyword.EVAL.equals(name) || JSKeyword.ARGUMENTS.equals(name)) {
            throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
        }
        if (parserContext.isStrictReservedIdentifier(name)) {
            throw new JSSyntaxErrorException("Unexpected strict mode reserved word");
        }
    }

    static void rejectDuplicates(FunctionParams params) {
        Set<String> names = new HashSet<>();
        params.forEachBoundName(name -> requireNewName(names, name));
    }

    private static void requireNewName(Set<String> names, String name) {
        if (!names.add(name)) {
            throw new JSSyntaxErrorException("duplicate argument name not allowed in this context");
        }
    }

    static void validateArrow(ParserContext parserContext, FunctionParams functionParams, ASTNode body) {
        List<Expression> defaults = functionParams.defaults();
        // ES2024 14.2.1 Static Semantics: Early Errors
        // ArrowFunction : ArrowParameters => ConciseBody
        // It is a Syntax Error if ArrowParameters Contains YieldExpression is true.
        // It is a Syntax Error if ArrowParameters Contains AwaitExpression is true.
        if (defaults != null) {
            for (Expression defaultExpression : defaults) {
                if (defaultExpression != null && defaultExpression.containsYield()) {
                    throw new JSSyntaxErrorException("Yield expression not allowed in arrow function parameters");
                }
                if (defaultExpression != null && defaultExpression.containsAwait()) {
                    throw new JSSyntaxErrorException("Await expression not allowed in arrow function parameters");
                }
            }
        }
        boolean bodyHasUseStrict = body instanceof BlockStatement blockBody && blockBody.hasUseStrictDirective();
        boolean strictParameters = parserContext.strictMode || bodyHasUseStrict;
        Set<String> seen = new HashSet<>();
        functionParams.forEachBoundName(name -> {
            requireNewName(seen, name);
            if (strictParameters && (JSKeyword.EVAL.equals(name) || JSKeyword.ARGUMENTS.equals(name))) {
                throw new JSSyntaxErrorException("Unexpected eval or arguments in strict mode");
            }
        });
        if (bodyHasUseStrict && functionParams.hasNonSimpleParameters()) {
            throw new JSSyntaxErrorException(
                    "Illegal 'use strict' directive in function with non-simple parameter list");
        }
    }

    static void validateFormalsBodyDuplicate(FunctionParams params, List<Statement> body) {
        Set<String> paramNames = new HashSet<>();
        params.forEachBoundName(paramNames::add);
        // Every lexical declaration counts — class and using as well as let and const — and the message is
        // V8's.
        for (String declaredName : DeclarationEarlyErrors.collectFunctionBodyLexicalNames(body)) {
            if (paramNames.contains(declaredName)) {
                throw new JSSyntaxErrorException("Identifier '" + declaredName + "' has already been declared");
            }
        }
    }

    static void validateStrict(ParserContext context, FunctionParams params, Identifier name) {
        if (name != null) {
            checkStrictBindingName(context, name.getName());
        }
        params.forEachBoundName(bound -> checkStrictBindingName(context, bound));
        rejectDuplicates(params);
    }
}
