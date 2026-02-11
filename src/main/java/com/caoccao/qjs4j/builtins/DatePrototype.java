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
import java.util.Locale;

/**
 * Implementation of JavaScript Date.prototype methods.
 */
public final class DatePrototype {
    private static final String DAY_NAMES = "SunMonTueWedThuFriSat";
    private static final int FIELD_DATE = JSDate.FIELD_DATE;
    private static final int FIELD_DAY = JSDate.FIELD_DAY;
    private static final int FIELD_HOURS = JSDate.FIELD_HOURS;
    private static final int FIELD_MILLISECONDS = JSDate.FIELD_MILLISECONDS;
    private static final int FIELD_MINUTES = JSDate.FIELD_MINUTES;
    private static final int FIELD_MONTH = JSDate.FIELD_MONTH;
    private static final int FIELD_SECONDS = JSDate.FIELD_SECONDS;
    private static final int FIELD_TIMEZONE_OFFSET = JSDate.FIELD_TIMEZONE_OFFSET;
    private static final int FIELD_YEAR = JSDate.FIELD_YEAR;
    private static final int FORMAT_ISO = 2;
    private static final int FORMAT_LOCALE = 3;
    private static final int FORMAT_TO_STRING = 1;
    private static final int FORMAT_UTC = 0;
    private static final String MONTH_NAMES = "JanFebMarAprMayJunJulAugSepOctNovDec";
    private static final int PART_ALL = 3;
    private static final int PART_DATE = 1;
    private static final int PART_TIME = 2;

    private static JSObject boxPrimitive(JSValue value) {
        JSObject object = new JSObject();
        object.setPrimitiveValue(value);
        return object;
    }

    private static String dayName(int day) {
        int index = Math.max(0, Math.min(6, day));
        int offset = index * 3;
        return DAY_NAMES.substring(offset, offset + 3);
    }

    public static JSValue getDate(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_DATE, true, false);
    }

    private static JSValue getDateField(
            JSContext context,
            JSValue thisArg,
            int fieldIndex,
            boolean isLocal,
            boolean getYearLegacy) {
        JSDate date = requireDate(context, thisArg, "get");
        if (date == null) {
            return context.getPendingException();
        }

        double[] fields = new double[9];
        int result = JSDate.getDateFields(date.getTimeValue(), fields, isLocal, false);
        if (result == 0) {
            return new JSNumber(Double.NaN);
        }

        double value = fields[fieldIndex];
        if (getYearLegacy) {
            value -= 1900;
        }
        return new JSNumber(value);
    }

    private static JSValue getDateString(JSContext context, JSValue thisArg, int format, int part) {
        JSDate date = requireDate(context, thisArg, "toString");
        if (date == null) {
            return context.getPendingException();
        }

        double[] fields = new double[9];
        int result = JSDate.getDateFields(date.getTimeValue(), fields, (format & 1) == 1, false);
        if (result == 0) {
            if (format == FORMAT_ISO) {
                return context.throwRangeError("Date value is NaN");
            }
            return new JSString("Invalid Date");
        }

        int year = (int) fields[FIELD_YEAR];
        int month = (int) fields[FIELD_MONTH];
        int day = (int) fields[FIELD_DATE];
        int hour = (int) fields[FIELD_HOURS];
        int minute = (int) fields[FIELD_MINUTES];
        int second = (int) fields[FIELD_SECONDS];
        int millisecond = (int) fields[FIELD_MILLISECONDS];
        int weekDay = (int) fields[FIELD_DAY];
        int timezoneOffset = (int) fields[FIELD_TIMEZONE_OFFSET];

        StringBuilder builder = new StringBuilder(64);

        if ((part & PART_DATE) != 0) {
            switch (format) {
                case FORMAT_UTC -> builder.append(String.format(
                        Locale.ENGLISH,
                        "%s, %02d %s %0" + (4 + (year < 0 ? 1 : 0)) + "d ",
                        dayName(weekDay),
                        day,
                        monthName(month),
                        year));
                case FORMAT_TO_STRING -> {
                    builder.append(String.format(
                            Locale.ENGLISH,
                            "%s %s %02d %0" + (4 + (year < 0 ? 1 : 0)) + "d",
                            dayName(weekDay),
                            monthName(month),
                            day,
                            year));
                    if (part == PART_ALL) {
                        builder.append(' ');
                    }
                }
                case FORMAT_ISO -> {
                    if (year >= 0 && year <= 9999) {
                        builder.append(String.format(Locale.ENGLISH, "%04d", year));
                    } else {
                        builder.append(String.format(Locale.ENGLISH, "%+07d", year));
                    }
                    builder.append(String.format(Locale.ENGLISH, "-%02d-%02dT", month + 1, day));
                }
                case FORMAT_LOCALE -> {
                    builder.append(String.format(
                            Locale.ENGLISH,
                            "%02d/%02d/%0" + (4 + (year < 0 ? 1 : 0)) + "d",
                            month + 1,
                            day,
                            year));
                    if (part == PART_ALL) {
                        builder.append(", ");
                    }
                }
                default -> {
                }
            }
        }

        if ((part & PART_TIME) != 0) {
            switch (format) {
                case FORMAT_UTC ->
                        builder.append(String.format(Locale.ENGLISH, "%02d:%02d:%02d GMT", hour, minute, second));
                case FORMAT_TO_STRING -> {
                    builder.append(String.format(Locale.ENGLISH, "%02d:%02d:%02d GMT", hour, minute, second));
                    int tz = timezoneOffset;
                    if (tz < 0) {
                        builder.append('-');
                        tz = -tz;
                    } else {
                        builder.append('+');
                    }
                    builder.append(String.format(Locale.ENGLISH, "%02d%02d", tz / 60, tz % 60));
                }
                case FORMAT_ISO -> builder.append(String.format(
                        Locale.ENGLISH,
                        "%02d:%02d:%02d.%03dZ",
                        hour,
                        minute,
                        second,
                        millisecond));
                case FORMAT_LOCALE -> builder.append(String.format(
                        Locale.ENGLISH,
                        "%02d:%02d:%02d %cM",
                        (hour + 11) % 12 + 1,
                        minute,
                        second,
                        hour < 12 ? 'A' : 'P'));
                default -> {
                }
            }
        }

        return new JSString(builder.toString());
    }

    public static JSValue getDay(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_DAY, true, false);
    }

    public static JSValue getFullYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_YEAR, true, false);
    }

    public static JSValue getHours(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_HOURS, true, false);
    }

    public static JSValue getMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_MILLISECONDS, true, false);
    }

    public static JSValue getMinutes(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_MINUTES, true, false);
    }

    public static JSValue getMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_MONTH, true, false);
    }

    public static JSValue getSeconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_SECONDS, true, false);
    }

    public static JSValue getTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDate date = requireDate(context, thisArg, "getTime");
        if (date == null) {
            return context.getPendingException();
        }
        return new JSNumber(date.getTimeValue());
    }

    public static JSValue getTimezoneOffset(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDate date = requireDate(context, thisArg, "getTimezoneOffset");
        if (date == null) {
            return context.getPendingException();
        }
        double value = date.getTimeValue();
        if (Double.isNaN(value)) {
            return new JSNumber(Double.NaN);
        }
        return new JSNumber(JSDate.getTimezoneOffset((long) JSDate.trunc(value)));
    }

    public static JSValue getUTCDate(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_DATE, false, false);
    }

    public static JSValue getUTCDay(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_DAY, false, false);
    }

    public static JSValue getUTCFullYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_YEAR, false, false);
    }

    public static JSValue getUTCHours(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_HOURS, false, false);
    }

    public static JSValue getUTCMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_MILLISECONDS, false, false);
    }

    public static JSValue getUTCMinutes(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_MINUTES, false, false);
    }

    public static JSValue getUTCMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_MONTH, false, false);
    }

    public static JSValue getUTCSeconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_SECONDS, false, false);
    }

    public static JSValue getYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateField(context, thisArg, FIELD_YEAR, true, true);
    }

    private static String monthName(int month) {
        int index = Math.max(0, Math.min(11, month));
        int offset = index * 3;
        return MONTH_NAMES.substring(offset, offset + 3);
    }

    private static JSValue ordinaryToPrimitive(JSContext context, JSObject object, boolean stringHint) {
        String[] methodNames = stringHint
                ? new String[]{"toString", "valueOf"}
                : new String[]{"valueOf", "toString"};
        for (String methodName : methodNames) {
            JSValue method = object.get(PropertyKey.fromString(methodName), context);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (method instanceof JSFunction function) {
                JSValue result = function.call(context, object, new JSValue[0]);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (JSTypeConversions.isPrimitive(result)) {
                    return result;
                }
            }
        }
        return context.throwTypeError("toPrimitive");
    }

    private static JSDate requireDate(JSContext context, JSValue thisArg, String method) {
        if (thisArg instanceof JSDate date) {
            return date;
        }
        context.throwTypeError("Date.prototype." + method + " called on non-Date");
        return null;
    }

    public static JSValue setDate(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_DATE, FIELD_HOURS, true);
    }

    private static JSValue setDateField(
            JSContext context,
            JSValue thisArg,
            JSValue[] args,
            int firstField,
            int endField,
            boolean isLocal) {
        JSDate date = requireDate(context, thisArg, "set");
        if (date == null) {
            return context.getPendingException();
        }
        return setDateField(context, date, args, firstField, endField, isLocal);
    }

    private static JSValue setDateField(
            JSContext context,
            JSDate date,
            JSValue[] args,
            int firstField,
            int endField,
            boolean isLocal) {
        double[] fields = new double[9];
        int result = JSDate.getDateFields(date.getTimeValue(), fields, isLocal, firstField == FIELD_YEAR);
        int initialResult = result;

        int n = Math.min(args.length, endField - firstField);
        for (int i = 0; i < n; i++) {
            JSNumber number = toNumberOrThrow(context, args[i]);
            if (number == null) {
                return context.getPendingException();
            }
            double value = number.value();
            if (!Double.isFinite(value)) {
                result = 0;
            }
            fields[firstField + i] = JSDate.trunc(value);
        }

        if (initialResult == 0) {
            return new JSNumber(Double.NaN);
        }

        double newTime = Double.NaN;
        if (result != 0 && args.length > 0) {
            newTime = JSDate.setDateFields(fields, isLocal);
        }
        date.setTimeValue(newTime);
        return new JSNumber(newTime);
    }

    public static JSValue setFullYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_YEAR, FIELD_HOURS, true);
    }

    public static JSValue setHours(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_HOURS, FIELD_TIMEZONE_OFFSET, true);
    }

    public static JSValue setMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_MILLISECONDS, FIELD_TIMEZONE_OFFSET, true);
    }

    public static JSValue setMinutes(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_MINUTES, FIELD_TIMEZONE_OFFSET, true);
    }

    public static JSValue setMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_MONTH, FIELD_HOURS, true);
    }

    public static JSValue setSeconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_SECONDS, FIELD_TIMEZONE_OFFSET, true);
    }

    public static JSValue setTime(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDate date = requireDate(context, thisArg, "setTime");
        if (date == null) {
            return context.getPendingException();
        }
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSNumber number = toNumberOrThrow(context, arg);
        if (number == null) {
            return context.getPendingException();
        }
        double value = number.value();
        double clipped = JSDate.timeClip(value);
        date.setTimeValue(clipped);
        return new JSNumber(clipped);
    }

    public static JSValue setUTCDate(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_DATE, FIELD_HOURS, false);
    }

    public static JSValue setUTCFullYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_YEAR, FIELD_HOURS, false);
    }

    public static JSValue setUTCHours(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_HOURS, FIELD_TIMEZONE_OFFSET, false);
    }

    public static JSValue setUTCMilliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_MILLISECONDS, FIELD_TIMEZONE_OFFSET, false);
    }

    public static JSValue setUTCMinutes(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_MINUTES, FIELD_TIMEZONE_OFFSET, false);
    }

    public static JSValue setUTCMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_MONTH, FIELD_HOURS, false);
    }

    public static JSValue setUTCSeconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return setDateField(context, thisArg, args, FIELD_SECONDS, FIELD_TIMEZONE_OFFSET, false);
    }

    public static JSValue setYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDate date = requireDate(context, thisArg, "setYear");
        if (date == null) {
            return context.getPendingException();
        }
        JSValue arg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSNumber number = toNumberOrThrow(context, arg);
        if (number == null) {
            return context.getPendingException();
        }
        double year = +number.value();
        if (Double.isFinite(year)) {
            year = JSDate.trunc(year);
            if (year >= 0 && year < 100) {
                year += 1900;
            }
        }
        return setDateField(context, date, new JSValue[]{new JSNumber(year)}, FIELD_YEAR, FIELD_MONTH, true);
    }

    public static JSValue symbolToPrimitive(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject obj)) {
            return context.throwTypeError("not an object");
        }

        String hint = args.length > 0 ? JSTypeConversions.toString(context, args[0]).value() : "default";
        return switch (hint) {
            case "number" -> ordinaryToPrimitive(context, obj, false);
            case "string", "default" -> ordinaryToPrimitive(context, obj, true);
            default -> context.throwTypeError("invalid hint");
        };
    }

    public static JSValue toDateString(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateString(context, thisArg, FORMAT_TO_STRING, PART_DATE);
    }

    public static JSValue toGMTString(JSContext context, JSValue thisArg, JSValue[] args) {
        return toUTCString(context, thisArg, args);
    }

    public static JSValue toISOString(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateString(context, thisArg, FORMAT_ISO, PART_ALL);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg instanceof JSUndefined || thisArg instanceof JSNull) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        if (thisArg instanceof JSDate date) {
            if (!Double.isFinite(date.getTimeValue())) {
                return JSNull.INSTANCE;
            }
            return toISOString(context, date, new JSValue[0]);
        }
        JSObject obj = thisArg instanceof JSObject jsObject ? jsObject : boxPrimitive(thisArg);

        JSValue primitive = JSTypeConversions.toPrimitive(context, obj, JSTypeConversions.PreferredType.NUMBER);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        if (primitive instanceof JSNumber number && !Double.isFinite(number.value())) {
            return JSNull.INSTANCE;
        }

        JSValue method = obj.get(PropertyKey.fromString("toISOString"), context);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(method instanceof JSFunction function)) {
            return context.throwTypeError("object needs toISOString method");
        }
        return function.call(context, obj, new JSValue[0]);
    }

    public static JSValue toLocaleDateString(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateString(context, thisArg, FORMAT_LOCALE, PART_DATE);
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateString(context, thisArg, FORMAT_LOCALE, PART_ALL);
    }

    public static JSValue toLocaleTimeString(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateString(context, thisArg, FORMAT_LOCALE, PART_TIME);
    }

    private static JSNumber toNumberOrThrow(JSContext context, JSValue value) {
        if (value.isSymbol() || value.isSymbolObject()) {
            context.throwTypeError("cannot convert symbol to number");
            return null;
        }
        if (value.isBigInt() || value.isBigIntObject()) {
            context.throwTypeError("cannot convert bigint to number");
            return null;
        }
        JSNumber number = JSTypeConversions.toNumber(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        return number;
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSDate date = requireDate(context, thisArg, "toString");
        if (date == null) {
            return context.getPendingException();
        }
        ZonedDateTime zonedDateTime = date.getLocalZonedDateTime();
        if (zonedDateTime == null) {
            return new JSString("Invalid Date");
        }
        return new JSString(JSDate.formatToString(context, zonedDateTime));
    }

    public static JSValue toTimeString(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateString(context, thisArg, FORMAT_TO_STRING, PART_TIME);
    }

    public static JSValue toUTCString(JSContext context, JSValue thisArg, JSValue[] args) {
        return getDateString(context, thisArg, FORMAT_UTC, PART_ALL);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return getTime(context, thisArg, args);
    }
}
