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

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * EvalDeclarationInstantiation step 5 is conditioned on the strictness of the <em>eval code</em>,
 * not of the caller. An indirect eval is never strict by inheritance, so a strict enclosing script
 * must not suppress the global-lexical collision check.
 */
public class JSIndirectEvalStrictnessTest extends BaseJavetTest {
    @Test
    void testDirectEvalInStrictCodeGetsItsOwnVarScope() {
        // Direct eval in strict code is strict, so `var x` is scoped to the eval and collides with
        // nothing.
        assertStringWithJavet("""
                'use strict';
                let x;
                var caught;
                try { eval('var x;'); } catch (e) { caught = e; }
                String(caught && caught.constructor.name);""");
    }

    @Test
    void testIndirectEvalIsNotStrictInsideStrictCode() {
        assertBooleanWithJavet("""
                'use strict';
                (0, eval)('this === globalThis');""");
    }

    @Test
    void testIndirectEvalVarCollidesWithGlobalLetInStrictCode() {
        assertStringWithJavet("""
                'use strict';
                let x;
                var caught;
                try { (0, eval)('var x;'); } catch (e) { caught = e; }
                String(caught && caught.constructor.name);""");
    }

    @Test
    void testIndirectEvalVarCollidesWithGlobalLetInStrictFunction() {
        assertStringWithJavet("""
                let y;
                function run() {
                  'use strict';
                  var caught;
                  try { (0, eval)('var y;'); } catch (e) { caught = e; }
                  return String(caught && caught.constructor.name);
                }
                run();""");
    }

    @Test
    void testIndirectEvalVarWithoutCollisionSucceeds() {
        assertStringWithJavet("""
                'use strict';
                var caught;
                try { (0, eval)('var freshBinding;'); } catch (e) { caught = e; }
                String(caught && caught.constructor.name);""");
    }
}
