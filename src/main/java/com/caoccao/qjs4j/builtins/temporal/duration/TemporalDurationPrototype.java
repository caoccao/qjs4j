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
import com.caoccao.qjs4j.core.temporal.TemporalError;
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
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.undefined(context);
        }

        TemporalUnit receiverLargestUnit = leftRecord.largestUnitOfDuration();
        TemporalUnit otherLargestUnit = rightRecord.largestUnitOfDuration();
        TemporalUnit largestUnit = receiverLargestUnit.coarserDurationUnit(otherLargestUnit);
        TemporalDuration balanced = TemporalDuration.createBalance(totalNanoseconds, largestUnit);
        TemporalDuration normalized = balanced.normalizeFloat64RepresentableFields();
        if (!TemporalDuration.isDurationRecordTimeRangeValid(normalized)) {
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.undefined(context);
        }
        return JSTemporalDuration.create(context, normalized);
    }

    private static void applyDurationFieldOverrides(JSTemporalDuration duration,
            TemporalDurationDouble durationFieldOverrides) {
        for (TemporalDurationProperty property : TemporalDurationProperty.COMPONENTS) {
            property.defineOverride(duration, durationFieldOverrides);
        }
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
        return TemporalDurationProperty.DAYS.call(context, thisArg, args);
    }

    public static JSValue hours(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDurationProperty.HOURS.call(context, thisArg, args);
    }

    public static JSValue microseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDurationProperty.MICROSECONDS.call(context, thisArg, args);
    }

    public static JSValue milliseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDurationProperty.MILLISECONDS.call(context, thisArg, args);
    }

    public static JSValue minutes(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDurationProperty.MINUTES.call(context, thisArg, args);
    }

    public static JSValue months(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDurationProperty.MONTHS.call(context, thisArg, args);
    }

    public static JSValue nanoseconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDurationProperty.NANOSECONDS.call(context, thisArg, args);
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
                    TemporalError.INVALID_ROUNDING_INCREMENT.message());
            if (context.hasPendingException()) {
                return null;
            }

            roundingMode = TemporalRoundingMode.readRoundOption(context, optionsObject, false);
            if (roundingMode == null) {
                return null;
            }
            smallestUnitText = TemporalUtils.getStringOption(context, optionsObject, "smallestUnit", null);
            if (context.hasPendingException()) {
                return null;
            }
        } else {
            return TemporalError.INVALID_ROUND_TO.reject(context);
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
            return context.rejectRangeError("Temporal error: Must specify either smallestUnit or largestUnit.");
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
            return TemporalError.INVALID_UNIT_ORDER.reject(context);
        }

        if (!canonicalSmallestUnit.isValidIncrement(roundingIncrement)) {
            return TemporalError.INVALID_ROUNDING_INCREMENT.reject(context);
        }

        if (roundingIncrement != 1 && smallestRoundUnit.isDateUnit()
                && largestRoundUnit.rank() < smallestRoundUnit.rank()) {
            return TemporalError.INVALID_ROUNDING_INCREMENT.reject(context);
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
        if (JSTypeChecking.isUndefinedOrMissing(value)) {
            return new TemporalPartialDurationFieldValue(false, 0L);
        }
        double numericValue = JSTypeConversions.toDouble(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        if (!Double.isFinite(numericValue) || numericValue != Math.floor(numericValue)) {
            return TemporalError.EXPECTED_FINITE_INTEGER.reject(context);
        }
        if (numericValue < Long.MIN_VALUE || numericValue > Long.MAX_VALUE) {
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.reject(context);
        }
        return new TemporalPartialDurationFieldValue(true, (long) numericValue);
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = RECEIVER.require(context, thisArg, "round");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || args[0] instanceof JSUndefined) {
            return TemporalError.MISSING_ROUND_TO.undefined(context);
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
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.undefined(context);
        }
        JSTemporalDuration roundedDuration = JSTemporalDuration.create(context, normalizedDurationRecord);
        TemporalDurationDouble durationFieldOverrides = roundComputationResult.durationFieldOverrides();
        if (durationFieldOverrides != null) {
            applyDurationFieldOverrides(roundedDuration, durationFieldOverrides);
        }
        return roundedDuration;
    }

    public static JSValue seconds(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDurationProperty.SECONDS.call(context, thisArg, args);
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
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.undefined(context);
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
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.undefined(context);
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
            JSValue unitValue = optionsObject.get(PropertyKey.UNIT);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (JSTypeChecking.isUndefinedOrMissing(unitValue)) {
                context.throwRangeError("Temporal error: Must specify a totalOf parameter");
                return JSUndefined.INSTANCE;
            }
            unitText = JSTypeConversions.toJavaString(context, unitValue);
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
        return TemporalDurationProperty.WEEKS.call(context, thisArg, args);
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalDuration duration = RECEIVER.require(context, thisArg, "with");
        if (duration == null) {
            return JSUndefined.INSTANCE;
        }
        if (args.length == 0 || !(args[0] instanceof JSObject fields)) {
            return TemporalError.MISSING_PARTIAL_FIELDS.undefined(context);
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
            return TemporalError.MISSING_PARTIAL_FIELDS.undefined(context);
        }
        TemporalDuration newRecord = TemporalDuration.fromComponents(components);
        if (!newRecord.isValid()) {
            return TemporalError.INVALID_DURATION.undefined(context);
        }

        return JSTemporalDuration.create(context, newRecord);
    }

    public static JSValue years(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalDurationProperty.YEARS.call(context, thisArg, args);
    }

}
