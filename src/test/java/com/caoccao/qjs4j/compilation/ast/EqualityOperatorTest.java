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
import com.caoccao.qjs4j.core.JSNull;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSTypeConversions;
import com.caoccao.qjs4j.core.JSUndefined;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EqualityOperatorTest extends BaseJavetTest {
    @Test
    public void testHTMLDDAIsEqualToNullishValues() {
        JSObject htmlDDA = context.createJSObject();
        htmlDDA.setHTMLDDA(true);

        assertThat(JSTypeConversions.abstractEquals(context, htmlDDA, JSNull.INSTANCE)).isTrue();
        assertThat(JSTypeConversions.abstractEquals(context, htmlDDA, JSUndefined.INSTANCE)).isTrue();
        assertThat(JSTypeConversions.abstractEquals(context, JSNull.INSTANCE, htmlDDA)).isTrue();
        assertThat(JSTypeConversions.abstractEquals(context, JSUndefined.INSTANCE, htmlDDA)).isTrue();
    }

    @Test
    public void testObjectNullishEqualityDoesNotCoerceObject() {
        assertStringWithJavet("""
                var coercionCount = 0;
                var value = {
                    valueOf() {
                        coercionCount++;
                        return 0;
                    },
                    toString() {
                        coercionCount++;
                        return '';
                    }
                };
                [
                    value == null,
                    null == value,
                    value != null,
                    null != value,
                    value == undefined,
                    undefined == value,
                    value != undefined,
                    undefined != value,
                    coercionCount
                ].join(',');
                """);
    }
}
