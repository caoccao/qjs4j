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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Implementation of JavaScript Date.prototype methods.
 * Based on ES2020 Date specification.
 */
public final class DatePrototype {

    /**
     * Date.prototype.getDate()
     * ES2020 20.3.4.3
     * Returns the day of the month (1-31) for the specified date according to local time.
     */
    public static JSValue getDate(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getDate called on non-Date");
        }
        ZonedDateTime zdt = date.getLocalZonedDateTime();
        return new JSNumber(zdt.getDayOfMonth());
    }

    /**
     * Date.prototype.getDay()
     * ES2020 20.3.4.4
     * Returns the day of the week (0-6) for the specified date according to local time.
     */
    public static JSValue getDay(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getDay called on non-Date");
        }
        ZonedDateTime zdt = date.getLocalZonedDateTime();
        int dayOfWeek = zdt.getDayOfWeek().getValue();
        return new JSNumber(dayOfWeek % 7); // Convert Monday=1 to Sunday=0
    }

    /**
     * Date.prototype.getFullYear()
     * ES2020 20.3.4.5
     * Returns the year of the specified date according to local time.
     */
    public static JSValue getFullYear(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getFullYear called on non-Date");
        }
        ZonedDateTime zdt = date.getLocalZonedDateTime();
        return new JSNumber(zdt.getYear());
    }

    /**
     * Date.prototype.getHours()
     * ES2020 20.3.4.6
     * Returns the hour (0-23) in the specified date according to local time.
     */
    public static JSValue getHours(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getHours called on non-Date");
        }
        ZonedDateTime zdt = date.getLocalZonedDateTime();
        return new JSNumber(zdt.getHour());
    }

    /**
     * Date.prototype.getMilliseconds()
     * ES2020 20.3.4.11
     * Returns the milliseconds (0-999) in the specified date according to local time.
     */
    public static JSValue getMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getMilliseconds called on non-Date");
        }
        ZonedDateTime zdt = date.getLocalZonedDateTime();
        return new JSNumber(zdt.getNano() / 1_000_000);
    }

    /**
     * Date.prototype.getMinutes()
     * ES2020 20.3.4.7
     * Returns the minutes (0-59) in the specified date according to local time.
     */
    public static JSValue getMinutes(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getMinutes called on non-Date");
        }
        ZonedDateTime zdt = date.getLocalZonedDateTime();
        return new JSNumber(zdt.getMinute());
    }

    /**
     * Date.prototype.getMonth()
     * ES2020 20.3.4.8
     * Returns the month (0-11) of the specified date according to local time.
     */
    public static JSValue getMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getMonth called on non-Date");
        }
        ZonedDateTime zdt = date.getLocalZonedDateTime();
        return new JSNumber(zdt.getMonthValue() - 1); // JavaScript months are 0-based
    }

    /**
     * Date.prototype.getSeconds()
     * ES2020 20.3.4.9
     * Returns the seconds (0-59) in the specified date according to local time.
     */
    public static JSValue getSeconds(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getSeconds called on non-Date");
        }
        ZonedDateTime zdt = date.getLocalZonedDateTime();
        return new JSNumber(zdt.getSecond());
    }

    /**
     * Date.prototype.getTime()
     * ES2020 20.3.4.10
     * Returns the numeric value corresponding to the time for the specified date.
     */
    public static JSValue getTime(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getTime called on non-Date");
        }
        return new JSNumber(date.getTimeValue());
    }

    /**
     * Date.prototype.getUTCDate()
     * ES2020 20.3.4.13
     */
    public static JSValue getUTCDate(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getUTCDate called on non-Date");
        }
        ZonedDateTime zdt = date.getZonedDateTime();
        return new JSNumber(zdt.getDayOfMonth());
    }

    /**
     * Date.prototype.getUTCFullYear()
     * ES2020 20.3.4.15
     */
    public static JSValue getUTCFullYear(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getUTCFullYear called on non-Date");
        }
        ZonedDateTime zdt = date.getZonedDateTime();
        return new JSNumber(zdt.getYear());
    }

    /**
     * Date.prototype.getUTCHours()
     * ES2020 20.3.4.16
     */
    public static JSValue getUTCHours(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getUTCHours called on non-Date");
        }
        ZonedDateTime zdt = date.getZonedDateTime();
        return new JSNumber(zdt.getHour());
    }

    /**
     * Date.prototype.getUTCMonth()
     * ES2020 20.3.4.18
     */
    public static JSValue getUTCMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.getUTCMonth called on non-Date");
        }
        ZonedDateTime zdt = date.getZonedDateTime();
        return new JSNumber(zdt.getMonthValue() - 1);
    }

    /**
     * Date.prototype.toISOString()
     * ES2020 20.3.4.36
     * Returns a string in simplified extended ISO format.
     */
    public static JSValue toISOString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.toISOString called on non-Date");
        }
        ZonedDateTime zdt = date.getZonedDateTime();
        String isoString = zdt.format(DateTimeFormatter.ISO_INSTANT);
        return new JSString(isoString);
    }

    /**
     * Date.prototype.toJSON()
     * ES2020 20.3.4.37
     */
    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        return toISOString(context, thisArg, args);
    }

    /**
     * Date.prototype.toString()
     * ES2020 20.3.4.41
     */
    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSDate date)) {
            return context.throwTypeError("Date.prototype.toString called on non-Date");
        }
        ZonedDateTime zdt = date.getLocalZonedDateTime();
        return new JSString(JSDate.formatToString(context, zdt));
    }

    /**
     * Date.prototype.valueOf()
     * ES2020 20.3.4.45
     */
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return getTime(context, thisArg, args);
    }
}
