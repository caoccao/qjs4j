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

import com.caoccao.qjs4j.core.*;

/**
 * Implementation of Date constructor static methods.
 */
public final class DateConstructor {

    public static JSValue UTC(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSNumber.of(Double.NaN);
        }
        double[] fields = {0, 0, 1, 0, 0, 0, 0};
        int n = Math.min(args.length, 7);
        for (int i = 0; i < n; i++) {
            fields[i] = JSTypeConversions.toNumber(context, args[i]).value();
        }
        return JSNumber.of(JSDate.setDateFieldsChecked(fields, false));
    }

    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDate date = new JSDate(JSDate.dateNow());
        return DatePrototype.toStringMethod(context, date, args);
    }

    public static JSValue now(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSNumber.of(JSDate.dateNow());
    }

    public static JSValue parse(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSNumber.of(Double.NaN);
        }
        JSString dateString = JSTypeConversions.toString(context, args[0]);
        return JSNumber.of(JSDate.parseDateString(dateString.value()));
    }
}
