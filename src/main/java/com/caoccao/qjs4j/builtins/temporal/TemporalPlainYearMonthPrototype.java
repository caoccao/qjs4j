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

import java.time.LocalDate;

/**
 * Implementation of Temporal.PlainYearMonth prototype methods.
 */
public final class TemporalPlainYearMonthPrototype {
    private static final long MAX_SUPPORTED_EPOCH_DAY = TemporalConstants.MAX_SUPPORTED_EPOCH_DAY;
    private static final long MIN_SUPPORTED_EPOCH_DAY = TemporalConstants.MIN_SUPPORTED_EPOCH_DAY;
    private static final TemporalReceiver<JSTemporalPlainYearMonth> RECEIVER = new TemporalReceiver<>(
            JSTemporalPlainYearMonth.class, "Temporal.PlainYearMonth");
    private static final long TEMPORAL_MAX_ROUNDING_INCREMENT = TemporalConstants.MAX_ROUNDING_INCREMENT;
    private static final String UNIT_AUTO = "auto";
    private static final String UNIT_MONTH = "month";
    private static final String UNIT_YEAR = "year";

    private TemporalPlainYearMonthPrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "add");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainYearMonth, args, 1);
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

    private static JSValue addOrSubtract(JSContext context, JSTemporalPlainYearMonth plainYearMonth, JSValue[] args,
            int sign) {
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must provide a duration.");
            return JSUndefined.INSTANCE;
        }

        JSTemporalDuration temporalDuration = TemporalDurationConstructor.toTemporalDurationObject(context, args[0]);
        if (context.hasPendingException() || temporalDuration == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = temporalDuration.getDuration();
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

    private static IsoDate createDifferenceIsoDate(JSContext context, JSTemporalPlainYearMonth plainYearMonth) {
        IsoDate isoDate = plainYearMonth.getIsoDate();
        IsoDate differenceIsoDate = new IsoDate(isoDate.year(), isoDate.month(), isoDate.day());
        if (!isDifferenceDateWithinSupportedRange(differenceIsoDate)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return differenceIsoDate;
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "daysInMonth",
                plainYearMonth -> JSNumber.of(plainYearMonth.getIsoDate().daysInMonth(plainYearMonth.getCalendarId())));
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "daysInYear",
                plainYearMonth -> JSNumber.of(plainYearMonth.getIsoDate().daysInYear(plainYearMonth.getCalendarId())));
    }

    private static JSValue difference(JSContext context, JSValue thisArg, JSValue[] args, boolean since) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, since ? "since" : "until");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return differenceTemporalPlainYearMonth(context, plainYearMonth, args, since);
    }

    private static JSValue differenceTemporalPlainYearMonth(JSContext context, JSTemporalPlainYearMonth plainYearMonth,
            JSValue[] args, boolean sinceOperation) {
        JSValue otherArgument = JSValue.argumentOrUndefined(args, 0);
        JSTemporalPlainYearMonth otherPlainYearMonth = TemporalPlainYearMonthConstructor
                .toTemporalYearMonthObject(context, otherArgument);
        if (context.hasPendingException() || otherPlainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        if (!plainYearMonth.getCalendarId().equals(otherPlainYearMonth.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArgument = JSValue.argumentOrUndefined(args, 1);
        TemporalDifferenceSettings differenceSettings = TemporalDifferenceSettings.parse(context, sinceOperation,
                optionsArgument, TemporalUnit.YEAR, TemporalUnit.MONTH, TemporalUnit.MONTH, TemporalUnit.YEAR, true,
                false);
        if (context.hasPendingException() || differenceSettings == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDate thisIsoDate = plainYearMonth.getIsoDate();
        IsoDate otherIsoDate = otherPlainYearMonth.getIsoDate();
        if (thisIsoDate.year() == otherIsoDate.year() && thisIsoDate.month() == otherIsoDate.month()
                && thisIsoDate.day() == otherIsoDate.day()) {
            return JSTemporalDuration.create(context, TemporalDuration.ZERO);
        }

        IsoDate thisDifferenceDate = createDifferenceIsoDate(context, plainYearMonth);
        if (context.hasPendingException() || thisDifferenceDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate otherDifferenceDate = createDifferenceIsoDate(context, otherPlainYearMonth);
        if (context.hasPendingException() || otherDifferenceDate == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalCalendarId calendarId = plainYearMonth.getCalendarId();
        TemporalDurationYearMonth durationFields;
        long totalMonthsDifference;
        if (calendarId == TemporalCalendarId.ISO8601) {
            totalMonthsDifference = (long) (otherDifferenceDate.year() - thisDifferenceDate.year()) * 12L
                    + (otherDifferenceDate.month() - thisDifferenceDate.month());
            durationFields = TemporalDurationYearMonth.fromTotalMonths(totalMonthsDifference,
                    differenceSettings.largestUnit());
        } else {
            durationFields = TemporalDurationYearMonth.betweenCalendarDates(context, thisDifferenceDate,
                    otherDifferenceDate, calendarId, differenceSettings.largestUnit());
            if (context.hasPendingException() || durationFields == null) {
                return JSUndefined.INSTANCE;
            }
            TemporalDurationYearMonth monthDifferenceFields = TemporalDurationYearMonth.betweenCalendarDates(context,
                    thisDifferenceDate, otherDifferenceDate, calendarId, TemporalUnit.MONTH);
            if (context.hasPendingException() || monthDifferenceFields == null) {
                return JSUndefined.INSTANCE;
            }
            totalMonthsDifference = monthDifferenceFields.months();
        }
        boolean roundingNoOp = differenceSettings.smallestUnit() == TemporalUnit.MONTH
                && differenceSettings.roundingIncrement() == 1L;
        if (!roundingNoOp) {
            durationFields = roundRelativeYearMonthDuration(context, durationFields, totalMonthsDifference,
                    thisDifferenceDate, differenceSettings);
            if (context.hasPendingException() || durationFields == null) {
                return JSUndefined.INSTANCE;
            }
        }

        TemporalDuration resultDuration = new TemporalDuration(durationFields.years(), durationFields.months(), 0, 0, 0,
                0, 0, 0, 0, 0);
        if (sinceOperation) {
            resultDuration = resultDuration.negated();
        }
        return JSTemporalDuration.create(context, resultDuration);
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
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "era");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalEraYear eraFields = resolveEraFields(plainYearMonth);
        if (eraFields == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(eraFields.era().identifier());
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "eraYear");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalEraYear eraFields = resolveEraFields(plainYearMonth);
        if (eraFields == null || eraFields.eraYear() == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(eraFields.eraYear());
    }

    private static IsoDate getYearMonthRoundedDate(JSContext context, IsoDate originDifferenceDate, long years,
            long months) {
        long monthIndex = (long) originDifferenceDate.month() - 1L + months;
        long yearDelta = Math.floorDiv(monthIndex, 12L);
        int normalizedMonth = (int) (Math.floorMod(monthIndex, 12L) + 1L);
        long normalizedYearAsLong = (long) originDifferenceDate.year() + years + yearDelta;
        if (normalizedYearAsLong < Integer.MIN_VALUE || normalizedYearAsLong > Integer.MAX_VALUE) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        IsoDate roundedDate = new IsoDate((int) normalizedYearAsLong, normalizedMonth, 1);
        if (!isDifferenceDateWithinSupportedRange(roundedDate)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return roundedDate;
    }

    public static JSValue inLeapYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "inLeapYear");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        IsoCalendarDate calendarDateFields = plainYearMonth.getIsoDate()
                .toIsoCalendarDate(plainYearMonth.getCalendarId());
        boolean leapYear = plainYearMonth.getCalendarId().isCalendarLeapYear(calendarDateFields.year());
        if (leapYear) {
            return JSBoolean.TRUE;
        } else {
            return JSBoolean.FALSE;
        }
    }

    private static boolean isDifferenceDateWithinSupportedRange(IsoDate differenceDate) {
        long differenceEpochDay = differenceDate.toEpochDay();
        return differenceEpochDay >= MIN_SUPPORTED_EPOCH_DAY && differenceEpochDay <= MAX_SUPPORTED_EPOCH_DAY;
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "month");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = resolveCalendarDate(plainYearMonth);
        return JSNumber.of(calendarDate.month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "monthCode",
                plainYearMonth -> new JSString(resolveCalendarMonthCode(plainYearMonth)));
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "monthsInYear", plainYearMonth -> JSNumber
                .of(TemporalUtils.monthsInYear(plainYearMonth.getIsoDate(), plainYearMonth.getCalendarId())));
    }

    public static JSValue referenceISODay(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "referenceISODay",
                plainYearMonth -> JSNumber.of(plainYearMonth.getIsoDate().day()));
    }

    private static IsoDate resolveCalendarDate(JSTemporalPlainYearMonth plainYearMonth) {
        IsoCalendarDate calendarDateFields = plainYearMonth.getIsoDate()
                .toIsoCalendarDate(plainYearMonth.getCalendarId());
        return new IsoDate(calendarDateFields.year(), calendarDateFields.month(), calendarDateFields.day());
    }

    private static String resolveCalendarMonthCode(JSTemporalPlainYearMonth plainYearMonth) {
        IsoCalendarDate calendarDateFields = plainYearMonth.getIsoDate()
                .toIsoCalendarDate(plainYearMonth.getCalendarId());
        return calendarDateFields.monthCode();
    }

    private static TemporalEraYear resolveEraFields(JSTemporalPlainYearMonth plainYearMonth) {
        return plainYearMonth.getIsoDate().toTemporalEraYear(plainYearMonth.getCalendarId());
    }

    private static TemporalDurationYearMonth roundRelativeYearMonthDuration(JSContext context,
            TemporalDurationYearMonth durationFields, long destinationMonthsDifference, IsoDate originDifferenceDate,
            TemporalDifferenceSettings differenceSettings) {
        long years = durationFields.years();
        long months = durationFields.months();
        long increment = differenceSettings.roundingIncrement();
        long sign = Long.signum(destinationMonthsDifference);
        if (sign == 0L) {
            return TemporalDurationYearMonth.ZERO;
        }

        long roundingStartValue;
        long roundingEndValue;
        long startYears;
        long startMonths;
        long endYears;
        long endMonths;
        if (differenceSettings.smallestUnit() == TemporalUnit.YEAR) {
            roundingStartValue = TemporalRoundingMode.TRUNC.roundNumberToIncrement(years, increment);
            roundingEndValue = roundingStartValue + increment * sign;
            startYears = roundingStartValue;
            startMonths = 0L;
            endYears = roundingEndValue;
            endMonths = 0L;
        } else {
            roundingStartValue = TemporalRoundingMode.TRUNC.roundNumberToIncrement(months, increment);
            roundingEndValue = roundingStartValue + increment * sign;
            startYears = years;
            startMonths = roundingStartValue;
            endYears = years;
            endMonths = roundingEndValue;
        }

        IsoDate startDate = getYearMonthRoundedDate(context, originDifferenceDate, startYears, startMonths);
        if (context.hasPendingException() || startDate == null) {
            return null;
        }
        IsoDate endDate = getYearMonthRoundedDate(context, originDifferenceDate, endYears, endMonths);
        if (context.hasPendingException() || endDate == null) {
            return null;
        }

        long startTotalMonths = startYears * 12L + startMonths;
        long endTotalMonths = endYears * 12L + endMonths;
        long numerator = destinationMonthsDifference - startTotalMonths;
        long denominator = endTotalMonths - startTotalMonths;
        if (denominator == 0L) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        TemporalUnsignedRoundingMode unsignedRoundingMode = differenceSettings.roundingMode()
                .toUnsigned(TemporalSign.fromSignum(sign));
        int comparison = Long.compare(Math.abs(numerator) * 2L, Math.abs(denominator));
        boolean evenCardinality = Math.floorMod(Math.abs(roundingStartValue) / increment, 2L) == 0L;

        long roundedUnit;
        if (numerator == 0L) {
            roundedUnit = Math.abs(roundingStartValue);
        } else if (numerator == denominator) {
            roundedUnit = Math.abs(roundingEndValue);
        } else {
            roundedUnit = unsignedRoundingMode.getRoundedUnit(Math.abs(roundingStartValue), Math.abs(roundingEndValue),
                    comparison, evenCardinality);
        }
        boolean didExpandCalendarUnit = roundedUnit == Math.abs(roundingEndValue);

        long roundedYears = didExpandCalendarUnit ? endYears : startYears;
        long roundedMonths = didExpandCalendarUnit ? endMonths : startMonths;
        if (didExpandCalendarUnit && differenceSettings.largestUnit() == TemporalUnit.YEAR
                && differenceSettings.smallestUnit() == TemporalUnit.MONTH) {
            long balancedTotalMonths = roundedYears * 12L + roundedMonths;
            roundedYears = balancedTotalMonths / 12L;
            roundedMonths = balancedTotalMonths % 12L;
        }
        return new TemporalDurationYearMonth(roundedYears, roundedMonths);
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        return difference(context, thisArg, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "subtract");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainYearMonth, args, -1);
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
        JSValue locales = JSValue.argumentOrUndefined(args, 0);
        JSValue options = JSValue.argumentOrUndefined(args, 1);
        if (options instanceof JSObject optionsObject) {
            JSValue timeStyleValue = optionsObject.get(PropertyKey.fromString("timeStyle"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(timeStyleValue instanceof JSUndefined) && timeStyleValue != null) {
                context.throwTypeError("Invalid date/time formatting options");
                return JSUndefined.INSTANCE;
            }
        }
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(context, null, new JSValue[]{locales, options});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSValue resolvedOptionsValue = JSIntlObject.dateTimeFormatResolvedOptions(context, dateTimeFormat,
                JSValue.NO_ARGS);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (resolvedOptionsValue instanceof JSObject resolvedOptionsObject) {
            JSValue formatterCalendarValue = resolvedOptionsObject.get(PropertyKey.fromString("calendar"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            TemporalCalendarId formatterCalendarId = TemporalCalendarId.createFromCalendarString(context,
                    formatterCalendarValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!plainYearMonth.getCalendarId().equals(formatterCalendarId)) {
                context.throwRangeError("Invalid date/time value");
                return JSUndefined.INSTANCE;
            }
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
        context.throwTypeError(
                "Do not use Temporal.PlainYearMonth.prototype.valueOf; use Temporal.PlainYearMonth.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
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

        PropertyKey monthKey = PropertyKey.fromString("month");
        PropertyKey monthCodeKey = PropertyKey.fromString("monthCode");
        PropertyKey yearKey = PropertyKey.fromString("year");
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
        boolean calendarSupportsEras = calendarId != TemporalCalendarId.ISO8601
                && calendarId != TemporalCalendarId.CHINESE && calendarId != TemporalCalendarId.DANGI;
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

        IsoDate calendarDate = resolveCalendarDate(plainYearMonth);
        String calendarMonthCode = resolveCalendarMonthCode(plainYearMonth);
        TemporalEraYear eraFields = resolveEraFields(plainYearMonth);

        JSObject mergedFieldsObject = new JSObject(context);
        mergedFieldsObject.set(PropertyKey.fromString("calendar"), new JSString(calendarId.identifier()));
        if (calendarId == TemporalCalendarId.ISO8601) {
            mergedFieldsObject.set(monthKey, JSNumber.of(calendarDate.month()));
        }
        mergedFieldsObject.set(monthCodeKey, new JSString(calendarMonthCode));
        mergedFieldsObject.set(yearKey, JSNumber.of(calendarDate.year()));
        if (eraFields != null) {
            mergedFieldsObject.set(eraKey, new JSString(eraFields.era().identifier()));
            if (eraFields.eraYear() != null) {
                mergedFieldsObject.set(eraYearKey, JSNumber.of(eraFields.eraYear()));
            }
        }

        if (hasYear) {
            mergedFieldsObject.delete(eraKey);
            mergedFieldsObject.delete(eraYearKey);
        }
        if (hasEra || hasEraYear) {
            mergedFieldsObject.delete(yearKey);
            mergedFieldsObject.delete(eraKey);
            mergedFieldsObject.delete(eraYearKey);
        }
        if (hasMonth) {
            mergedFieldsObject.delete(monthCodeKey);
        }
        if (hasMonthCode) {
            mergedFieldsObject.delete(monthKey);
        }

        if (hasMonth) {
            mergedFieldsObject.set(monthKey, JSNumber.of(month));
        }
        if (hasMonthCode) {
            if (monthCodeInfo != null) {
                mergedFieldsObject.set(monthCodeKey, new JSString(monthCodeInfo.monthCode()));
            }
        }
        if (hasYear) {
            mergedFieldsObject.set(yearKey, JSNumber.of(year));
        }
        if (hasEra) {
            mergedFieldsObject.set(eraKey, new JSString(era));
        }
        if (hasEraYear) {
            mergedFieldsObject.set(eraYearKey, JSNumber.of(eraYear));
        }
        JSObject normalizedOptionsObject = new JSObject(context);
        normalizedOptionsObject.set(PropertyKey.fromString("overflow"), new JSString(overflow));
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
        JSTemporalPlainYearMonth plainYearMonth = RECEIVER.require(context, thisArg, "year");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = resolveCalendarDate(plainYearMonth);
        return JSNumber.of(calendarDate.year());
    }
}
