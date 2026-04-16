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
    private static final String DIFFERENCE_LARGEST_UNIT_OPTION = "largestUnit";
    private static final String DIFFERENCE_ROUNDING_INCREMENT_OPTION = "roundingIncrement";
    private static final String DIFFERENCE_ROUNDING_MODE_OPTION = "roundingMode";
    private static final String DIFFERENCE_SMALLEST_UNIT_OPTION = "smallestUnit";
    private static final long MAX_SUPPORTED_EPOCH_DAY = TemporalConstants.MAX_SUPPORTED_EPOCH_DAY;
    private static final long MIN_SUPPORTED_EPOCH_DAY = TemporalConstants.MIN_SUPPORTED_EPOCH_DAY;
    private static final long TEMPORAL_MAX_ROUNDING_INCREMENT = TemporalConstants.MAX_ROUNDING_INCREMENT;
    private static final String TYPE_NAME = "Temporal.PlainYearMonth";
    private static final String UNIT_AUTO = "auto";
    private static final String UNIT_MONTH = "month";
    private static final String UNIT_YEAR = "year";

    private TemporalPlainYearMonthPrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "add");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainYearMonth, args, 1);
    }

    private static IsoDate addCalendarDateConstrain(
            JSContext context,
            IsoDate baseIsoDate,
            String calendarId,
            long years,
            long months) {
        if ("iso8601".equals(calendarId)) {
            return addDateDurationToPlainYearMonth(context, baseIsoDate, years, months, "constrain");
        }
        return TemporalCalendarMath.addCalendarDate(
                context,
                baseIsoDate,
                calendarId,
                years,
                months,
                0L,
                0L,
                "constrain");
    }

    private static IsoDate addDateDurationToPlainYearMonth(
            JSContext context,
            IsoDate baseDate,
            long yearsToAdd,
            long monthsToAdd,
            String overflow) {
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

    private static JSValue addOrSubtract(JSContext context, JSTemporalPlainYearMonth plainYearMonth, JSValue[] args, int sign) {
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

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException() || overflow == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDate isoDate = plainYearMonth.getIsoDate();
        if (!isoDate.isValid()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        if (durationRecord.weeks() != 0
                || durationRecord.days() != 0
                || durationRecord.hours() != 0
                || durationRecord.minutes() != 0
                || durationRecord.seconds() != 0
                || durationRecord.milliseconds() != 0
                || durationRecord.microseconds() != 0
                || durationRecord.nanoseconds() != 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        String calendarId = plainYearMonth.getCalendarId();
        IsoDate resultDate;
        if ("iso8601".equals(calendarId)) {
            resultDate = addDateDurationToPlainYearMonth(
                    context,
                    isoDate,
                    durationRecord.years(),
                    durationRecord.months(),
                    overflow);
        } else {
            resultDate = TemporalCalendarMath.addCalendarDate(
                    context,
                    isoDate,
                    calendarId,
                    durationRecord.years(),
                    durationRecord.months(),
                    0L,
                    0L,
                    overflow);
        }
        if (context.hasPendingException() || resultDate == null) {
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainYearMonthConstructor.createPlainYearMonth(context, resultDate, calendarId);
    }

    private static long applyUnsignedRoundingMode(
            long roundingFloor,
            long roundingCeiling,
            int comparison,
            boolean evenCardinality,
            String unsignedRoundingMode) {
        if ("zero".equals(unsignedRoundingMode)) {
            return roundingFloor;
        }
        if ("infinity".equals(unsignedRoundingMode)) {
            return roundingCeiling;
        }
        if (comparison < 0) {
            return roundingFloor;
        }
        if (comparison > 0) {
            return roundingCeiling;
        }
        if ("half-zero".equals(unsignedRoundingMode)) {
            return roundingFloor;
        }
        if ("half-infinity".equals(unsignedRoundingMode)) {
            return roundingCeiling;
        }
        if (evenCardinality) {
            return roundingFloor;
        }
        return roundingCeiling;
    }

    public static JSValue calendarId(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "calendarId");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(plainYearMonth.getCalendarId());
    }

    private static String canonicalizeDifferenceUnit(String unitText, boolean allowAuto) {
        if (unitText == null) {
            return null;
        }
        if (allowAuto && UNIT_AUTO.equals(unitText)) {
            return UNIT_AUTO;
        }
        TemporalUnit unit = TemporalUnit.fromString(unitText);
        return unit != null && unit.isLargerOrEqual(TemporalUnit.MONTH) ? unit.jsName() : null;
    }

    private static JSTemporalPlainYearMonth checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalPlainYearMonth plainYearMonth)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return plainYearMonth;
    }

    private static int compareCalendarDateFields(
            long firstYear,
            String firstMonthCode,
            int firstDay,
            int secondYear,
            String secondMonthCode,
            int secondDay) {
        int yearComparison = Long.compare(firstYear, secondYear);
        if (yearComparison != 0) {
            return yearComparison;
        }
        int monthComparison = compareMonthCodes(firstMonthCode, secondMonthCode);
        if (monthComparison != 0) {
            return monthComparison;
        }
        return Integer.compare(firstDay, secondDay);
    }

    private static int compareMonthCodes(String firstMonthCode, String secondMonthCode) {
        TemporalParsedMonthCode firstMonthCodeParts = parseMonthCodeParts(firstMonthCode);
        TemporalParsedMonthCode secondMonthCodeParts = parseMonthCodeParts(secondMonthCode);
        if (firstMonthCodeParts == null || secondMonthCodeParts == null) {
            return firstMonthCode.compareTo(secondMonthCode);
        }
        int monthNumberComparison = Integer.compare(
                firstMonthCodeParts.month(),
                secondMonthCodeParts.month());
        if (monthNumberComparison != 0) {
            return monthNumberComparison;
        }
        if (firstMonthCodeParts.leapMonth() == secondMonthCodeParts.leapMonth()) {
            return 0;
        }
        if (firstMonthCodeParts.leapMonth()) {
            return 1;
        }
        return -1;
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

    private static TemporalDurationYearMonthFields createDurationFieldsFromTotalMonths(long totalMonthsDifference, String largestUnit) {
        if (UNIT_YEAR.equals(largestUnit)) {
            long years = totalMonthsDifference / 12L;
            long months = totalMonthsDifference % 12L;
            return new TemporalDurationYearMonthFields(years, months);
        }
        return new TemporalDurationYearMonthFields(0L, totalMonthsDifference);
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "daysInMonth");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(TemporalCalendarMath.daysInMonth(plainYearMonth.getIsoDate(), plainYearMonth.getCalendarId()));
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "daysInYear");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(TemporalCalendarMath.daysInYear(plainYearMonth.getIsoDate(), plainYearMonth.getCalendarId()));
    }

    private static TemporalDurationYearMonthFields differenceCalendarYearMonth(
            JSContext context,
            IsoDate firstDate,
            IsoDate secondDate,
            String calendarId,
            String largestUnit) {
        int sign = -Integer.signum(firstDate.compareTo(secondDate));
        if (sign == 0) {
            return TemporalDurationYearMonthFields.ZERO;
        }

        long years = 0L;
        long months = 0L;
        IsoCalendarDate firstCalendarDateFields = firstDate.toIsoCalendarDate(calendarId);
        IsoCalendarDate secondCalendarDateFields = secondDate.toIsoCalendarDate(calendarId);

        long candidateYears = (long) secondCalendarDateFields.year() - firstCalendarDateFields.year();
        if (candidateYears != 0L) {
            candidateYears -= sign;
        }
        while (true) {
            IsoDate candidateYearDate = addCalendarDateConstrain(context, firstDate, calendarId, candidateYears, 0L);
            if (context.hasPendingException() || candidateYearDate == null) {
                return null;
            }
            if (isoDateSurpasses(sign, candidateYearDate, secondDate)) {
                break;
            }
            if (doesConceptualYearDateSurpassTarget(
                    sign,
                    firstCalendarDateFields,
                    candidateYears,
                    secondCalendarDateFields)) {
                break;
            }
            if (doesConstrainedCalendarDaySurpassTarget(
                    sign,
                    firstCalendarDateFields,
                    candidateYearDate,
                    secondCalendarDateFields,
                    calendarId)) {
                break;
            }
            years = candidateYears;
            candidateYears += sign;
        }

        long candidateMonths = sign;
        while (true) {
            IsoDate candidateMonthDate = addCalendarDateConstrain(context, firstDate, calendarId, years, candidateMonths);
            if (context.hasPendingException() || candidateMonthDate == null) {
                return null;
            }
            if (isoDateSurpasses(sign, candidateMonthDate, secondDate)) {
                break;
            }
            if (doesConstrainedCalendarDaySurpassTarget(
                    sign,
                    firstCalendarDateFields,
                    candidateMonthDate,
                    secondCalendarDateFields,
                    calendarId)) {
                break;
            }
            months = candidateMonths;
            candidateMonths += sign;
        }

        if (UNIT_MONTH.equals(largestUnit)) {
            long monthsFromYears = monthsForYearDelta(context, firstDate, calendarId, years);
            if (context.hasPendingException()) {
                return null;
            }
            long totalMonths;
            try {
                totalMonths = Math.addExact(months, monthsFromYears);
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return null;
            }

            while (true) {
                IsoDate totalMonthDate = addCalendarDateConstrain(context, firstDate, calendarId, 0L, totalMonths);
                if (context.hasPendingException() || totalMonthDate == null) {
                    return null;
                }
                if (isoDateSurpasses(sign, totalMonthDate, secondDate)) {
                    totalMonths -= sign;
                    continue;
                }
                if (doesConstrainedCalendarDaySurpassTarget(
                        sign,
                        firstCalendarDateFields,
                        totalMonthDate,
                        secondCalendarDateFields,
                        calendarId)) {
                    totalMonths -= sign;
                    continue;
                }

                long nextTotalMonths;
                try {
                    nextTotalMonths = Math.addExact(totalMonths, sign);
                } catch (ArithmeticException arithmeticException) {
                    break;
                }
                IsoDate nextTotalMonthDate = addCalendarDateConstrain(context, firstDate, calendarId, 0L, nextTotalMonths);
                if (context.hasPendingException() || nextTotalMonthDate == null) {
                    return null;
                }
                if (isoDateSurpasses(sign, nextTotalMonthDate, secondDate)) {
                    break;
                }
                if (doesConstrainedCalendarDaySurpassTarget(
                        sign,
                        firstCalendarDateFields,
                        nextTotalMonthDate,
                        secondCalendarDateFields,
                        calendarId)) {
                    break;
                }
                totalMonths = nextTotalMonths;
            }
            return new TemporalDurationYearMonthFields(0L, totalMonths);
        }
        return new TemporalDurationYearMonthFields(years, months);
    }

    private static JSValue differenceTemporalPlainYearMonth(
            JSContext context,
            JSTemporalPlainYearMonth plainYearMonth,
            JSValue[] args,
            boolean sinceOperation) {
        JSValue otherArgument = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSTemporalPlainYearMonth otherPlainYearMonth = TemporalPlainYearMonthConstructor.toTemporalYearMonthObject(context, otherArgument);
        if (context.hasPendingException() || otherPlainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        if (!plainYearMonth.getCalendarId().equals(otherPlainYearMonth.getCalendarId())) {
            context.throwRangeError("Temporal error: Mismatched calendars.");
            return JSUndefined.INSTANCE;
        }

        JSValue optionsArgument = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        TemporalDifferenceSettings differenceSettings = getDifferenceSettings(context, sinceOperation, optionsArgument);
        if (context.hasPendingException() || differenceSettings == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDate thisIsoDate = plainYearMonth.getIsoDate();
        IsoDate otherIsoDate = otherPlainYearMonth.getIsoDate();
        if (thisIsoDate.year() == otherIsoDate.year()
                && thisIsoDate.month() == otherIsoDate.month()
                && thisIsoDate.day() == otherIsoDate.day()) {
            return TemporalDurationConstructor.createDuration(context, TemporalDuration.ZERO);
        }

        IsoDate thisDifferenceDate = createDifferenceIsoDate(context, plainYearMonth);
        if (context.hasPendingException() || thisDifferenceDate == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate otherDifferenceDate = createDifferenceIsoDate(context, otherPlainYearMonth);
        if (context.hasPendingException() || otherDifferenceDate == null) {
            return JSUndefined.INSTANCE;
        }

        String calendarId = plainYearMonth.getCalendarId();
        TemporalDurationYearMonthFields durationFields;
        long totalMonthsDifference;
        if ("iso8601".equals(calendarId)) {
            totalMonthsDifference = (long) (otherDifferenceDate.year() - thisDifferenceDate.year()) * 12L
                    + (otherDifferenceDate.month() - thisDifferenceDate.month());
            durationFields = createDurationFieldsFromTotalMonths(
                    totalMonthsDifference,
                    differenceSettings.largestUnit());
        } else {
            durationFields = differenceCalendarYearMonth(
                    context,
                    thisDifferenceDate,
                    otherDifferenceDate,
                    calendarId,
                    differenceSettings.largestUnit());
            if (context.hasPendingException() || durationFields == null) {
                return JSUndefined.INSTANCE;
            }
            TemporalDurationYearMonthFields monthDifferenceFields = differenceCalendarYearMonth(
                    context,
                    thisDifferenceDate,
                    otherDifferenceDate,
                    calendarId,
                    UNIT_MONTH);
            if (context.hasPendingException() || monthDifferenceFields == null) {
                return JSUndefined.INSTANCE;
            }
            totalMonthsDifference = monthDifferenceFields.months();
        }
        boolean roundingNoOp = UNIT_MONTH.equals(differenceSettings.smallestUnit())
                && differenceSettings.roundingIncrement() == 1L;
        if (!roundingNoOp) {
            durationFields = roundRelativeYearMonthDuration(
                    context,
                    durationFields,
                    totalMonthsDifference,
                    thisDifferenceDate,
                    differenceSettings);
            if (context.hasPendingException() || durationFields == null) {
                return JSUndefined.INSTANCE;
            }
        }

        TemporalDuration resultDuration = new TemporalDuration(
                durationFields.years(),
                durationFields.months(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0);
        if (sinceOperation) {
            resultDuration = resultDuration.negated();
        }
        return TemporalDurationConstructor.createDuration(context, resultDuration);
    }

    private static boolean doesConceptualYearDateSurpassTarget(
            int sign,
            IsoCalendarDate firstCalendarDateFields,
            long candidateYears,
            IsoCalendarDate secondCalendarDateFields) {
        long candidateYear = firstCalendarDateFields.year() + candidateYears;
        int comparison = compareCalendarDateFields(
                candidateYear,
                firstCalendarDateFields.monthCode(),
                firstCalendarDateFields.day(),
                secondCalendarDateFields.year(),
                secondCalendarDateFields.monthCode(),
                secondCalendarDateFields.day());
        return sign * comparison > 0;
    }

    private static boolean doesConstrainedCalendarDaySurpassTarget(
            int sign,
            IsoCalendarDate firstCalendarDateFields,
            IsoDate constrainedCandidateDate,
            IsoCalendarDate secondCalendarDateFields,
            String calendarId) {
        IsoCalendarDate candidateCalendarDateFields = constrainedCandidateDate.toIsoCalendarDate(calendarId);
        if (candidateCalendarDateFields.day() == firstCalendarDateFields.day()) {
            return false;
        }
        if (candidateCalendarDateFields.year() != secondCalendarDateFields.year()) {
            return false;
        }
        if (!candidateCalendarDateFields.monthCode().equals(secondCalendarDateFields.monthCode())) {
            return false;
        }
        return sign * Integer.compare(firstCalendarDateFields.day(), secondCalendarDateFields.day()) > 0;
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "equals");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue otherArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "era");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalEraFields eraFields = resolveEraFields(plainYearMonth);
        if (eraFields == null || eraFields.era() == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(eraFields.era());
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "eraYear");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalEraFields eraFields = resolveEraFields(plainYearMonth);
        if (eraFields == null || eraFields.eraYear() == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(eraFields.eraYear());
    }

    private static long getDifferenceRoundingIncrementOption(JSContext context, JSObject optionsObject) {
        JSValue optionValue = optionsObject.get(PropertyKey.fromString(DIFFERENCE_ROUNDING_INCREMENT_OPTION));
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (optionValue instanceof JSUndefined || optionValue == null) {
            return 1L;
        }
        JSNumber numericValue = JSTypeConversions.toNumber(context, optionValue);
        if (context.hasPendingException() || numericValue == null) {
            return Long.MIN_VALUE;
        }
        double roundingIncrement = numericValue.value();
        if (!Double.isFinite(roundingIncrement) || Double.isNaN(roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return Long.MIN_VALUE;
        }
        long integerIncrement = (long) roundingIncrement;
        if (integerIncrement < 1L || integerIncrement > TEMPORAL_MAX_ROUNDING_INCREMENT) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return Long.MIN_VALUE;
        }
        return integerIncrement;
    }

    private static TemporalDifferenceSettings getDifferenceSettings(
            JSContext context,
            boolean sinceOperation,
            JSValue optionsArgument) {
        JSObject optionsObject = null;
        if (!(optionsArgument instanceof JSUndefined) && optionsArgument != null) {
            if (optionsArgument instanceof JSObject castedOptionsObject) {
                optionsObject = castedOptionsObject;
            } else {
                context.throwTypeError("Temporal error: Options must be an object.");
                return null;
            }
        }

        String largestUnitText = null;
        long roundingIncrement = 1L;
        String roundingMode = "trunc";
        String smallestUnitText = null;
        if (optionsObject != null) {
            largestUnitText = getDifferenceStringOption(context, optionsObject, DIFFERENCE_LARGEST_UNIT_OPTION, null);
            if (context.hasPendingException()) {
                return null;
            }
            roundingIncrement = getDifferenceRoundingIncrementOption(context, optionsObject);
            if (context.hasPendingException()) {
                return null;
            }
            roundingMode = getDifferenceStringOption(context, optionsObject, DIFFERENCE_ROUNDING_MODE_OPTION, "trunc");
            if (context.hasPendingException()) {
                return null;
            }
            smallestUnitText = getDifferenceStringOption(context, optionsObject, DIFFERENCE_SMALLEST_UNIT_OPTION, null);
            if (context.hasPendingException()) {
                return null;
            }
        }

        String largestUnit = largestUnitText == null
                ? UNIT_AUTO
                : canonicalizeDifferenceUnit(largestUnitText, true);
        if (largestUnit == null) {
            context.throwRangeError("Temporal error: Invalid largest unit.");
            return null;
        }
        String smallestUnit = smallestUnitText == null
                ? UNIT_MONTH
                : canonicalizeDifferenceUnit(smallestUnitText, false);
        if (smallestUnit == null) {
            context.throwRangeError("Temporal error: Invalid smallest unit.");
            return null;
        }
        if (!isValidDifferenceRoundingMode(roundingMode)) {
            context.throwRangeError("Temporal error: Invalid rounding mode.");
            return null;
        }
        if (sinceOperation) {
            roundingMode = negateRoundingMode(roundingMode);
        }
        if (!UNIT_AUTO.equals(largestUnit) && !isYearMonthUnit(largestUnit)) {
            context.throwRangeError("Temporal error: Invalid largest unit.");
            return null;
        }
        if (!isYearMonthUnit(smallestUnit)) {
            context.throwRangeError("Temporal error: Invalid smallest unit.");
            return null;
        }

        if (UNIT_AUTO.equals(largestUnit)) {
            largestUnit = largerOfTwoTemporalUnits(UNIT_YEAR, smallestUnit);
        }
        if (!largestUnit.equals(largerOfTwoTemporalUnits(largestUnit, smallestUnit))) {
            context.throwRangeError("Temporal error: smallestUnit must be smaller than largestUnit.");
            return null;
        }

        return new TemporalDifferenceSettings(largestUnit, smallestUnit, roundingIncrement, roundingMode);
    }

    private static String getDifferenceStringOption(
            JSContext context,
            JSObject optionsObject,
            String optionName,
            String defaultValue) {
        JSValue optionValue = optionsObject.get(PropertyKey.fromString(optionName));
        if (context.hasPendingException()) {
            return null;
        }
        if (optionValue instanceof JSUndefined || optionValue == null) {
            return defaultValue;
        }
        JSString optionText = JSTypeConversions.toString(context, optionValue);
        if (context.hasPendingException() || optionText == null) {
            return null;
        }
        return optionText.value();
    }

    private static String getUnsignedRoundingMode(String roundingMode, String sign) {
        boolean negativeSign = "negative".equals(sign);
        return switch (roundingMode) {
            case "ceil" -> negativeSign ? "zero" : "infinity";
            case "floor" -> negativeSign ? "infinity" : "zero";
            case "expand" -> "infinity";
            case "trunc" -> "zero";
            case "halfCeil" -> negativeSign ? "half-zero" : "half-infinity";
            case "halfFloor" -> negativeSign ? "half-infinity" : "half-zero";
            case "halfExpand" -> "half-infinity";
            case "halfTrunc" -> "half-zero";
            case "halfEven" -> "half-even";
            default -> "half-infinity";
        };
    }

    private static IsoDate getYearMonthRoundedDate(
            JSContext context,
            IsoDate originDifferenceDate,
            long years,
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
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "inLeapYear");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        if (TemporalCalendarMath.inLeapYear(plainYearMonth.getIsoDate(), plainYearMonth.getCalendarId())) {
            return JSBoolean.TRUE;
        }
        return JSBoolean.FALSE;
    }

    private static boolean isDifferenceDateWithinSupportedRange(IsoDate differenceDate) {
        long differenceEpochDay = differenceDate.toEpochDay();
        return differenceEpochDay >= MIN_SUPPORTED_EPOCH_DAY && differenceEpochDay <= MAX_SUPPORTED_EPOCH_DAY;
    }

    private static boolean isValidDifferenceRoundingMode(String roundingMode) {
        return TemporalRoundingMode.isValid(roundingMode);
    }

    private static boolean isYearMonthUnit(String unit) {
        return UNIT_YEAR.equals(unit) || UNIT_MONTH.equals(unit);
    }

    private static boolean isoDateSurpasses(int sign, IsoDate firstDate, IsoDate secondDate) {
        return sign * firstDate.compareTo(secondDate) > 0;
    }

    private static String largerOfTwoTemporalUnits(String leftUnit, String rightUnit) {
        int leftRank = temporalUnitRank(leftUnit);
        int rightRank = temporalUnitRank(rightUnit);
        if (leftRank > rightRank) {
            return rightUnit;
        }
        return leftUnit;
    }

    public static JSValue month(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "month");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = resolveCalendarDate(plainYearMonth);
        return JSNumber.of(calendarDate.month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "monthCode");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(resolveCalendarMonthCode(plainYearMonth));
    }

    private static long monthsForYearDelta(
            JSContext context,
            IsoDate firstDate,
            String calendarId,
            long yearDelta) {
        if (yearDelta == 0L) {
            return 0L;
        }
        if ("coptic".equals(calendarId) || "ethiopic".equals(calendarId) || "ethioaa".equals(calendarId)) {
            try {
                return Math.multiplyExact(yearDelta, 13L);
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return 0L;
            }
        }
        if ("iso8601".equals(calendarId)
                || "gregory".equals(calendarId)
                || "japanese".equals(calendarId)
                || "buddhist".equals(calendarId)
                || "roc".equals(calendarId)
                || "indian".equals(calendarId)
                || "persian".equals(calendarId)
                || "islamic-civil".equals(calendarId)
                || "islamic-tbla".equals(calendarId)
                || "islamic-umalqura".equals(calendarId)) {
            try {
                return Math.multiplyExact(yearDelta, 12L);
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return 0L;
            }
        }

        int yearSign;
        if (yearDelta > 0L) {
            yearSign = 1;
        } else {
            yearSign = -1;
        }
        long absoluteYearDelta = Math.abs(yearDelta);
        long totalMonths = 0L;
        IsoDate cursorDate = firstDate;
        for (long yearIndex = 0L; yearIndex < absoluteYearDelta; yearIndex++) {
            int monthsInYear = TemporalCalendarMath.monthsInYear(cursorDate, calendarId);
            try {
                totalMonths = Math.addExact(totalMonths, (long) yearSign * monthsInYear);
            } catch (ArithmeticException arithmeticException) {
                context.throwRangeError("Temporal error: Duration field out of range.");
                return 0L;
            }
            cursorDate = addCalendarDateConstrain(context, cursorDate, calendarId, yearSign, 0L);
            if (context.hasPendingException() || cursorDate == null) {
                return 0L;
            }
        }
        return totalMonths;
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "monthsInYear");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(TemporalCalendarMath.monthsInYear(plainYearMonth.getIsoDate(), plainYearMonth.getCalendarId()));
    }

    private static String negateRoundingMode(String roundingMode) {
        TemporalRoundingMode mode = TemporalRoundingMode.fromString(roundingMode);
        return mode != null ? mode.negate().jsName() : roundingMode;
    }

    private static TemporalParsedMonthCode parseMonthCodeForWith(JSContext context, String monthCode) {
        if (monthCode == null || monthCode.length() < 3 || monthCode.length() > 4) {
            context.throwRangeError("Temporal error: Month code out of range.");
            return null;
        }
        if (monthCode.charAt(0) != 'M') {
            context.throwRangeError("Temporal error: Month code out of range.");
            return null;
        }
        if (!Character.isDigit(monthCode.charAt(1)) || !Character.isDigit(monthCode.charAt(2))) {
            context.throwRangeError("Temporal error: Month code out of range.");
            return null;
        }
        boolean leapMonth = false;
        if (monthCode.length() == 4) {
            if (monthCode.charAt(3) != 'L') {
                context.throwRangeError("Temporal error: Month code out of range.");
                return null;
            }
            leapMonth = true;
        }
        int month = Integer.parseInt(monthCode.substring(1, 3));
        return new TemporalParsedMonthCode(month, leapMonth);
    }

    private static TemporalParsedMonthCode parseMonthCodeParts(String monthCodeText) {
        if (monthCodeText == null || monthCodeText.length() < 3 || monthCodeText.length() > 4) {
            return null;
        }
        if (monthCodeText.charAt(0) != 'M') {
            return null;
        }
        if (!Character.isDigit(monthCodeText.charAt(1)) || !Character.isDigit(monthCodeText.charAt(2))) {
            return null;
        }
        int monthNumber = Integer.parseInt(monthCodeText.substring(1, 3));
        boolean leapMonth = false;
        if (monthCodeText.length() == 4) {
            if (monthCodeText.charAt(3) != 'L') {
                return null;
            }
            leapMonth = true;
        }
        return new TemporalParsedMonthCode(monthNumber, leapMonth);
    }

    public static JSValue referenceISODay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "referenceISODay");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainYearMonth.getIsoDate().day());
    }

    private static IsoDate resolveCalendarDate(JSTemporalPlainYearMonth plainYearMonth) {
        IsoCalendarDate calendarDateFields =
                plainYearMonth.getIsoDate().toIsoCalendarDate(plainYearMonth.getCalendarId());
        return new IsoDate(
                calendarDateFields.year(),
                calendarDateFields.month(),
                calendarDateFields.day());
    }

    private static String resolveCalendarMonthCode(JSTemporalPlainYearMonth plainYearMonth) {
        IsoCalendarDate calendarDateFields =
                plainYearMonth.getIsoDate().toIsoCalendarDate(plainYearMonth.getCalendarId());
        return calendarDateFields.monthCode();
    }

    private static TemporalEraFields resolveEraFields(JSTemporalPlainYearMonth plainYearMonth) {
        String calendarId = plainYearMonth.getCalendarId();
        if ("iso8601".equals(calendarId) || "chinese".equals(calendarId) || "dangi".equals(calendarId)) {
            return null;
        }

        IsoDate isoDate = plainYearMonth.getIsoDate();
        IsoDate calendarDate = resolveCalendarDate(plainYearMonth);
        int calendarYear = calendarDate.year();

        if ("gregory".equals(calendarId)) {
            if (calendarYear <= 0) {
                return new TemporalEraFields("bce", 1 - calendarYear);
            } else {
                return new TemporalEraFields("ce", calendarYear);
            }
        }
        if ("japanese".equals(calendarId)) {
            LocalDate date = LocalDate.of(isoDate.year(), isoDate.month(), isoDate.day());
            LocalDate reiwaStart = LocalDate.of(2019, 5, 1);
            LocalDate heiseiStart = LocalDate.of(1989, 1, 8);
            LocalDate showaStart = LocalDate.of(1926, 12, 25);
            LocalDate taishoStart = LocalDate.of(1912, 7, 30);
            LocalDate meijiStart = LocalDate.of(1873, 1, 1);

            if (!date.isBefore(reiwaStart)) {
                return new TemporalEraFields("reiwa", isoDate.year() - 2018);
            } else if (!date.isBefore(heiseiStart)) {
                return new TemporalEraFields("heisei", isoDate.year() - 1988);
            } else if (!date.isBefore(showaStart)) {
                return new TemporalEraFields("showa", isoDate.year() - 1925);
            } else if (!date.isBefore(taishoStart)) {
                return new TemporalEraFields("taisho", isoDate.year() - 1911);
            } else if (!date.isBefore(meijiStart)) {
                return new TemporalEraFields("meiji", isoDate.year() - 1867);
            } else if (isoDate.year() <= 0) {
                return new TemporalEraFields("bce", 1 - isoDate.year());
            } else {
                return new TemporalEraFields("ce", isoDate.year());
            }
        }
        if ("roc".equals(calendarId)) {
            if (calendarYear >= 1) {
                return new TemporalEraFields("roc", calendarYear);
            } else {
                return new TemporalEraFields("broc", 1 - calendarYear);
            }
        }
        if ("buddhist".equals(calendarId)) {
            return new TemporalEraFields("be", calendarYear);
        }
        if ("coptic".equals(calendarId)) {
            return new TemporalEraFields("am", calendarYear);
        }
        if ("ethiopic".equals(calendarId)) {
            if (calendarYear <= 0) {
                return new TemporalEraFields("aa", 5500 + calendarYear);
            } else {
                return new TemporalEraFields("am", calendarYear);
            }
        }
        if ("ethioaa".equals(calendarId)) {
            return new TemporalEraFields("aa", calendarYear);
        }
        if ("hebrew".equals(calendarId)) {
            return new TemporalEraFields("am", calendarYear);
        }
        if ("indian".equals(calendarId)) {
            return new TemporalEraFields("shaka", calendarYear);
        }
        if ("persian".equals(calendarId)) {
            return new TemporalEraFields("ap", calendarYear);
        }
        if ("islamic-civil".equals(calendarId)
                || "islamic-tbla".equals(calendarId)
                || "islamic-umalqura".equals(calendarId)) {
            if (calendarYear > 0) {
                return new TemporalEraFields("ah", calendarYear);
            } else {
                return new TemporalEraFields("bh", 1 - calendarYear);
            }
        }
        return null;
    }

    private static long roundNumberToIncrement(long quantity, long increment, String roundingMode) {
        long quotient = quantity / increment;
        long remainder = quantity % increment;
        String sign = quantity < 0 ? "negative" : "positive";
        long roundingFloor = Math.abs(quotient);
        long roundingCeiling = roundingFloor + 1L;
        int comparison = Integer.compare(Long.compare(Math.abs(remainder * 2L), increment), 0);
        boolean evenCardinality = Math.floorMod(roundingFloor, 2L) == 0L;
        String unsignedRoundingMode = getUnsignedRoundingMode(roundingMode, sign);
        long rounded;
        if (remainder == 0L) {
            rounded = roundingFloor;
        } else {
            rounded = applyUnsignedRoundingMode(
                    roundingFloor,
                    roundingCeiling,
                    comparison,
                    evenCardinality,
                    unsignedRoundingMode);
        }
        if ("positive".equals(sign)) {
            return increment * rounded;
        }
        return -increment * rounded;
    }

    private static TemporalDurationYearMonthFields roundRelativeYearMonthDuration(
            JSContext context,
            TemporalDurationYearMonthFields durationFields,
            long destinationMonthsDifference,
            IsoDate originDifferenceDate,
            TemporalDifferenceSettings differenceSettings) {
        long years = durationFields.years();
        long months = durationFields.months();
        long increment = differenceSettings.roundingIncrement();
        long sign = Long.signum(destinationMonthsDifference);
        if (sign == 0L) {
            return TemporalDurationYearMonthFields.ZERO;
        }

        long roundingStartValue;
        long roundingEndValue;
        long startYears;
        long startMonths;
        long endYears;
        long endMonths;
        if (UNIT_YEAR.equals(differenceSettings.smallestUnit())) {
            roundingStartValue = roundNumberToIncrement(years, increment, "trunc");
            roundingEndValue = roundingStartValue + increment * sign;
            startYears = roundingStartValue;
            startMonths = 0L;
            endYears = roundingEndValue;
            endMonths = 0L;
        } else {
            roundingStartValue = roundNumberToIncrement(months, increment, "trunc");
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

        String signText = sign < 0L ? "negative" : "positive";
        String unsignedRoundingMode = getUnsignedRoundingMode(differenceSettings.roundingMode(), signText);
        int comparison = Long.compare(Math.abs(numerator) * 2L, Math.abs(denominator));
        boolean evenCardinality = Math.floorMod(Math.abs(roundingStartValue) / increment, 2L) == 0L;

        long roundedUnit;
        if (numerator == 0L) {
            roundedUnit = Math.abs(roundingStartValue);
        } else if (numerator == denominator) {
            roundedUnit = Math.abs(roundingEndValue);
        } else {
            roundedUnit = applyUnsignedRoundingMode(
                    Math.abs(roundingStartValue),
                    Math.abs(roundingEndValue),
                    comparison,
                    evenCardinality,
                    unsignedRoundingMode);
        }
        boolean didExpandCalendarUnit = roundedUnit == Math.abs(roundingEndValue);

        long roundedYears = didExpandCalendarUnit ? endYears : startYears;
        long roundedMonths = didExpandCalendarUnit ? endMonths : startMonths;
        if (didExpandCalendarUnit
                && UNIT_YEAR.equals(differenceSettings.largestUnit())
                && UNIT_MONTH.equals(differenceSettings.smallestUnit())) {
            long balancedTotalMonths = roundedYears * 12L + roundedMonths;
            roundedYears = balancedTotalMonths / 12L;
            roundedMonths = balancedTotalMonths % 12L;
        }
        return new TemporalDurationYearMonthFields(roundedYears, roundedMonths);
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "since");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return differenceTemporalPlainYearMonth(context, plainYearMonth, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "subtract");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return addOrSubtract(context, plainYearMonth, args, -1);
    }

    private static int temporalUnitRank(String unit) {
        TemporalUnit tu = TemporalUnit.fromString(unit);
        return tu != null ? tu.ordinal() : TemporalUnit.values().length;
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "toJSON");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = plainYearMonth.getIsoDate();
        String formattedDate = new IsoDate(isoDate.year(), isoDate.month(), 1).toString();
        return new JSString(formattedDate.substring(0, formattedDate.lastIndexOf('-')));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "toLocaleString");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue locales = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
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
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(
                context,
                null,
                new JSValue[]{locales, options});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSValue resolvedOptionsValue = JSIntlObject.dateTimeFormatResolvedOptions(context, dateTimeFormat, JSValue.NO_ARGS);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (resolvedOptionsValue instanceof JSObject resolvedOptionsObject) {
            JSValue formatterCalendarValue = resolvedOptionsObject.get(PropertyKey.fromString("calendar"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String formatterCalendarId = TemporalUtils.validateCalendar(context, formatterCalendarValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!plainYearMonth.getCalendarId().equals(formatterCalendarId)) {
                context.throwRangeError("Invalid date/time value");
                return JSUndefined.INSTANCE;
            }
        }

        JSTemporalPlainYearMonth plainYearMonthForFormatting = plainYearMonth;
        if (!"iso8601".equals(plainYearMonth.getCalendarId())) {
            IsoCalendarDate calendarDateFields =
                    plainYearMonth.getIsoDate().toIsoCalendarDate(plainYearMonth.getCalendarId());
            IsoDate midMonthIsoDate = TemporalCalendarMath.calendarDateToIsoDate(
                    context,
                    plainYearMonth.getCalendarId(),
                    calendarDateFields.year(),
                    null,
                    calendarDateFields.monthCode(),
                    15,
                    "constrain");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (midMonthIsoDate != null) {
                plainYearMonthForFormatting = TemporalPlainYearMonthConstructor.createPlainYearMonth(
                        context,
                        midMonthIsoDate,
                        plainYearMonth.getCalendarId());
            }
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainYearMonthForFormatting});
    }

    public static JSValue toPlainDate(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "toPlainDate");
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
        IsoCalendarDate calendarDateFields =
                plainYearMonth.getIsoDate().toIsoCalendarDate(plainYearMonth.getCalendarId());
        JSObject mergedFields = context.createJSObject();
        mergedFields.set(PropertyKey.fromString("calendar"), new JSString(plainYearMonth.getCalendarId()));
        mergedFields.set(PropertyKey.fromString("year"), JSNumber.of(calendarDateFields.year()));
        mergedFields.set(PropertyKey.fromString("monthCode"), new JSString(calendarDateFields.monthCode()));
        mergedFields.set(PropertyKey.fromString("day"), JSNumber.of(dayOfMonth));
        return TemporalPlainDateConstructor.dateFromFields(context, mergedFields, JSUndefined.INSTANCE);
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "toString");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        String calendarNameOption = TemporalUtils.getCalendarNameOption(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = plainYearMonth.getIsoDate();
        boolean includeReferenceDay;
        if ("iso8601".equals(plainYearMonth.getCalendarId())) {
            includeReferenceDay = "always".equals(calendarNameOption) || "critical".equals(calendarNameOption);
        } else {
            includeReferenceDay = true;
        }
        String result = new IsoDate(isoDate.year(), isoDate.month(), includeReferenceDay ? isoDate.day() : 1).toString();
        if (!includeReferenceDay) {
            result = result.substring(0, result.lastIndexOf('-'));
        }
        result = TemporalUtils.maybeAppendCalendar(result, plainYearMonth.getCalendarId(), calendarNameOption);
        return new JSString(result);
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "until");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return differenceTemporalPlainYearMonth(context, plainYearMonth, args, false);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        context.throwTypeError("Do not use Temporal.PlainYearMonth.prototype.valueOf; use Temporal.PlainYearMonth.prototype.compare for comparison.");
        return JSUndefined.INSTANCE;
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "with");
        if (plainYearMonth == null) {
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

        PropertyKey monthKey = PropertyKey.fromString("month");
        PropertyKey monthCodeKey = PropertyKey.fromString("monthCode");
        PropertyKey yearKey = PropertyKey.fromString("year");
        PropertyKey eraKey = PropertyKey.fromString("era");
        PropertyKey eraYearKey = PropertyKey.fromString("eraYear");

        JSValue monthValue = fields.get(monthKey);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonth = !(monthValue instanceof JSUndefined) && monthValue != null;
        Integer month = null;
        if (hasMonth) {
            month = TemporalUtils.toIntegerThrowOnInfinity(context, monthValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        JSValue monthCodeValue = fields.get(monthCodeKey);
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

        JSValue yearValue = fields.get(yearKey);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYear = !(yearValue instanceof JSUndefined) && yearValue != null;
        Integer year = null;
        if (hasYear) {
            year = TemporalUtils.toIntegerThrowOnInfinity(context, yearValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        String calendarId = plainYearMonth.getCalendarId();
        boolean calendarSupportsEras = !"iso8601".equals(calendarId)
                && !"chinese".equals(calendarId)
                && !"dangi".equals(calendarId);
        boolean hasEra = false;
        boolean hasEraYear = false;
        String era = null;
        Integer eraYear = null;
        if (calendarSupportsEras) {
            JSValue eraValue = fields.get(eraKey);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEra = !(eraValue instanceof JSUndefined) && eraValue != null;
            if (hasEra) {
                era = JSTypeConversions.toString(context, eraValue).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            JSValue eraYearValue = fields.get(eraYearKey);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            hasEraYear = !(eraYearValue instanceof JSUndefined) && eraYearValue != null;
            if (hasEraYear) {
                eraYear = TemporalUtils.toIntegerThrowOnInfinity(context, eraYearValue);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }
        } else if (!"iso8601".equals(calendarId)) {
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
        JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
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
        TemporalParsedMonthCode monthCodeInfo = null;
        if (hasMonthCode) {
            monthCodeInfo = parseMonthCodeForWith(context, monthCode);
            if (context.hasPendingException() || monthCodeInfo == null) {
                return JSUndefined.INSTANCE;
            }
            if ("iso8601".equals(calendarId) && monthCodeInfo.leapMonth()) {
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
        TemporalEraFields eraFields = resolveEraFields(plainYearMonth);

        JSObject mergedFieldsObject = new JSObject(context);
        mergedFieldsObject.set(PropertyKey.fromString("calendar"), new JSString(calendarId));
        if ("iso8601".equals(calendarId)) {
            mergedFieldsObject.set(monthKey, JSNumber.of(calendarDate.month()));
        }
        mergedFieldsObject.set(monthCodeKey, new JSString(calendarMonthCode));
        mergedFieldsObject.set(yearKey, JSNumber.of(calendarDate.year()));
        if (eraFields != null) {
            if (eraFields.era() != null) {
                mergedFieldsObject.set(eraKey, new JSString(eraFields.era()));
            }
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
                mergedFieldsObject.set(monthCodeKey, new JSString(TemporalUtils.monthCode(monthCodeInfo.month())
                        + (monthCodeInfo.leapMonth() ? "L" : "")));
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
        JSValue mergedYearMonthValue = TemporalPlainYearMonthConstructor.yearMonthFromFields(
                context,
                mergedFieldsObject,
                normalizedOptionsObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(mergedYearMonthValue instanceof JSTemporalPlainYearMonth mergedYearMonth)) {
            context.throwTypeError("Temporal error: year argument must be an object.");
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainYearMonthConstructor.createPlainYearMonth(
                context,
                mergedYearMonth.getIsoDate(),
                calendarId);
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "year");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate calendarDate = resolveCalendarDate(plainYearMonth);
        return JSNumber.of(calendarDate.year());
    }

}
