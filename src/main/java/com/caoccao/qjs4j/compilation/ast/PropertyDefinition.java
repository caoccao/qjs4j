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

package com.caoccao.qjs4j.compilation.ast;

import com.caoccao.qjs4j.core.JSKeyword;

import java.util.List;
import java.util.Set;

/**
 * Represents a field in a class (public or private).
 */
public final class PropertyDefinition extends ClassElement {
    private final boolean autoAccessor;
    private final boolean computed;
    private final boolean isPrivate;
    private final boolean isStatic;
    private final Expression key;
    private final Expression value;

    public PropertyDefinition(
            Expression key,
            Expression value,
            boolean computed,
            boolean isStatic,
            boolean isPrivate) {
        this(key, value, computed, isStatic, isPrivate, false);
    }

    public PropertyDefinition(
            Expression key,
            Expression value,
            boolean computed,
            boolean isStatic,
            boolean isPrivate,
            boolean autoAccessor) {
        super(key != null ? key.getLocation() : (value != null ? value.getLocation() : new SourceLocation(0, 0, 0, 0)));
        this.key = key;
        this.value = value;
        this.computed = computed;
        this.isStatic = isStatic;
        this.isPrivate = isPrivate;
        this.autoAccessor = autoAccessor;
    }

    public static String createAutoAccessorBackingName(int index, Set<String> existingNames) {
        String candidateName = "__auto_accessor_" + index;
        while (existingNames.contains(candidateName)) {
            index++;
            candidateName = "__auto_accessor_" + index;
        }
        return candidateName;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = key != null && key.containsAwait();
            if (!awaitInside && value != null && value.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = key != null && key.containsYield();
            if (!yieldInside && value != null && value.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public Expression getKey() {
        return key;
    }

    public Expression getValue() {
        return value;
    }

    public boolean isAutoAccessor() {
        return autoAccessor;
    }

    public boolean isComputed() {
        return computed;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public MethodDefinition toAutoAccessorMethod(String methodKind, String backingPrivateName) {
        SourceLocation location = getLocation();
        Identifier thisIdentifier = new Identifier("this", location);
        PrivateIdentifier backingPrivateIdentifier = new PrivateIdentifier(backingPrivateName, location);
        MemberExpression backingMemberExpression = new MemberExpression(
                thisIdentifier,
                backingPrivateIdentifier,
                false,
                false,
                location);

        FunctionExpression methodFunctionExpression;
        if (JSKeyword.GET.equals(methodKind)) {
            BlockStatement body = new BlockStatement(
                    List.of(new ReturnStatement(backingMemberExpression, location)),
                    location);
            methodFunctionExpression = new FunctionExpression(
                    null,
                    new FunctionParams(List.of(), null, null),
                    body,
                    false,
                    false,
                    false,
                    location);
        } else {
            Identifier valueIdentifier = new Identifier("value", location);
            AssignmentExpression assignmentExpression = new AssignmentExpression(
                    backingMemberExpression,
                    AssignmentOperator.ASSIGN,
                    valueIdentifier,
                    location);
            BlockStatement body = new BlockStatement(
                    List.of(new ExpressionStatement(assignmentExpression, location)),
                    location);
            methodFunctionExpression = new FunctionExpression(
                    null,
                    new FunctionParams(List.of(valueIdentifier), null, null),
                    body,
                    false,
                    false,
                    false,
                    location);
        }

        return new MethodDefinition(
                key,
                methodFunctionExpression,
                methodKind,
                computed,
                isStatic,
                false);
    }
}
