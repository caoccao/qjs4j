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

package com.caoccao.qjs4j.builtins.temporal.duration;

import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.core.*;

/** The paired addition and difference operations for a calendar date prototype. */
public record TemporalDateArithmetic<T extends JSObject>(TemporalReceiver<T> receiver,
        TemporalDateAddition.Apply<T> addition, TemporalReceiver.Difference<T> difference) {
    public JSValue shift(JSContext context, JSValue date, JSValue[] args, int sign) {
        return TemporalDateAddition.apply(context, date, args, sign, receiver, addition);
    }

    public JSValue between(JSContext context, JSValue date, JSValue[] args, boolean since) {
        return receiver.difference(context, date, args, since, difference);
    }
}
