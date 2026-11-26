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
import com.caoccao.qjs4j.core.temporal.*;

/**
 * Reads Temporal fields in the order requested by the caller. After an abrupt completion, later getters are skipped.
 */
final class TemporalPropertyBag {
    private final JSContext context;
    private final JSObject fields;
    private boolean hasTimeField;

    TemporalPropertyBag(JSContext context, JSObject fields) {
        this.context = context;
        this.fields = fields;
    }

    TemporalCalendarId calendar() {
        JSValue calendar = value("calendar");
        return context.hasPendingException() ? null : TemporalCalendarId.createFromCalendarValue(context, calendar);
    }

    boolean hasDefined(String name) {
        JSValue field = value(name);
        return field != null && !(field instanceof JSUndefined);
    }

    boolean hasTimeField() {
        return hasTimeField;
    }

    Integer integer(String name) {
        return context.hasPendingException() ? null : TemporalUtils.getOptionalIntegerField(context, fields, name);
    }

    Integer integral(String name) {
        if (context.hasPendingException()) {
            return null;
        }
        JSValue value = fields.get(PropertyKey.fromString(name));
        if (context.hasPendingException() || value == null || value instanceof JSUndefined) {
            return null;
        }
        long result = TemporalUtils.toLongIfIntegral(context, value);
        return context.hasPendingException() ? null : (int) result;
    }

    int integralOrZero(String name) {
        Integer value = integral(name);
        return value == null ? 0 : value;
    }

    IsoMonth monthCode() {
        if (context.hasPendingException()) {
            return null;
        }
        JSValue value = fields.get(PropertyKey.fromString("monthCode"));
        return context.hasPendingException() ? null : IsoMonth.fromField(context, value);
    }

    String offset() {
        JSValue value = value("offset");
        if (context.hasPendingException() || value == null || value instanceof JSUndefined) {
            return null;
        }
        if (value instanceof JSString string) {
            return string.value();
        }
        if (value instanceof JSObject) {
            return JSTypeConversions.toString(context, value).value();
        }
        context.throwTypeError("Temporal error: Offset must be string.");
        return null;
    }

    Integer positiveDateField(String name) {
        Integer value = integer(name);
        if (value != null && value < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return value;
    }

    TemporalPartialDate readYearAndEra(TemporalCalendarId calendar, Integer day, Integer month, String monthCode) {
        Integer year = integer("year");
        String era = calendar.hasEra() ? string("era") : null;
        Integer eraYear = calendar.hasEra() ? integer("eraYear") : null;
        return new TemporalPartialDate(day, month, monthCode, year, era, eraYear);
    }

    Integer resolvedYear(TemporalCalendarId calendar, String missingEraMessage) {
        Integer year = integer("year");
        return context.hasPendingException()
                ? null
                : TemporalUtils.resolveEraYear(context, fields, calendar, year, missingEraMessage);
    }

    String string(String name) {
        return context.hasPendingException() ? null : TemporalUtils.getStringOption(context, fields, name, null);
    }

    int time(String name, int fallback) {
        Integer value = integer(name);
        if (value == null) {
            return fallback;
        }
        hasTimeField = true;
        return value;
    }

    JSValue value(String name) {
        return context.hasPendingException() ? null : fields.get(PropertyKey.fromString(name));
    }
}
