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

package com.caoccao.qjs4j.builtins.temporal.duration;

import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.duration.TemporalDuration;
import com.caoccao.qjs4j.core.temporal.duration.TemporalDurationDouble;
import com.caoccao.qjs4j.core.temporal.duration.TemporalPartialDurationFieldValue;
import com.caoccao.qjs4j.core.temporal.duration.TemporalRelativeToOption;
import com.caoccao.qjs4j.core.temporal.format.TemporalDurationToStringOptions;
import com.caoccao.qjs4j.core.temporal.rounding.TemporalRoundComputationResult;
import com.caoccao.qjs4j.core.temporal.rounding.TemporalRoundOptions;
import com.caoccao.qjs4j.core.temporal.rounding.TemporalRoundingMode;

import java.math.BigInteger;

/**
 * Implementation of Temporal.Duration prototype methods.
 */
public final class TemporalDurationPrototype {

    private static final BigInteger MAX_ABSOLUTE_TIME_NANOSECONDS = TemporalConstants.MAX_ABSOLUTE_TIME_NANOSECONDS;

    private static final TemporalReceiver<JSTemporalDuration> RECEIVER = TemporalReceiver.DURATION;

    private TemporalDurationPrototype() {
    }

    public static JSValue abs(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = RECEIVER.require(context, thisArg, "abs");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalDuration.create(context, duration.getDuration().abs());
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, 1);
    }

    private static JSValue addOrSubtract(JSContext context, JSValue thisArg, JSValue[] args, int sign) {
        JSTemporalDuration duration = RECEIVER.require(context, thisArg, sign > 0 ? "add" : "subtract");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalDuration rightRecord = TemporalDurationConstructor.toDurationRecord(context,
                JSValue.argumentOrUndefined(args, 0));
        if (rightRecord == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration leftRecord = duration.getDuration();

        if (leftRecord.hasCalendarUnits() || rightRecord.hasCalendarUnits()) {
            context.throwRangeError(
                    "Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return JSUndefined.INSTANCE;
        }

        BigInteger leftNanoseconds = leftRecord.dayTimeNanoseconds();
        BigInteger rightNanoseconds = rightRecord.dayTimeNanoseconds();
        BigInteger signedRightNanoseconds;
        if (sign < 0) {
            signedRightNanoseconds = rightNanoseconds.negate();
        } else {
            signedRightNanoseconds = rightNanoseconds;
        }
        BigInteger totalNanoseconds = leftNanoseconds.add(signedRightNanoseconds);
        if (totalNanoseconds.abs().compareTo(MAX_ABSOLUTE_TIME_NANOSECONDS) > 0) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        TemporalUnit receiverLargestUnit = leftRecord.largestUnitOfDuration();
        TemporalUnit otherLargestUnit = rightRecord.largestUnitOfDuration();
        TemporalUnit largestUnit = receiverLargestUnit.coarserDurationUnit(otherLargestUnit);
        TemporalDuration balanced = TemporalDuration.createBalance(totalNanoseconds, largestUnit);
        TemporalDuration normalized = balanced.normalizeFloat64RepresentableFields();
        if (!TemporalDuration.isDurationRecordTimeRangeValid(normalized)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        return JSTemporalDuration.create(context, normalized);
    }

    private static void applyDurationFieldOverrides(JSTemporalDuration duration,
            TemporalDurationDouble durationFieldOverrides) {
        duration.defineProperty(PropertyKey.fromString("years"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.years())));
        duration.defineProperty(PropertyKey.fromString("months"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.months())));
        duration.defineProperty(PropertyKey.fromString("weeks"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.weeks())));
        duration.defineProperty(PropertyKey.fromString("days"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.days())));
        duration.defineProperty(PropertyKey.fromString("hours"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.hours())));
        duration.defineProperty(PropertyKey.fromString("minutes"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.minutes())));
        duration.defineProperty(PropertyKey.fromString("seconds"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.seconds())));
        duration.defineProperty(PropertyKey.fromString("milliseconds"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.milliseconds())));
        duration.defineProperty(PropertyKey.fromString("microseconds"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.microseconds())));
        duration.defineProperty(PropertyKey.fromString("nanoseconds"),
                PropertyDescriptor.defaultData(JSNumber.of(durationFieldOverrides.nanoseconds())));
    }

    public static JSValue blank(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "blank",
                duration -> duration.getDuration().isBlank() ? JSBoolean.TRUE : JSBoolean.FALSE);
    }

    private static TemporalUnit canonicalizeTemporalDurationUnit(JSContext context, String unitText,
            String optionName) {
        TemporalUnit temporalUnit = TemporalUnit.fromString(unitText).orElse(null);
        if (temporalUnit == null) {
            context.throwRangeError("Temporal error: Invalid " + optionName + ".");
        }
        return temporalUnit;
    }

    public static JSValue days(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "days", duration -> JSNumber.of(duration.getDuration().days()));
    }

    public static JSValue hours(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "hours", duration -> JSNumber.of(duration.getDuration().hours()));
    }

    public static JSValue microseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "microseconds",
                duration -> JSNumber.of(duration.getDuration().microseconds()));
    }

    public static JSValue milliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "milliseconds",
                duration -> JSNumber.of(duration.getDuration().milliseconds()));
    }

    public static JSValue minutes(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "minutes", duration -> JSNumber.of(duration.getDuration().minutes()));
    }

    public static JSValue months(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "months", duration -> JSNumber.of(duration.getDuration().months()));
    }

    public static JSValue nanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "nanoseconds",
                duration -> JSNumber.of(duration.getDuration().nanoseconds()));
    }

    public static JSValue negated(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = RECEIVER.require(context, thisArg, "negated");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        return JSTemporalDuration.create(context, duration.getDuration().negated());
    }

    private static TemporalRoundOptions parseRoundOptions(JSContext context, JSValue roundToArg,
            TemporalDuration durationRecord) {
        String largestUnitText = null;
        String smallestUnitText = null;
        TemporalRelativeToOption relativeToOption = null;
        boolean largestUnitProvided = false;
        long roundingIncrement = 1L;
        TemporalRoundingMode roundingMode = TemporalRoundingMode.HALF_EXPAND;

        if (roundToArg instanceof JSString smallestUnitStringValue) {
            smallestUnitText = smallestUnitStringValue.value();
        } else if (roundToArg instanceof JSObject optionsObject) {
            largestUnitText = TemporalUtils.getStringOption(context, optionsObject, "largestUnit", null);
            if (context.hasPendingException()) {
                return null;
            }
            if (largestUnitText != null) {
                largestUnitProvided = true;
            }

            JSValue relativeToValue = optionsObject.get(PropertyKey.fromString("relativeTo"));
            if (context.hasPendingException()) {
                return null;
            }
            relativeToOption = TemporalRelativeTo.parseRoundRelativeToOption(context, relativeToValue);
            if (context.hasPendingException()) {
                return null;
            }

            roundingIncrement = TemporalUtils.getRoundingIncrementOption(context, optionsObject,
                    "Temporal error: Invalid rounding increment.");
            if (context.hasPendingException()) {
                return null;
            }

            String roundingModeText = TemporalUtils.getStringOption(context, optionsObject, "roundingMode",
                    "halfExpand");
            if (context.hasPendingException() || roundingModeText == null) {
                return null;
            }
            roundingMode = TemporalRoundingMode.fromString(roundingModeText);
            if (roundingMode == null) {
                context.throwRangeError("Temporal error: Invalid rounding mode.");
                return null;
            }

            smallestUnitText = TemporalUtils.getStringOption(context, optionsObject, "smallestUnit", null);
            if (context.hasPendingException()) {
                return null;
            }
        } else {
            context.throwTypeError("Temporal error: roundTo must be an object.");
            return null;
        }

        TemporalUnit canonicalLargestUnit = null;
        if (largestUnitText != null) {
            if (!"auto".equals(largestUnitText)) {
                canonicalLargestUnit = canonicalizeTemporalDurationUnit(context, largestUnitText, "largestUnit");
                if (context.hasPendingException()) {
                    return null;
                }
            }
        }

        TemporalUnit canonicalSmallestUnit = null;
        if (smallestUnitText != null) {
            canonicalSmallestUnit = canonicalizeTemporalDurationUnit(context, smallestUnitText, "smallestUnit");
            if (context.hasPendingException()) {
                return null;
            }
        }

        if (canonicalLargestUnit == null && canonicalSmallestUnit == null && !largestUnitProvided) {
            context.throwRangeError("Temporal error: Must specify either smallestUnit or largestUnit.");
            return null;
        }

        if (canonicalSmallestUnit == null) {
            canonicalSmallestUnit = TemporalUnit.NANOSECOND;
        }
        TemporalUnit smallestRoundUnit = canonicalSmallestUnit;
        TemporalUnit largestRoundUnit;
        if (canonicalLargestUnit == null) {
            largestRoundUnit = smallestRoundUnit.coarserDurationUnit(durationRecord.largestUnitOfDuration());
        } else {
            largestRoundUnit = canonicalLargestUnit;
        }

        if (smallestRoundUnit.rank() < largestRoundUnit.rank()) {
            context.throwRangeError("Temporal error: smallestUnit must be smaller than largestUnit.");
            return null;
        }

        if (!canonicalSmallestUnit.isValidIncrement(roundingIncrement)) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return null;
        }

        if (roundingIncrement != 1 && smallestRoundUnit.isDateUnit()
                && largestRoundUnit.rank() < smallestRoundUnit.rank()) {
            context.throwRangeError("Temporal error: Invalid rounding increment.");
            return null;
        }

        return new TemporalRoundOptions(smallestRoundUnit, largestRoundUnit, roundingIncrement, roundingMode,
                relativeToOption);
    }

    private static TemporalPartialDurationFieldValue readPartialDurationField(JSContext context, JSObject fields,
            PropertyKey fieldKey) {
        JSValue value = fields.get(fieldKey);
        if (context.hasPendingException()) {
            return null;
        }
        if (value instanceof JSUndefined || value == null) {
            return new TemporalPartialDurationFieldValue(false, 0L);
        }
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return null;
        }
        if (!Double.isFinite(numericValue) || numericValue != Math.floor(numericValue)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return null;
        }
        if (numericValue < Long.MIN_VALUE || numericValue > Long.MAX_VALUE) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return null;
        }
        return new TemporalPartialDurationFieldValue(true, (long) numericValue);
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = RECEIVER.require(context, thisArg, "round");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            context.throwTypeError("Temporal error: Must specify a roundTo parameter.");
            return JSUndefined.INSTANCE;
        }

        TemporalRoundOptions roundOptions = parseRoundOptions(context, args[0], duration.getDuration());
        if (context.hasPendingException() || roundOptions == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalRelativeToOption relativeToOption = roundOptions.relativeToOption();

        TemporalDuration durationRecord = duration.getDuration();
        boolean requiresRelativeTo = durationRecord.hasCalendarUnits()
                || roundOptions.smallestUnit().requiresRelativeTo() || roundOptions.largestUnit().requiresRelativeTo();
        if (requiresRelativeTo && relativeToOption == null) {
            context.throwRangeError(
                    "Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return JSUndefined.INSTANCE;
        }

        TemporalRoundComputationResult roundComputationResult;
        if (relativeToOption == null) {
            roundComputationResult = TemporalDurationArithmetic.roundWithoutRelativeTo(context, durationRecord,
                    roundOptions);
        } else {
            TemporalDuration roundedDurationRecord = TemporalDurationArithmetic.roundWithRelativeTo(context,
                    durationRecord, roundOptions, relativeToOption);
            roundComputationResult = new TemporalRoundComputationResult(roundedDurationRecord, null);
        }
        if (context.hasPendingException() || roundComputationResult == null
                || roundComputationResult.durationRecord() == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration normalizedDurationRecord = roundComputationResult.durationRecord()
                .normalizeFloat64RepresentableFields();
        if (!normalizedDurationRecord.isValid()
                || !TemporalDuration.isDurationRecordTimeRangeValid(normalizedDurationRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }
        JSTemporalDuration roundedDuration = JSTemporalDuration.create(context, normalizedDurationRecord);
        TemporalDurationDouble durationFieldOverrides = roundComputationResult.durationFieldOverrides();
        if (durationFieldOverrides != null) {
            applyDurationFieldOverrides(roundedDuration, durationFieldOverrides);
        }
        return roundedDuration;
    }

    public static JSValue seconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "seconds", duration -> JSNumber.of(duration.getDuration().seconds()));
    }

    public static JSValue sign(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "sign", duration -> JSNumber.of(duration.getDuration().sign()));
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "toJSON", duration -> new JSString(duration.getDuration().toString()));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = RECEIVER.require(context, thisArg, "toLocaleString");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue locales = JSValue.argumentOrUndefined(args, 0);
        JSValue options = JSValue.argumentOrUndefined(args, 1);
        JSValue durationFormat = JSIntlObject.createDurationFormat(context, null, new JSValue[]{locales, options});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.durationFormatFormat(context, durationFormat, new JSValue[]{duration});
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = RECEIVER.require(context, thisArg, "toString");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue optionsValue = JSValue.argumentOrUndefined(args, 0);
        TemporalDurationToStringOptions options = TemporalDurationToStringOptions.read(context, optionsValue);
        if (context.hasPendingException() || options == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = duration.getDuration();
        BigInteger dayTimeNanoseconds = durationRecord.dayTimeNanoseconds();
        BigInteger incrementNanoseconds = BigInteger.valueOf(options.roundingIncrementNanoseconds());
        BigInteger roundedDayTimeNanoseconds = options.roundingMode()
                .roundBigIntegerToIncrementSigned(dayTimeNanoseconds, incrementNanoseconds);
        if (roundedDayTimeNanoseconds.abs().compareTo(MAX_ABSOLUTE_TIME_NANOSECONDS) > 0) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        TemporalUnit largestExistingDayTimeUnit = durationRecord.largestDayTimeUnit();
        TemporalUnit smallestBalancingUnit = options.smallestUnit();
        TemporalUnit largestBalancingUnit = largestExistingDayTimeUnit.coarserDurationUnit(smallestBalancingUnit);
        TemporalDuration balancedDayTime = TemporalDuration.createBalance(roundedDayTimeNanoseconds,
                largestBalancingUnit);
        TemporalDuration roundedDurationRecord = new TemporalDuration(durationRecord.years(), durationRecord.months(),
                durationRecord.weeks(), balancedDayTime.days(), balancedDayTime.hours(), balancedDayTime.minutes(),
                balancedDayTime.seconds(), balancedDayTime.milliseconds(), balancedDayTime.microseconds(),
                balancedDayTime.nanoseconds());
        if (!roundedDurationRecord.isValid()
                || !TemporalDuration.isDurationRecordTimeRangeValid(roundedDurationRecord)) {
            context.throwRangeError("Temporal error: Duration field out of range.");
            return JSUndefined.INSTANCE;
        }

        String formattedDuration = roundedDurationRecord.formatWithPrecision(options);
        return new JSString(formattedDuration);
    }

    public static JSValue total(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = RECEIVER.require(context, thisArg, "total");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined || args[0] == null) {
            context.throwTypeError("Temporal error: Must specify a totalOf parameter");
            return JSUndefined.INSTANCE;
        }

        JSValue totalOfValue = args[0];
        TemporalRelativeToOption relativeToOption = null;
        String unitText;

        if (totalOfValue instanceof JSString unitStringValue) {
            unitText = unitStringValue.value();
        } else {
            if (!(totalOfValue instanceof JSObject optionsObject)) {
                context.throwTypeError("Temporal error: totalOf must be an object.");
                return JSUndefined.INSTANCE;
            }
            JSValue relativeToValue = optionsObject.get(PropertyKey.fromString("relativeTo"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            relativeToOption = TemporalRelativeTo.parseRoundRelativeToOption(context, relativeToValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSValue unitValue = optionsObject.get(PropertyKey.fromString("unit"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (unitValue instanceof JSUndefined || unitValue == null) {
                context.throwRangeError("Temporal error: Must specify a totalOf parameter");
                return JSUndefined.INSTANCE;
            }
            unitText = JSTypeConversions.toString(context, unitValue).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        TemporalUnit unit = canonicalizeTemporalDurationUnit(context, unitText, "unit");
        if (context.hasPendingException() || unit == null) {
            return JSUndefined.INSTANCE;
        }

        TemporalDuration durationRecord = duration.getDuration();
        boolean requiresRelativeTo = durationRecord.hasCalendarUnits() || unit.requiresRelativeTo();
        if (requiresRelativeTo && relativeToOption == null) {
            context.throwRangeError(
                    "Temporal error: A starting point is required for years, months, or weeks arithmetic.");
            return JSUndefined.INSTANCE;
        }

        double totalValue;
        if (relativeToOption == null) {
            totalValue = TemporalDurationArithmetic.totalWithoutRelativeTo(durationRecord, unit);
        } else {
            totalValue = TemporalDurationArithmetic.totalWithRelativeTo(context, durationRecord, unit,
                    relativeToOption);
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSNumber.of(totalValue);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.rejectValueOf(context);
    }

    public static JSValue weeks(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "weeks", duration -> JSNumber.of(duration.getDuration().weeks()));
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = RECEIVER.require(context, thisArg, "with");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }

        long[] components = duration.getDuration().toComponents();
        boolean any = false;
        for (TemporalUnit unit : TemporalUnit.DURATION_PROPERTY_ORDER) {
            TemporalPartialDurationFieldValue field = readPartialDurationField(context, fields,
                    unit.durationProperty());
            if (field == null) {
                return JSUndefined.INSTANCE;
            }
            if (field.present()) {
                any = true;
                components[unit.rank()] = field.value();
            }
        }
        if (!any) {
            context.throwTypeError("Temporal error: Argument to with() must contain some date/time fields.");
            return JSUndefined.INSTANCE;
        }
        TemporalDuration newRecord = TemporalDuration.fromComponents(components);
        if (!newRecord.isValid()) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return JSUndefined.INSTANCE;
        }

        return JSTemporalDuration.create(context, newRecord);
    }

    public static JSValue years(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "years", duration -> JSNumber.of(duration.getDuration().years()));
    }

}
