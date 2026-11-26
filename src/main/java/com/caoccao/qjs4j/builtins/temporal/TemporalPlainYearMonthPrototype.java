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
 * Implementation of Temporal.PlainYearMonth prototype methods.
 */
public final class TemporalPlainYearMonthPrototype {
    private static final TemporalReceiver<JSTemporalPlainYearMonth> RECEIVER = new TemporalReceiver<>(
            JSTemporalPlainYearMonth.class, "Temporal.PlainYearMonth");

    private TemporalPlainYearMonthPrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, 1);
    }

    private static IsoDate addDateDurationToPlainYearMonth(JSContext context, IsoDate baseDate, long yearsToAdd,
            long monthsToAdd, String overflow) {
        long monthIndex = (long) baseDate.month() - 1L + monthsToAdd;
        long yearDelta = Math.floorDiv(monthIndex, 12L);
        int normalizedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        long normalizedYearAsLong = (long) baseDate.year() + yearsToAdd + yearDelta;
        if (normalizedYearAsLong < Integer.MIN_VALUE || normalizedYearAsLong > Integer.MAX_VALUE) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        int normalizedYear = (int) normalizedYearAsLong;

        if (!TemporalPlainYearMonthConstructor.isValidIsoYearMonth(normalizedYear, normalizedMonth)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        int normalizedDay = baseDate.day();
        int daysInNormalizedMonth = IsoDate.daysInMonth(normalizedYear, normalizedMonth);
        if (normalizedDay > daysInNormalizedMonth) {
            if ("reject".equals(overflow)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            normalizedDay = daysInNormalizedMonth;
        }

        IsoDate isoDate = new IsoDate(normalizedYear, normalizedMonth, normalizedDay);
        if (!isoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        return isoDate;
    }

    private static JSValue addOrSubtract(JSContext context, JSValue thisArg, JSValue[] args, int sign) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, sign > 0 ? "add" : "subtract");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalDuration durationRecord = TemporalDurationConstructor.toDurationRecord(context,
                JSValue.argumentOrUndefined(args, 0));
        if (durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        if (sign < 0) {
            durationRecord = durationRecord.negated();
        }

        String overflow = TemporalUtils.getOverflowOption(context, JSValue.argumentOrUndefined(args, 1));
        if (context.hasPendingException() || overflow == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDate isoDate = plainYearMonth.getIsoDate();
        if (!isoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        if (durationRecord.weeks() != 0 || durationRecord.days() != 0 || durationRecord.hours() != 0
                || durationRecord.minutes() != 0 || durationRecord.seconds() != 0 || durationRecord.milliseconds() != 0
                || durationRecord.microseconds() != 0 || durationRecord.nanoseconds() != 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = plainYearMonth.getCalendarId();
        IsoDate resultDate;
        if (calendarId == TemporalCalendarId.ISO8601) {
            resultDate = addDateDurationToPlainYearMonth(context, isoDate, durationRecord.years(),
                    durationRecord.months(), overflow);
        } else {
            resultDate = isoDate.addCalendarDate(context, calendarId, durationRecord.years(), durationRecord.months(),
                    0L, 0L, overflow);
        }
        if (context.hasPendingException() || resultDate == null) {
            return JSUndefined.INSTANCE;
        }

        return JSTemporalPlainYearMonth.create(context, resultDate, calendarId);
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "calendarId",
                plainYearMonth -> new JSString(plainYearMonth.getCalendarId().identifier()));
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAYS_IN_MONTH.read(context, thisArg, RECEIVER);
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.DAYS_IN_YEAR.read(context, thisArg, RECEIVER);
    }

    private static JSValue difference(JSContext context, JSValue thisArg, JSValue[] args, boolean since) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, since ? "since" : "until");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArgument = JSValue.argumentOrUndefined(args, 0);
        JSTemporalPlainYearMonth otherPlainYearMonth = otherArgument instanceof JSTemporalPlainYearMonth yearMonth
                ? yearMonth
                : TemporalPlainYearMonthConstructor.toTemporalYearMonthObject(context, otherArgument);
        if (context.hasPendingException() || otherPlainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return TemporalCalendarDifference.between(context, plainYearMonth, otherPlainYearMonth,
                JSValue.argumentOrUndefined(args, 1), since, TemporalUnit.MONTH);
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "equals");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = JSValue.argumentOrUndefined(args, 0);
        JSTemporalPlainYearMonth other = TemporalPlainYearMonthConstructor.toTemporalYearMonthObject(context, otherArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean equal = plainYearMonth.getIsoDate().year() == other.getIsoDate().year()
                && plainYearMonth.getIsoDate().month() == other.getIsoDate().month()
                && plainYearMonth.getIsoDate().day() == other.getIsoDate().day()
                && plainYearMonth.getCalendarId().equals(other.getCalendarId());
        if (equal) {
            return JSBoolean.TRUE;
        }
        return JSBoolean.FALSE;
    }

    public static JSValue era(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.ERA.read(context, thisArg, RECEIVER);
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.ERA_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.IN_LEAP_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.MONTH.read(context, thisArg, RECEIVER);
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.MONTH_CODE.read(context, thisArg, RECEIVER);
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.MONTHS_IN_YEAR.read(context, thisArg, RECEIVER);
    }

    public static JSValue referenceISODay(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "referenceISODay",
                plainYearMonth -> JSNumber.of(plainYearMonth.getIsoDate().day()));
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        return difference(context, thisArg, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "toJSON");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = plainYearMonth.getIsoDate();
        String formattedDate = new IsoDate(isoDate.year(), isoDate.month(), 1).toString();
        return new JSString(formattedDate.substring(0, formattedDate.lastIndexOf('-')));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "toLocaleString");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue dateTimeFormat = TemporalLocaleFormat.create(context, args, "timeStyle");
        if (dateTimeFormat == null) {
            return JSUndefined.INSTANCE;
        }
        if (!TemporalLocaleFormat.matchesCalendar(context, dateTimeFormat, plainYearMonth.getCalendarId())) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainYearMonth plainYearMonthForFormatting = plainYearMonth;
        if (plainYearMonth.getCalendarId() != TemporalCalendarId.ISO8601) {
            IsoCalendarDate calendarDateFields = plainYearMonth.getIsoDate()
                    .toIsoCalendarDate(plainYearMonth.getCalendarId());
            IsoDate midMonthIsoDate = IsoDate.calendarDateToIsoDate(context, plainYearMonth.getCalendarId(),
                    calendarDateFields.year(), null, calendarDateFields.monthCode(), 15, "constrain");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (midMonthIsoDate != null) {
                plainYearMonthForFormatting = JSTemporalPlainYearMonth.create(context, midMonthIsoDate,
                        plainYearMonth.getCalendarId());
            }
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainYearMonthForFormatting});
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "toPlainDate");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        int dayOfMonth = TemporalUtils.getIntegerField(context, fields, "day", Integer.MIN_VALUE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (dayOfMonth == Integer.MIN_VALUE) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        IsoCalendarDate calendarDateFields = plainYearMonth.getIsoDate()
                .toIsoCalendarDate(plainYearMonth.getCalendarId());
        JSObject mergedFields = context.createJSObject();
        mergedFields.set(PropertyKey.fromString("calendar"), new JSString(plainYearMonth.getCalendarId().identifier()));
        mergedFields.set(PropertyKey.fromString("year"), JSNumber.of(calendarDateFields.year()));
        mergedFields.set(PropertyKey.fromString("monthCode"), new JSString(calendarDateFields.monthCode()));
        mergedFields.set(PropertyKey.fromString("day"), JSNumber.of(dayOfMonth));
        return TemporalPlainDateConstructor.dateFromFields(context, mergedFields, JSUndefined.INSTANCE);
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "toString");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = plainYearMonth.getIsoDate();
        boolean includeReferenceDay;
        if (plainYearMonth.getCalendarId() == TemporalCalendarId.ISO8601) {
            TemporalDisplayCalendar displayCalendar = TemporalDisplayCalendar.fromString(calendarNameOption);
            includeReferenceDay = displayCalendar != null && displayCalendar.requiresAnnotation();
        } else {
            includeReferenceDay = true;
        }
        String result = new IsoDate(isoDate.year(), isoDate.month(), includeReferenceDay ? isoDate.day() : 1)
                .toString();
        if (!includeReferenceDay) {
            result = result.substring(0, result.lastIndexOf('-'));
        }
        result = TemporalUtils.maybeAppendCalendar(result, plainYearMonth.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        return difference(context, thisArg, args, false);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.rejectValueOf(context);
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "with");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        JSObject fields = TemporalUtils.requirePartialTemporalObject(context, args,
                "Temporal error: Argument to with() must contain some date/time fields.");
        if (fields == null) {
            return JSUndefined.INSTANCE;
        }

        PropertyKey eraKey = PropertyKey.fromString("era");
        PropertyKey eraYearKey = PropertyKey.fromString("eraYear");

        Integer month = TemporalUtils.getOptionalIntegerField(context, fields, "month");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonth = month != null;

        String monthCode = TemporalUtils.getStringOption(context, fields, "monthCode", null);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonthCode = monthCode != null;

        Integer year = TemporalUtils.getOptionalIntegerField(context, fields, "year");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYear = year != null;

        TemporalCalendarId calendarId = plainYearMonth.getCalendarId();
        boolean calendarSupportsEras = calendarId.hasEra();
        boolean hasEra = false;
        boolean hasEraYear = false;
        String era = null;
        Integer eraYear = null;
        if (calendarSupportsEras) {
            era = TemporalUtils.getStringOption(context, fields, "era", null);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEra = era != null;

            eraYear = TemporalUtils.getOptionalIntegerField(context, fields, "eraYear");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEraYear = eraYear != null;
        } else if (calendarId != TemporalCalendarId.ISO8601) {
            JSValue eraValue = fields.get(eraKey);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEra = !(eraValue instanceof JSUndefined) && eraValue != null;
            JSValue eraYearValue = fields.get(eraYearKey);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEraYear = !(eraYearValue instanceof JSUndefined) && eraYearValue != null;
        }

        if (!hasMonth && !hasMonthCode && !hasYear && !hasEra && !hasEraYear) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }
        if (!calendarSupportsEras && (hasEra || hasEraYear)) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        if (calendarSupportsEras && hasEra != hasEraYear) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        JSValue optionsValue = JSValue.argumentOrUndefined(args, 1);
        String overflow = null;
        boolean shouldReadOptionsFirst = optionsValue instanceof JSObject;
        if (shouldReadOptionsFirst) {
            overflow = TemporalUtils.getOverflowOption(context, optionsValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (hasMonth && month != null && month < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        IsoMonth monthCodeInfo = null;
        if (hasMonthCode) {
            monthCodeInfo = IsoMonth.parseByMonthCode(context, monthCode, "Temporal error: Month code out of range.");
            if (context.hasPendingException() || monthCodeInfo == null) {
                return JSUndefined.INSTANCE;
            }
            if (calendarId == TemporalCalendarId.ISO8601 && monthCodeInfo.leapMonth()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (hasMonth && month != null && month != monthCodeInfo.month()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }
        if (!shouldReadOptionsFirst) {
            overflow = TemporalUtils.getOverflowOption(context, optionsValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        IsoDate calendarDate = plainYearMonth.toIsoCalendarDate().toIsoDate();
        String calendarMonthCode = plainYearMonth.toIsoCalendarDate().monthCode();
        TemporalEraYear eraFields = plainYearMonth.toTemporalEraYear();

        TemporalPartialDate partialDate = new TemporalPartialDate(null, month,
                monthCodeInfo == null ? null : monthCodeInfo.monthCode(), year, era, eraYear);
        JSObject mergedFieldsObject = partialDate.mergeFields(context, calendarId, calendarDate, calendarMonthCode,
                eraFields, false);
        JSObject normalizedOptionsObject = TemporalUtils.overflowOptions(context, overflow);
        JSValue mergedYearMonthValue = TemporalPlainYearMonthConstructor.yearMonthFromFields(context,
                mergedFieldsObject, normalizedOptionsObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(mergedYearMonthValue instanceof JSTemporalPlainYearMonth mergedYearMonth)) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalPlainYearMonth.create(context, mergedYearMonth.getIsoDate(), calendarId);
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDateProperty.YEAR.read(context, thisArg, RECEIVER);
    }
}
