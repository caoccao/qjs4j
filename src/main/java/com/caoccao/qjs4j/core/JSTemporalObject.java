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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Implementation of Temporal object and Temporal.* prototype methods.
 */
public final class JSTemporalObject {
    public static final String TEMPORAL_PLAIN_MONTH_DAY_BRAND_PROPERTY = "$$TemporalPlainMonthDayBrand$$";

    private JSTemporalObject() {
    }

    public static boolean isTemporalPlainMonthDayValue(JSObject objectValue) {
        JSValue brand = objectValue.get(PropertyKey.fromString(TEMPORAL_PLAIN_MONTH_DAY_BRAND_PROPERTY));
        return brand instanceof JSBoolean jsBoolean && jsBoolean.value();
    }

    public static double temporalPlainMonthDayToEpochMillis(JSContext context, JSObject plainMonthDayObject) {
        JSValue monthCodeValue = plainMonthDayObject.get(PropertyKey.fromString("monthCode"));
        if (!(monthCodeValue instanceof JSString monthCodeString)) {
            context.throwTypeError("Invalid Temporal.PlainMonthDay value");
            return Double.NaN;
        }
        int month = parseTemporalPlainMonthDayMonth(context, monthCodeString.value());
        if (context.hasPendingException()) {
            return Double.NaN;
        }

        JSValue dayValue = plainMonthDayObject.get(PropertyKey.fromString("day"));
        int day;
        if (dayValue instanceof JSNumber dayNumber) {
            double numericDay = dayNumber.value();
            if (!Double.isFinite(numericDay) || Math.floor(numericDay) != numericDay) {
                context.throwRangeError("Invalid Temporal.PlainMonthDay value");
                return Double.NaN;
            }
            day = (int) numericDay;
        } else {
            double numericDay = JSTypeConversions.toNumber(context, dayValue).value();
            if (context.hasPendingException() || !Double.isFinite(numericDay) || Math.floor(numericDay) != numericDay) {
                context.throwRangeError("Invalid Temporal.PlainMonthDay value");
                return Double.NaN;
            }
            day = (int) numericDay;
        }
        if (day < 1 || day > 31) {
            context.throwRangeError("Invalid Temporal.PlainMonthDay value");
            return Double.NaN;
        }

        try {
            LocalDate referenceDate = LocalDate.of(1972, month, day);
            return referenceDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (DateTimeException e) {
            context.throwRangeError("Invalid Temporal.PlainMonthDay value");
            return Double.NaN;
        }
    }

    private static int parseTemporalPlainMonthDayMonth(JSContext context, String monthCode) {
        if (monthCode == null || monthCode.length() != 3 || monthCode.charAt(0) != 'M') {
            context.throwTypeError("Invalid Temporal.PlainMonthDay value");
            return -1;
        }
        char tens = monthCode.charAt(1);
        char ones = monthCode.charAt(2);
        if (!Character.isDigit(tens) || !Character.isDigit(ones)) {
            context.throwTypeError("Invalid Temporal.PlainMonthDay value");
            return -1;
        }
        int month = Integer.parseInt(monthCode.substring(1));
        if (month < 1 || month > 12) {
            context.throwRangeError("Invalid Temporal.PlainMonthDay value");
            return -1;
        }
        return month;
    }

    static JSValue plainMonthDayConstructorCall(JSContext childContext, JSValue thisArg, JSValue[] args) {
        return childContext.throwTypeError("Temporal.PlainMonthDay constructor cannot be called directly");
    }

    static JSValue plainMonthDayFrom(JSContext childContext, JSObject plainMonthDayPrototype, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(item instanceof JSObject itemObject)) {
            return childContext.throwTypeError("Temporal.PlainMonthDay.from requires an object");
        }

        JSValue monthCodeValue = itemObject.get(PropertyKey.fromString("monthCode"));
        if (!(monthCodeValue instanceof JSString monthCodeString)) {
            return childContext.throwTypeError("Invalid Temporal.PlainMonthDay value");
        }
        int month = parseTemporalPlainMonthDayMonth(childContext, monthCodeString.value());
        if (childContext.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue dayValue = itemObject.get(PropertyKey.fromString("day"));
        double dayNumeric = JSTypeConversions.toNumber(childContext, dayValue).value();
        if (childContext.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!Double.isFinite(dayNumeric) || Math.floor(dayNumeric) != dayNumeric) {
            return childContext.throwRangeError("Invalid Temporal.PlainMonthDay value");
        }
        int day = (int) dayNumeric;
        if (day < 1 || day > 31) {
            return childContext.throwRangeError("Invalid Temporal.PlainMonthDay value");
        }

        JSValue calendarValue = itemObject.get(PropertyKey.fromString("calendar"));
        String calendar = "iso8601";
        if (!(calendarValue instanceof JSUndefined)) {
            calendar = JSTypeConversions.toString(childContext, calendarValue).value();
            if (childContext.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        try {
            LocalDate.of(1972, month, day);
        } catch (DateTimeException e) {
            return childContext.throwRangeError("Invalid Temporal.PlainMonthDay value");
        }

        JSObject plainMonthDay = childContext.createJSObject();
        plainMonthDay.setPrototype(plainMonthDayPrototype);
        plainMonthDay.set(PropertyKey.fromString("monthCode"), new JSString(String.format(Locale.ROOT, "M%02d", month)));
        plainMonthDay.set(PropertyKey.fromString("day"), JSNumber.of(day));
        plainMonthDay.set(PropertyKey.fromString("calendar"), new JSString(calendar));
        plainMonthDay.defineProperty(
                PropertyKey.fromString(TEMPORAL_PLAIN_MONTH_DAY_BRAND_PROPERTY),
                JSBoolean.TRUE,
                PropertyDescriptor.DataState.None);
        return plainMonthDay;
    }

    static JSValue plainMonthDayToLocaleString(JSContext childContext, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject thisObject)) {
            return childContext.throwTypeError("Temporal.PlainMonthDay.prototype.toLocaleString called on incompatible receiver");
        }
        JSValue brandValue = thisObject.get(PropertyKey.fromString(TEMPORAL_PLAIN_MONTH_DAY_BRAND_PROPERTY));
        if (!(brandValue instanceof JSBoolean jsBoolean) || !jsBoolean.value()) {
            return childContext.throwTypeError("Temporal.PlainMonthDay.prototype.toLocaleString called on incompatible receiver");
        }

        JSValue intlValue = childContext.getGlobalObject().get(PropertyKey.fromString("Intl"));
        if (!(intlValue instanceof JSObject intlObject)) {
            return childContext.throwTypeError("Intl is not available");
        }
        JSValue dateTimeFormatValue = intlObject.get(PropertyKey.fromString("DateTimeFormat"));
        if (!(dateTimeFormatValue instanceof JSObject dateTimeFormatObject)) {
            return childContext.throwTypeError("Intl.DateTimeFormat is not available");
        }
        JSValue dateTimeFormatPrototypeValue = dateTimeFormatObject.get(PropertyKey.PROTOTYPE);
        if (!(dateTimeFormatPrototypeValue instanceof JSObject dateTimeFormatPrototype)) {
            return childContext.throwTypeError("Intl.DateTimeFormat.prototype is not available");
        }

        JSValue locales = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(
                childContext,
                dateTimeFormatPrototype,
                new JSValue[]{locales, options});
        if (childContext.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(childContext, dateTimeFormat, new JSValue[]{thisArg});
    }
}
