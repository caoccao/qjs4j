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

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * The strict-mode early error for {@code delete} is stated over {@code IdentifierReference}.
 * {@code this} and {@code new.target} are parsed as {@code Identifier} nodes because they resolve
 * like one, but grammatically they are a {@code PrimaryExpression} and a {@code MetaProperty}, so
 * deleting them is legal in strict mode and evaluates to {@code true}.
 * <p>
 * Both assertions run in sloppy and strict mode: {@code BaseJavetTest} evaluates every source twice.
 */
public class StrictModeDeleteOperandTest extends BaseJavetTest {
    @Test
    void testDeleteIdentifierReferenceIsStillAnEarlyError() {
        assertErrorWithJavet("'use strict';\nvar x = 1;\ndelete x;");
    }

    @Test
    void testDeleteMemberExpressionIsAllowed() {
        assertBooleanWithJavet("var o = { a: 1 }; delete o.a;");
    }

    @Test
    void testDeleteNewTargetIsAllowed() {
        assertBooleanWithJavet("(function () { return delete (new.target); })();");
        assertBooleanWithJavet("(function () { return delete void typeof +-~!(new.target); })();");
    }

    @Test
    void testDeleteThisIsAllowed() {
        assertBooleanWithJavet("(function () { return delete this; })();");
        assertBooleanWithJavet("delete this;");
    }

    @Test
    void testForOfTargetStillRejectsThisAndNewTarget() {
        // The same predicate now classifies the for-in/of assignment target, so `this` must still
        // be rejected — with V8's wording, which the message did not use before.
        assertErrorWithJavet("for (this of []) ;");
        assertErrorWithJavet("for (new.target in {}) ;");
    }
}
