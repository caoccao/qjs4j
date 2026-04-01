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
import com.caoccao.qjs4j.core.temporal.IsoDate;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

import java.util.Locale;

/**
 * Implementation of Temporal.PlainMonthDay prototype methods.
 */
public final class TemporalPlainMonthDayPrototype {
    private static final String TYPE_NAME = "Temporal.PlainMonthDay";

    private TemporalPlainMonthDayPrototype() {
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = checkReceiver(context, thisArg, "calendarId");
        if (plainMonthDay == null) return JSUndefined.INSTANCE;
        return new JSString(plainMonthDay.getCalendarId());
    }

    private static JSTemporalPlainMonthDay checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalPlainMonthDay plainMonthDay)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return plainMonthDay;
    }

    public static JSValue day(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = checkReceiver(context, thisArg, "day");
        if (plainMonthDay == null) return JSUndefined.INSTANCE;
        return JSNumber.of(plainMonthDay.getIsoDate().day());
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = checkReceiver(context, thisArg, "equals");
        if (plainMonthDay == null) return JSUndefined.INSTANCE;
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainMonthDay other = TemporalPlainMonthDayConstructor.toTemporalMonthDayObject(context, otherArg);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        boolean equal = plainMonthDay.getIsoDate().month() == other.getIsoDate().month()
                && plainMonthDay.getIsoDate().day() == other.getIsoDate().day()
                && plainMonthDay.getIsoDate().year() == other.getIsoDate().year()
                && plainMonthDay.getCalendarId().equals(other.getCalendarId());
        return equal ? JSBoolean.TRUE : JSBoolean.FALSE;
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = checkReceiver(context, thisArg, "monthCode");
        if (plainMonthDay == null) return JSUndefined.INSTANCE;
        return new JSString(TemporalUtils.monthCode(plainMonthDay.getIsoDate().month()));
    }

    private static ParsedMonthCode parseMonthCodeSyntaxForWith(JSContext context, String monthCode) {
        if (monthCode == null || monthCode.length() < 3 || monthCode.length() > 4) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        if (monthCode.charAt(0) != 'M') {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        if (!Character.isDigit(monthCode.charAt(1)) || !Character.isDigit(monthCode.charAt(2))) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        boolean leapMonth = false;
        if (monthCode.length() == 4) {
            if (monthCode.charAt(3) != 'L') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            leapMonth = true;
        }
        int month = Integer.parseInt(monthCode.substring(1, 3));
        return new ParsedMonthCode(month, leapMonth);
    }

    public static JSValue referenceISOYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = checkReceiver(context, thisArg, "referenceISOYear");
        if (plainMonthDay == null) return JSUndefined.INSTANCE;
        return JSNumber.of(plainMonthDay.getIsoDate().year());
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = checkReceiver(context, thisArg, "toJSON");
        if (plainMonthDay == null) return JSUndefined.INSTANCE;
        IsoDate isoDate = plainMonthDay.getIsoDate();
        return new JSString(String.format(Locale.ROOT, "%02d-%02d", isoDate.month(), isoDate.day()));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = checkReceiver(context, thisArg, "toLocaleString");
        if (plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue locales = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(
                context,
                null,
                new JSValue[]{locales, options});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainMonthDay});
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = checkReceiver(context, thisArg, "toPlainDate");
        if (plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        int year = TemporalUtils.getIntegerField(context, fields, "year", Integer.MIN_VALUE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (year == Integer.MIN_VALUE) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = plainMonthDay.getIsoDate();
        int month = isoDate.month();
        int dayOfMonth = isoDate.day();
        if (!IsoDate.isValidIsoDate(year, month, dayOfMonth)) {
            dayOfMonth = Math.min(dayOfMonth, IsoDate.daysInMonth(year, month));
            if (!IsoDate.isValidIsoDate(year, month, dayOfMonth)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }
        return TemporalPlainDateConstructor.createPlainDate(context,
                new IsoDate(year, month, dayOfMonth), plainMonthDay.getCalendarId());
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = checkReceiver(context, thisArg, "toString");
        if (plainMonthDay == null) return JSUndefined.INSTANCE;
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = plainMonthDay.getIsoDate();
        boolean includeReferenceYear = !"never".equals(calendarNameOption)
                && (!"auto".equals(calendarNameOption) || !"iso8601".equals(plainMonthDay.getCalendarId()));
        String result;
        if (includeReferenceYear) {
            result = TemporalUtils.formatIsoDate(isoDate.year(), isoDate.month(), isoDate.day());
        } else {
            result = String.format(Locale.ROOT, "%02d-%02d", isoDate.month(), isoDate.day());
        }
        result = TemporalUtils.maybeAppendCalendar(result, plainMonthDay.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.PlainMonthDay.prototype.valueOf; use Temporal.PlainMonthDay.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainMonthDay plainMonthDay = checkReceiver(context, thisArg, "with");
        if (plainMonthDay == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }
        if (fields instanceof JSTemporalPlainDate
                || fields instanceof JSTemporalPlainDateTime
                || fields instanceof JSTemporalPlainMonthDay
                || fields instanceof JSTemporalPlainTime
                || fields instanceof JSTemporalPlainYearMonth
                || fields instanceof JSTemporalZonedDateTime) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        JSValue calendarLike = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(calendarLike instanceof JSUndefined) && calendarLike != null) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        JSValue timeZoneLike = fields.get(PropertyKey.fromString("timeZone"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(timeZoneLike instanceof JSUndefined) && timeZoneLike != null) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        IsoDate originalDate = plainMonthDay.getIsoDate();

        JSValue dayValue = fields.get(PropertyKey.fromString("day"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasDay = !(dayValue instanceof JSUndefined) && dayValue != null;
        int dayOfMonth = originalDate.day();
        if (hasDay) {
            dayOfMonth = TemporalUtils.toIntegerThrowOnInfinity(context, dayValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthValue = fields.get(PropertyKey.fromString("month"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonth = !(monthValue instanceof JSUndefined) && monthValue != null;
        int month = originalDate.month();
        if (hasMonth) {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthCodeValue = fields.get(PropertyKey.fromString("monthCode"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthCode = !(monthCodeValue instanceof JSUndefined) && monthCodeValue != null;
        String monthCode = null;
        if (hasMonthCode) {
            monthCode = JSTypeConversions.toString(context, monthCodeValue).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue yearValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYear = !(yearValue instanceof JSUndefined) && yearValue != null;
        int year = originalDate.year();
        if (hasYear) {
            year = TemporalUtils.toIntegerThrowOnInfinity(context, yearValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        if (!hasDay && !hasMonth && !hasMonthCode && !hasYear) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        if (dayOfMonth < 1 || month < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        ParsedMonthCode parsedMonthCode = null;
        if (hasMonthCode) {
            parsedMonthCode = parseMonthCodeSyntaxForWith(context, monthCode);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (parsedMonthCode != null) {
            if (parsedMonthCode.month() < 1 || parsedMonthCode.month() > 12) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (parsedMonthCode.leapMonth()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (hasMonth && month != parsedMonthCode.month()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            month = parsedMonthCode.month();
        }

        if ("reject".equals(overflow)) {
            if (!IsoDate.isValidIsoDate(year, month, dayOfMonth)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            return TemporalPlainMonthDayConstructor.createPlainMonthDay(
                    context,
                    new IsoDate(1972, month, dayOfMonth),
                    plainMonthDay.getCalendarId());
        }

        int constrainedMonth = Math.max(1, Math.min(12, month));
        int maximumDay = IsoDate.daysInMonth(year, constrainedMonth);
        int constrainedDay = Math.max(1, Math.min(maximumDay, dayOfMonth));
        return TemporalPlainMonthDayConstructor.createPlainMonthDay(context,
                new IsoDate(1972, constrainedMonth, constrainedDay), plainMonthDay.getCalendarId());
    }

    private record ParsedMonthCode(int month, boolean leapMonth) {
    }
}
