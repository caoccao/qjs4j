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

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Implementation of Date constructor static methods.
 * Based on ES2020 Date specification.
 */
public final class DateConstructor {

    /**
     * Date.UTC(year, month, date, hours, minutes, seconds, ms)
     * ES2020 20.3.3.4
     * Accepts the same parameters as the Date constructor, but treats them as UTC.
     */
    public static JSValue UTC(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }

        // Get year (required)
        int year = (int) JSTypeConversions.toNumber(context, args[0]).value();

        // Get month (required)
        int month = args.length > 1 ? (int) JSTypeConversions.toNumber(context, args[1]).value() : 0;

        // Get date (default 1)
        int date = args.length > 2 ? (int) JSTypeConversions.toNumber(context, args[2]).value() : 1;

        // Get hours (default 0)
        int hours = args.length > 3 ? (int) JSTypeConversions.toNumber(context, args[3]).value() : 0;

        // Get minutes (default 0)
        int minutes = args.length > 4 ? (int) JSTypeConversions.toNumber(context, args[4]).value() : 0;

        // Get seconds (default 0)
        int seconds = args.length > 5 ? (int) JSTypeConversions.toNumber(context, args[5]).value() : 0;

        // Get milliseconds (default 0)
        int ms = args.length > 6 ? (int) JSTypeConversions.toNumber(context, args[6]).value() : 0;

        try {
            // Create UTC ZonedDateTime
            ZonedDateTime zdt = ZonedDateTime.of(
                    year,
                    month + 1, // JavaScript months are 0-based
                    date,
                    hours,
                    minutes,
                    seconds,
                    ms * 1_000_000, // Convert ms to nanoseconds
                    java.time.ZoneId.of("UTC")
            );

            return new JSNumber(zdt.toInstant().toEpochMilli());
        } catch (Exception e) {
            return new JSNumber(Double.NaN);
        }
    }

    /**
     * Date() function (called without new).
     * Returns current date/time as a string.
     * ES2020 20.3.2
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        // When Date is called as a function (not constructor), return current date string
        // Arguments are ignored
        // V8 format matches Date.prototype.toString()
        JSDate date = new JSDate(System.currentTimeMillis());
        ZonedDateTime zdt = date.getLocalZonedDateTime();
        return new JSString(zdt.format(JSDate.TO_STRING_FORMATTER));
    }

    /**
     * Date.now()
     * ES2020 20.3.3.1
     * Returns the number of milliseconds elapsed since January 1, 1970 00:00:00 UTC.
     */
    public static JSValue now(JSContext context, JSValue thisArg, JSValue[] args) {
        return new JSNumber(System.currentTimeMillis());
    }

    /**
     * Date.parse(string)
     * ES2020 20.3.3.2
     * Parses a string representation of a date and returns the number of milliseconds since January 1, 1970, 00:00:00 UTC.
     */
    public static JSValue parse(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }

        JSString dateString = JSTypeConversions.toString(context, args[0]);
        String str = dateString.value();

        try {
            // Try ISO 8601 format first
            Instant instant = Instant.parse(str);
            return new JSNumber(instant.toEpochMilli());
        } catch (Exception e1) {
            try {
                // Try RFC 1123 format
                ZonedDateTime zdt = ZonedDateTime.parse(str, DateTimeFormatter.RFC_1123_DATE_TIME);
                return new JSNumber(zdt.toInstant().toEpochMilli());
            } catch (Exception e2) {
                // Return NaN for unparseable dates
                return new JSNumber(Double.NaN);
            }
        }
    }
}
