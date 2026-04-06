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
import com.caoccao.qjs4j.core.temporal.TemporalDuration;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;

/**
 * Implementation of Temporal.PlainYearMonth prototype methods.
 */
public final class TemporalPlainYearMonthPrototype {
    private static final String DIFFERENCE_LARGEST_UNIT_OPTION = "largestUnit";
    private static final String DIFFERENCE_ROUNDING_INCREMENT_OPTION = "roundingIncrement";
    private static final String DIFFERENCE_ROUNDING_MODE_OPTION = "roundingMode";
    private static final String DIFFERENCE_SMALLEST_UNIT_OPTION = "smallestUnit";
    private static final long MAX_SUPPORTED_EPOCH_DAY = new IsoDate(275760, 9, 13).toEpochDay();
    private static final long MIN_SUPPORTED_EPOCH_DAY = new IsoDate(-271821, 4, 19).toEpochDay();
    private static final long TEMPORAL_MAX_ROUNDING_INCREMENT = 1_000_000_000L;
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

        if (!IsoDate.isValidIsoDate(normalizedYear, normalizedMonth, normalizedDay)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        return new IsoDate(normalizedYear, normalizedMonth, normalizedDay);
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

        IsoDate originalDate = plainYearMonth.getIsoDate();
        if (!IsoDate.isValidIsoDate(originalDate.year(), originalDate.month(), originalDate.day())) {
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

        IsoDate resultDate = addDateDurationToPlainYearMonth(
                context,
                originalDate,
                durationRecord.years(),
                durationRecord.months(),
                overflow);
        if (context.hasPendingException() || resultDate == null) {
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainYearMonthConstructor.createPlainYearMonth(context, resultDate, plainYearMonth.getCalendarId());
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
        return switch (unitText) {
            case UNIT_YEAR, "years" -> UNIT_YEAR;
            case UNIT_MONTH, "months" -> UNIT_MONTH;
            default -> null;
        };
    }

    private static JSTemporalPlainYearMonth checkReceiver(JSContext context, JSValue thisArg, String methodName) {
        if (!(thisArg instanceof JSTemporalPlainYearMonth plainYearMonth)) {
            context.throwTypeError("Method " + TYPE_NAME + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return plainYearMonth;
    }

    private static IsoDate createDifferenceIsoDate(JSContext context, JSTemporalPlainYearMonth plainYearMonth) {
        IsoDate isoDate = plainYearMonth.getIsoDate();
        IsoDate differenceIsoDate = new IsoDate(isoDate.year(), isoDate.month(), 1);
        if (!isDifferenceDateWithinSupportedRange(differenceIsoDate)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return differenceIsoDate;
    }

    private static DurationYearMonthFields createDurationFieldsFromTotalMonths(long totalMonthsDifference, String largestUnit) {
        if (UNIT_YEAR.equals(largestUnit)) {
            long years = totalMonthsDifference / 12L;
            long months = totalMonthsDifference % 12L;
            return new DurationYearMonthFields(years, months);
        }
        return new DurationYearMonthFields(0L, totalMonthsDifference);
    }

    public static JSValue daysInMonth(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "daysInMonth");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        IsoDate isoDate = plainYearMonth.getIsoDate();
        return JSNumber.of(IsoDate.daysInMonth(isoDate.year(), isoDate.month()));
    }

    public static JSValue daysInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "daysInYear");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(IsoDate.daysInYear(plainYearMonth.getIsoDate().year()));
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
        DifferenceSettings differenceSettings = getDifferenceSettings(context, sinceOperation, optionsArgument);
        if (context.hasPendingException() || differenceSettings == null) {
            return JSUndefined.INSTANCE;
        }

        IsoDate thisIsoDate = plainYearMonth.getIsoDate();
        IsoDate otherIsoDate = otherPlainYearMonth.getIsoDate();
        if (thisIsoDate.year() == otherIsoDate.year() && thisIsoDate.month() == otherIsoDate.month()) {
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

        long totalMonthsDifference = (long) (otherDifferenceDate.year() - thisDifferenceDate.year()) * 12L
                + (otherDifferenceDate.month() - thisDifferenceDate.month());
        DurationYearMonthFields durationFields = createDurationFieldsFromTotalMonths(
                totalMonthsDifference,
                differenceSettings.largestUnit());
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
        return JSUndefined.INSTANCE;
    }

    public static JSValue eraYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "eraYear");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return JSUndefined.INSTANCE;
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

    private static DifferenceSettings getDifferenceSettings(
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

        return new DifferenceSettings(largestUnit, smallestUnit, roundingIncrement, roundingMode);
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
        if (IsoDate.isLeapYear(plainYearMonth.getIsoDate().year())) {
            return JSBoolean.TRUE;
        }
        return JSBoolean.FALSE;
    }

    private static boolean isDifferenceDateWithinSupportedRange(IsoDate differenceDate) {
        long differenceEpochDay = differenceDate.toEpochDay();
        return differenceEpochDay >= MIN_SUPPORTED_EPOCH_DAY && differenceEpochDay <= MAX_SUPPORTED_EPOCH_DAY;
    }

    private static boolean isValidDifferenceRoundingMode(String roundingMode) {
        return "ceil".equals(roundingMode)
                || "floor".equals(roundingMode)
                || "trunc".equals(roundingMode)
                || "expand".equals(roundingMode)
                || "halfExpand".equals(roundingMode)
                || "halfTrunc".equals(roundingMode)
                || "halfEven".equals(roundingMode)
                || "halfCeil".equals(roundingMode)
                || "halfFloor".equals(roundingMode);
    }

    private static boolean isYearMonthUnit(String unit) {
        return UNIT_YEAR.equals(unit) || UNIT_MONTH.equals(unit);
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
        return JSNumber.of(plainYearMonth.getIsoDate().month());
    }

    public static JSValue monthCode(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "monthCode");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(TemporalUtils.monthCode(plainYearMonth.getIsoDate().month()));
    }

    public static JSValue monthsInYear(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "monthsInYear");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(12);
    }

    private static String negateRoundingMode(String roundingMode) {
        return switch (roundingMode) {
            case "ceil" -> "floor";
            case "floor" -> "ceil";
            case "halfCeil" -> "halfFloor";
            case "halfFloor" -> "halfCeil";
            default -> roundingMode;
        };
    }

    private static MonthCodeInfo parseMonthCodeForWith(JSContext context, String monthCode) {
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
        return new MonthCodeInfo(month, leapMonth);
    }

    public static JSValue referenceISODay(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "referenceISODay");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainYearMonth.getIsoDate().day());
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

    private static DurationYearMonthFields roundRelativeYearMonthDuration(
            JSContext context,
            DurationYearMonthFields durationFields,
            long destinationMonthsDifference,
            IsoDate originDifferenceDate,
            DifferenceSettings differenceSettings) {
        long years = durationFields.years();
        long months = durationFields.months();
        long increment = differenceSettings.roundingIncrement();
        long sign = Long.signum(destinationMonthsDifference);
        if (sign == 0L) {
            return DurationYearMonthFields.ZERO;
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
        return new DurationYearMonthFields(roundedYears, roundedMonths);
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
        return switch (unit) {
            case UNIT_YEAR -> 0;
            case UNIT_MONTH -> 1;
            default -> 2;
        };
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
        JSValue dateTimeFormat = JSIntlObject.createDateTimeFormat(
                context,
                null,
                new JSValue[]{locales, options});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainYearMonth});
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
        IsoDate isoDate = plainYearMonth.getIsoDate();
        if (dayOfMonth < 1) {
            dayOfMonth = 1;
        }
        int daysInMonth = IsoDate.daysInMonth(isoDate.year(), isoDate.month());
        if (dayOfMonth > daysInMonth) {
            dayOfMonth = daysInMonth;
        }
        if (!IsoDate.isValidIsoDate(isoDate.year(), isoDate.month(), dayOfMonth)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        return TemporalPlainDateConstructor.createPlainDate(context,
                new IsoDate(isoDate.year(), isoDate.month(), dayOfMonth), plainYearMonth.getCalendarId());
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
        boolean includeReferenceDay = !"never".equals(calendarNameOption)
                && (!"auto".equals(calendarNameOption) || !"iso8601".equals(plainYearMonth.getCalendarId()));
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

        IsoDate originalDate = plainYearMonth.getIsoDate();

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

        if (!hasMonth && !hasMonthCode && !hasYear) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }
        if (month < 1) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException() || overflow == null) {
            return JSUndefined.INSTANCE;
        }

        if (hasMonthCode) {
            MonthCodeInfo monthCodeInfo = parseMonthCodeForWith(context, monthCode);
            if (context.hasPendingException() || monthCodeInfo == null) {
                return JSUndefined.INSTANCE;
            }
            if (monthCodeInfo.leapMonth()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            if (hasMonth && month != monthCodeInfo.month()) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            month = monthCodeInfo.month();
        }

        int referenceDay = originalDate.day();
        IsoDate resultDate;
        if ("reject".equals(overflow)) {
            if (!TemporalPlainYearMonthConstructor.isValidIsoYearMonth(year, month)
                    || !IsoDate.isValidIsoDate(year, month, referenceDay)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
            resultDate = new IsoDate(year, month, referenceDay);
        } else {
            resultDate = TemporalUtils.constrainIsoDate(year, month, referenceDay);
            if (!TemporalPlainYearMonthConstructor.isValidIsoYearMonth(resultDate.year(), resultDate.month())) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return JSUndefined.INSTANCE;
            }
        }

        long epochDay = resultDate.toEpochDay();
        if (epochDay < MIN_SUPPORTED_EPOCH_DAY || epochDay > MAX_SUPPORTED_EPOCH_DAY) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainYearMonthConstructor.createPlainYearMonth(context,
                resultDate, plainYearMonth.getCalendarId());
    }

    public static JSValue year(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainYearMonth plainYearMonth = checkReceiver(context, thisArg, "year");
        if (plainYearMonth == null) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(plainYearMonth.getIsoDate().year());
    }

    private record DifferenceSettings(String largestUnit, String smallestUnit, long roundingIncrement,
                                      String roundingMode) {
    }

    private record DurationYearMonthFields(long years, long months) {
        private static final DurationYearMonthFields ZERO = new DurationYearMonthFields(0L, 0L);
    }

    private record MonthCodeInfo(int month, boolean leapMonth) {
    }
}
