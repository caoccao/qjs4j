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

package com.caoccao.qjs4j.compilation.compiler;

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.utils.DtoaConverter;

/**
 * Names the callee of a call the way V8's CallPrinter does in {@code TypeError: o.a.b is not a function}.
 * <p>
 * The name is the callee's source, normalised: a string key is printed as a property access ({@code o['a']} is
 * {@code o.a}), an inner call's arguments as {@code (...)}, and an expression that has no short spelling as
 * {@code (intermediate value)}. It is computed when the call is compiled. The message used to be rebuilt at run time
 * from the property reads that preceded the call, which named the wrong expression whenever a computed key or a call
 * was evaluated in between ({@code [Symbol.Symbol.iterator] is not a function} for {@code o[Symbol.iterator]()}).
 */
final class CallPrinter {
    private static final String INTERMEDIATE_VALUE = "(intermediate value)";

    private CallPrinter() {
    }

    private static String describe(Expression expression) {
        if (expression instanceof Identifier identifier) {
            return identifier.getName();
        }
        if (expression instanceof MemberExpression memberExpression) {
            return describeMember(memberExpression);
        }
        if (expression instanceof CallExpression callExpression) {
            return describe(callExpression.getCallee()) + "(...)";
        }
        if (expression instanceof Literal literal) {
            return describeLiteral(literal);
        }
        if (expression instanceof UnaryExpression unaryExpression) {
            return describeUnary(unaryExpression);
        }
        if (expression instanceof ObjectExpression objectExpression) {
            return objectExpression.getProperties().isEmpty() ? "{}" : "{" + INTERMEDIATE_VALUE + "}";
        }
        if (expression instanceof TemplateLiteral templateLiteral) {
            // V8 prints a template by its substitutions alone.
            StringBuilder builder = new StringBuilder();
            for (Expression substitution : templateLiteral.getExpressions()) {
                builder.append(describe(substitution));
            }
            return builder.toString();
        }
        return INTERMEDIATE_VALUE;
    }

    /**
     * Name a callee.
     *
     * @param callee
     *            the callee expression
     * @return the name V8 prints, or null when the value itself should be described, as V8 does for a literal callee
     */
    static String describeCallee(Expression callee) {
        if (callee instanceof Literal || callee instanceof TemplateLiteral) {
            return null;
        }
        return describe(callee);
    }

    private static String describeLiteral(Literal literal) {
        Object value = literal.getValue();
        if (value instanceof String string) {
            return "\"" + string + "\"";
        }
        if (value instanceof Number number) {
            return DtoaConverter.convert(number.doubleValue());
        }
        return String.valueOf(value);
    }

    private static String describeMember(MemberExpression memberExpression) {
        String object = describe(memberExpression.getObject());
        String access = memberExpression.isOptional() ? "?." : ".";
        Expression property = memberExpression.getProperty();
        if (property instanceof PrivateIdentifier privateIdentifier) {
            // V8 prints a private name as a computed key: this[#name].
            return object + (memberExpression.isOptional() ? "?.[#" : "[#") + privateIdentifier.getName() + "]";
        }
        if (!memberExpression.isComputed()) {
            return object + access + describe(property);
        }
        if (property instanceof Literal literal && literal.getValue() instanceof String key) {
            return object + access + key;
        }
        if (property instanceof TemplateLiteral templateLiteral && templateLiteral.getExpressions().isEmpty()) {
            return object + access + templateLiteral.getQuasis().get(0);
        }
        // A template key with substitutions falls through to describe(): o[`a${k}`] prints as o[k].
        return object + (memberExpression.isOptional() ? "?.[" : "[") + describe(property) + "]";
    }

    private static String describeUnary(UnaryExpression unaryExpression) {
        Expression operand = unaryExpression.getOperand();
        String operator = unaryExpression.getOperator().displayText();
        if (!unaryExpression.isPrefix()) {
            return "(" + describe(operand) + operator + ")";
        }
        // V8's parser folds a sign applied to a number literal: o[-1] and o[+1] print as o[-1] and o[1].
        if (operand instanceof Literal literal && literal.getValue() instanceof Number) {
            if (unaryExpression.getOperator() == UnaryExpression.UnaryOperator.MINUS) {
                return "-" + describeLiteral(literal);
            }
            if (unaryExpression.getOperator() == UnaryExpression.UnaryOperator.PLUS) {
                return describeLiteral(literal);
            }
        }
        return "(" + operator + describe(operand) + ")";
    }
}
