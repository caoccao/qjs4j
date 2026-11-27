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

import com.caoccao.qjs4j.builtins.temporal.plain.TemporalPartialDate;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.calendar.IsoMonth;
import com.caoccao.qjs4j.core.temporal.calendar.TemporalCalendarId;

/**
 * Reads Temporal fields in the order requested by the caller. After an abrupt completion, later getters are skipped.
 */
public class TemporalPropertyBag {
    protected final JSContext context;
    private final JSObject fields;
    private boolean hasTimeField;

    public TemporalPropertyBag(JSContext context, JSObject fields) {
        this.context = context;
        this.fields = fields;
    }

    public TemporalCalendarId calendar() {
        JSValue calendar = valueByKey(PropertyKey.CALENDAR);
        return context.hasPendingException() ? null : TemporalCalendarId.createFromCalendarValue(context, calendar);
    }

    public boolean hasDefined(String name) {
        return hasDefinedByKey(PropertyKey.fromString(name));
    }

    public boolean hasDefinedByKey(PropertyKey name) {
        JSValue field = valueByKey(name);
        return field != null && !(field instanceof JSUndefined);
    }

    public boolean hasTimeField() {
        return hasTimeField;
    }

    public Integer integer(String name) {
        return integerByKey(PropertyKey.fromString(name));
    }

    public Integer integerByKey(PropertyKey name) {
        return context.hasPendingException() ? null : TemporalUtils.getOptionalIntegerFieldByKey(context, fields, name);
    }

    public Integer integral(String name) {
        return integralByKey(PropertyKey.fromString(name));
    }

    public Integer integralByKey(PropertyKey name) {
        if (context.hasPendingException()) {
            return null;
        }
        JSValue value = fields.get(name);
        if (context.hasPendingException() || JSTypeChecking.isUndefinedOrMissing(value)) {
            return null;
        }
        long result = TemporalUtils.toLongIfIntegral(context, value);
        return context.hasPendingException() ? null : (int) result;
    }

    public int integralOrZero(String name) {
        return integralOrZeroByKey(PropertyKey.fromString(name));
    }

    public int integralOrZeroByKey(PropertyKey name) {
        Integer value = integralByKey(name);
        return value == null ? 0 : value;
    }

    public IsoMonth monthCode() {
        if (context.hasPendingException()) {
            return null;
        }
        JSValue value = fields.get(PropertyKey.MONTH_CODE);
        return context.hasPendingException() ? null : IsoMonth.fromField(context, value);
    }

    public String offset() {
        JSValue value = valueByKey(PropertyKey.OFFSET);
        if (context.hasPendingException() || JSTypeChecking.isUndefinedOrMissing(value)) {
            return null;
        }
        if (value instanceof JSString string) {
            return string.value();
        }
        if (value instanceof JSObject) {
            return JSTypeConversions.toString(context, value).value();
        }
        return context.rejectTypeError("Temporal error: Offset must be string.");
    }

    public Integer positiveDateField(String name) {
        Integer value = integer(name);
        if (value != null && value < 1) {
            return TemporalError.INVALID_ISO_DATE.reject(context);
        }
        return value;
    }

    public TemporalPartialDate readYearAndEra(TemporalCalendarId calendar, Integer day, Integer month,
            String monthCode) {
        Integer year = integerByKey(PropertyKey.YEAR);
        String era = calendar.hasEra() ? stringByKey(PropertyKey.ERA) : null;
        Integer eraYear = calendar.hasEra() ? integerByKey(PropertyKey.ERA_YEAR) : null;
        return new TemporalPartialDate(day, month, monthCode, year, era, eraYear);
    }

    public Integer resolvedYear(TemporalCalendarId calendar, String missingEraMessage) {
        Integer year = integerByKey(PropertyKey.YEAR);
        return context.hasPendingException()
                ? null
                : TemporalUtils.resolveEraYear(context, fields, calendar, year, missingEraMessage);
    }

    public String string(String name) {
        return stringByKey(PropertyKey.fromString(name));
    }

    public String stringByKey(PropertyKey name) {
        return context.hasPendingException() ? null : TemporalUtils.getStringOptionByKey(context, fields, name, null);
    }

    public int time(String name, int fallback) {
        return timeByKey(PropertyKey.fromString(name), fallback);
    }

    public int timeByKey(PropertyKey name, int fallback) {
        Integer value = integerByKey(name);
        if (value == null) {
            return fallback;
        }
        hasTimeField = true;
        return value;
    }

    public JSValue value(String name) {
        return valueByKey(PropertyKey.fromString(name));
    }

    public JSValue valueByKey(PropertyKey name) {
        return context.hasPendingException() ? null : fields.get(name);
    }
}
