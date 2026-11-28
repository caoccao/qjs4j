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

package com.caoccao.qjs4j.builtins.collection;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.builtins.object.ObjectConstructor;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.collection.JSMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;

class GroupByTest extends BaseTest {
    private void assertGroup(JSValue group, double first, double second) {
        JSArray array = group.asArray().orElseThrow();
        assertThat(array.getLength()).isEqualTo(2);
        assertNumberValue(array.get(0), first);
        assertNumberValue(array.get(1), second);
    }

    @ParameterizedTest
    @EnumSource(GroupBy.class)
    void testGroupBy(GroupBy groupBy) {
        JSArray items = context.createJSArray(nativeArguments(1, 2, 3, 4));
        JSFunction callback = new JSNativeFunction(context, "parity", 3, (child, receiver, args) -> {
            double number = args[0].asNumber().orElseThrow().value();
            return new JSString(number % 2 == 0 ? "even" : "odd");
        });

        JSValue result = groupBy.call(context, items, callback);
        JSValue empty = groupBy.call(context, context.createJSArray(), callback);
        if (groupBy == GroupBy.MAP) {
            JSMap groups = result.asMap().orElseThrow();
            assertGroup(groups.mapGet(new JSString("even")), 2, 4);
            assertGroup(groups.mapGet(new JSString("odd")), 1, 3);
            assertThat(empty.asMap().orElseThrow().size()).isZero();
        } else {
            JSObject groups = result.asObject().orElseThrow();
            assertGroup(groups.get("even"), 2, 4);
            assertGroup(groups.get("odd"), 1, 3);
            assertThat(empty.asObject().orElseThrow().getOwnPropertyKeys()).isEmpty();
        }

        assertPendingTypeError(groupBy.call(context, items));
        assertPendingTypeError(groupBy.call(context, JSNull.INSTANCE, callback));
        assertPendingTypeError(groupBy.call(context, new JSNumber(1), callback));
        assertPendingTypeError(groupBy.call(context, items, new JSString("not function")));
    }

    enum GroupBy {
        MAP(MapConstructor::groupBy),
        OBJECT(ObjectConstructor::groupBy);
        private final JSNativeCallback operation;

        GroupBy(JSNativeCallback operation) {
            this.operation = operation;
        }

        JSValue call(JSContext context, JSValue... args) {
            return operation.call(context, JSUndefined.INSTANCE, args);
        }
    }
}
