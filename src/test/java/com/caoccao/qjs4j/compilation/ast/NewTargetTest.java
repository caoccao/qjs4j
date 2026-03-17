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

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class NewTargetTest extends BaseJavetTest {

    @Test
    public void testNewTargetInDefaultParameter() {
        assertBooleanWithJavet(
                "function F(x = new.target) { this.x = x; } new F().x === F");
    }

    @Test
    public void testNewTargetInEvalAtGlobalLevel() {
        assertErrorWithJavet("eval('new.target')");
    }

    @Test
    public void testNewTargetInEvalFunctionExpressionDefaults() {
        assertBooleanWithJavet(
                "var f = eval('(function(x = new.target) { this.x = x; })'); new f().x === f");
    }

    @Test
    public void testNewTargetInEvalInsideArrowInsideFunction() {
        assertBooleanWithJavet(
                "function F() { this.nt = (() => eval('new.target'))(); } new F().nt === F");
    }

    @Test
    public void testNewTargetInEvalInsideFunction() {
        assertBooleanWithJavet(
                "function F() { this.nt = eval('new.target'); } new F().nt === F");
    }

    @Test
    public void testNewTargetInEvalInsideFunctionWithoutNew() {
        assertUndefinedWithJavet(
                "function F() { return eval('new.target'); } F()");
    }

    @Test
    public void testNewTargetInFunction() {
        assertBooleanWithJavet(
                "function F() { this.nt = new.target; } new F().nt === F");
    }

    @Test
    public void testNewTargetInNestedEval() {
        assertBooleanWithJavet(
                "function F() { this.nt = eval(\"eval('new.target')\"); } new F().nt === F");
    }

    @Test
    public void testNewTargetUndefinedWithoutNew() {
        assertBooleanWithJavet(
                "function F() { return new.target === undefined; } F()");
    }
}
