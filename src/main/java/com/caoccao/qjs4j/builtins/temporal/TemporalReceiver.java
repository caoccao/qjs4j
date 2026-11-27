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

package com.caoccao.qjs4j.builtins.temporal;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

import java.util.Comparator;
import java.util.function.*;

/** Receiver validation shared by the methods and getters of one Temporal prototype. */
record TemporalReceiver<T extends JSObject>(Class<T> type, String typeName,
        BiFunction<JSContext, JSValue, JSValue> conversion, PropertyKey prototypeKey) {
    private TemporalReceiver(Class<T> type, String name, BiFunction<JSContext, JSValue, JSValue> conversion) {
        this(type, "Temporal." + name, conversion, PropertyKey.fromString(name));
    }

    T require(JSContext context, JSValue receiver, String method) {
        return TemporalUtils.checkReceiver(context, receiver, type, typeName, method);
    }

    JSValue read(JSContext context, JSValue receiver, String property, Function<T, JSValue> getter) {
        T value = require(context, receiver, property);
        return value == null ? JSUndefined.INSTANCE : getter.apply(value);
    }

    JSValue rejectValueOf(JSContext context) {
        context.throwTypeError("Do not use " + typeName + ".prototype.valueOf; use " + typeName
                + ".prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    static final TemporalReceiver<JSTemporalPlainDate> PLAIN_DATE = new TemporalReceiver<>(JSTemporalPlainDate.class,
            "PlainDate",
            (context, item) -> TemporalPlainDateConstructor.toTemporalDate(context, item, JSUndefined.INSTANCE));

    static final TemporalReceiver<JSTemporalPlainTime> PLAIN_TIME = new TemporalReceiver<>(JSTemporalPlainTime.class,
            "PlainTime",
            (context, item) -> TemporalPlainTimeConstructor.toTemporalTime(context, item, JSUndefined.INSTANCE));

    static final TemporalReceiver<JSTemporalPlainDateTime> PLAIN_DATE_TIME = new TemporalReceiver<>(
            JSTemporalPlainDateTime.class, "PlainDateTime", (context, item) -> TemporalPlainDateTimeConstructor
                    .toTemporalDateTime(context, item, JSUndefined.INSTANCE));

    static final TemporalReceiver<JSTemporalPlainYearMonth> PLAIN_YEAR_MONTH = new TemporalReceiver<>(
            JSTemporalPlainYearMonth.class, "PlainYearMonth", (context, item) -> TemporalPlainYearMonthConstructor
                    .toTemporalYearMonth(context, item, JSUndefined.INSTANCE));

    static final TemporalReceiver<JSTemporalPlainMonthDay> PLAIN_MONTH_DAY = new TemporalReceiver<>(
            JSTemporalPlainMonthDay.class, "PlainMonthDay", (context, item) -> TemporalPlainMonthDayConstructor
                    .toTemporalMonthDay(context, item, JSUndefined.INSTANCE));

    static final TemporalReceiver<JSTemporalInstant> INSTANT = new TemporalReceiver<>(JSTemporalInstant.class,
            "Instant", TemporalInstantConstructor::toTemporalInstant);

    static final TemporalReceiver<JSTemporalZonedDateTime> ZONED_DATE_TIME = new TemporalReceiver<>(
            JSTemporalZonedDateTime.class, "ZonedDateTime", TemporalZonedDateTimeConstructor::toTemporalZonedDateTime);

    static final TemporalReceiver<JSTemporalDuration> DURATION = new TemporalReceiver<>(JSTemporalDuration.class,
            "Duration", TemporalDurationConstructor::toTemporalDuration);

    T convert(JSContext context, JSValue item) {
        JSValue result = conversion.apply(context, item);
        return context.hasPendingException() ? null : type.cast(result);
    }

    JSValue compare(JSContext context, JSValue[] args, Comparator<T> comparator) {
        T first = comparisonOperand(context, JSValue.argumentOrUndefined(args, 0));
        if (first == null) {
            return JSUndefined.INSTANCE;
        }
        T second = comparisonOperand(context, JSValue.argumentOrUndefined(args, 1));
        return second == null ? JSUndefined.INSTANCE : JSNumber.of(comparator.compare(first, second));
    }

    JSValue equalTo(JSContext context, JSValue receiver, JSValue[] args, BiPredicate<T, T> predicate) {
        T first = require(context, receiver, "equals");
        if (first == null) {
            return JSUndefined.INSTANCE;
        }
        T second = comparisonOperand(context, JSValue.argumentOrUndefined(args, 0));
        return second == null ? JSUndefined.INSTANCE : JSBoolean.valueOf(predicate.test(first, second));
    }

    JSValue difference(JSContext context, JSValue receiver, JSValue[] args, boolean since, Difference<T> difference) {
        T first = require(context, receiver, since ? "since" : "until");
        if (first == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue item = JSValue.argumentOrUndefined(args, 0);
        T second = type.isInstance(item) ? type.cast(item) : convert(context, item);
        return second == null
                ? JSUndefined.INSTANCE
                : difference.between(context, first, second, JSValue.argumentOrUndefined(args, 1), since);
    }

    @FunctionalInterface
    interface Difference<T> {
        JSValue between(JSContext context, T first, T second, JSValue options, boolean since);
    }

    private T comparisonOperand(JSContext context, JSValue value) {
        if (!type.isInstance(value) || context.hasPendingException()) {
            return convert(context, value);
        }
        // A same-type conversion looks up the prototype before making a copy. Comparisons
        // only read immutable slots, but the lookup can invoke user-defined getters.
        TemporalUtils.getTemporalPrototypeByKey(context, prototypeKey);
        return context.hasPendingException() ? null : type.cast(value);
    }
}
