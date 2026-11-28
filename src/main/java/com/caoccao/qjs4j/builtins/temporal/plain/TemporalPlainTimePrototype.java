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

package com.caoccao.qjs4j.builtins.temporal.plain;

import com.caoccao.qjs4j.builtins.temporal.TemporalLocaleFormat;
import com.caoccao.qjs4j.builtins.temporal.TemporalReceiver;
import com.caoccao.qjs4j.builtins.temporal.duration.TemporalDurationConstructor;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.TemporalUtils;
import com.caoccao.qjs4j.core.temporal.duration.TemporalDuration;
import com.caoccao.qjs4j.core.temporal.format.TemporalPlainTimeToStringSettings;
import com.caoccao.qjs4j.core.temporal.iso.IsoTime;
import com.caoccao.qjs4j.core.temporal.rounding.TemporalDifferenceSettings;
import com.caoccao.qjs4j.core.temporal.rounding.TemporalRoundSettings;

import java.math.BigInteger;

/**
 * Implementation of Temporal.PlainTime prototype methods.
 */
public final class TemporalPlainTimePrototype {
    private static final BigInteger DAY_NANOSECONDS = TemporalConstants.BI_DAY_NANOSECONDS;
    private static final TemporalReceiver<JSTemporalPlainTime> RECEIVER = TemporalReceiver.PLAIN_TIME;

    private TemporalPlainTimePrototype() {
    }

    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, 1);
    }

    private static JSValue addOrSubtract(JSContext context, JSValue thisArg, JSValue[] args, int sign) {
        JSTemporalPlainTime plainTime = RECEIVER.require(context, thisArg, sign > 0 ? "add" : "subtract");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalDuration durationRecord = TemporalDurationConstructor.toDurationRecord(context,
                JSValue.argumentOrUndefined(args, 0));
        if (durationRecord == null) {
            return JSUndefined.INSTANCE;
        }
        BigInteger durationNanoseconds = durationRecord.dayTimeNanoseconds();
        if (sign < 0) {
            durationNanoseconds = durationNanoseconds.negate();
        }

        BigInteger dayNanoseconds = DAY_NANOSECONDS;
        BigInteger timeNanoseconds = BigInteger.valueOf(plainTime.getIsoTime().totalNanoseconds());
        BigInteger resultNanoseconds = timeNanoseconds.add(durationNanoseconds).remainder(dayNanoseconds);
        if (resultNanoseconds.signum() < 0) {
            resultNanoseconds = resultNanoseconds.add(dayNanoseconds);
        }

        return JSTemporalPlainTime.create(context, IsoTime.createFromNanoseconds(resultNanoseconds.longValue()));
    }

    private static JSValue difference(JSContext context, JSValue thisArg, JSValue[] args, boolean since) {
        return RECEIVER.convertedDifference(context, thisArg, args, since,
                TemporalPlainTimePrototype::differencePlainTime);
    }

    private static JSValue differencePlainTime(JSContext context, JSTemporalPlainTime plainTime,
            JSTemporalPlainTime other, JSValue optionsArg, boolean sinceOperation) {
        TemporalDifferenceSettings differenceSettings = TemporalDifferenceSettings.parse(context, false, optionsArg,
                TemporalUnit.HOUR, TemporalUnit.NANOSECOND, TemporalUnit.NANOSECOND, TemporalUnit.HOUR, false, true);
        if (context.hasPendingException() || differenceSettings == null) {
            return JSUndefined.INSTANCE;
        }

        long leftNanoseconds = plainTime.getIsoTime().totalNanoseconds();
        long rightNanoseconds = other.getIsoTime().totalNanoseconds();
        BigInteger differenceNanoseconds;
        if (sinceOperation) {
            differenceNanoseconds = BigInteger.valueOf(leftNanoseconds - rightNanoseconds);
        } else {
            differenceNanoseconds = BigInteger.valueOf(rightNanoseconds - leftNanoseconds);
        }

        TemporalDuration normalizedDuration = differenceSettings.roundTimeDifference(differenceNanoseconds);
        if (!normalizedDuration.isValid() || !TemporalDuration.isDurationRecordTimeRangeValid(normalizedDuration)) {
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.undefined(context);
        }
        return JSTemporalDuration.create(context, normalizedDuration);
    }

    public static JSValue equals(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.equalTo(context, thisArg, args,
                (left, right) -> left.getIsoTime().compareTo(right.getIsoTime()) == 0);
    }

    private static TemporalPlainTimeToStringSettings getToStringSettings(JSContext context, JSValue optionsValue) {
        JSObject optionsObject = TemporalUtils.toOptionalOptionsObject(context, optionsValue,
                TemporalError.INVALID_OPTIONS.message());
        if (context.hasPendingException()) {
            return null;
        }
        if (optionsObject == null) {
            return TemporalPlainTimeToStringSettings.DEFAULT;
        }

        return TemporalPlainTimeToStringSettings.read(context, optionsObject);
    }

    public static JSValue hour(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.HOUR.read(context, thisArg, RECEIVER);
    }

    public static JSValue microsecond(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.MICROSECOND.read(context, thisArg, RECEIVER);
    }

    public static JSValue millisecond(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.MILLISECOND.read(context, thisArg, RECEIVER);
    }

    public static JSValue minute(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.MINUTE.read(context, thisArg, RECEIVER);
    }

    public static JSValue nanosecond(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.NANOSECOND.read(context, thisArg, RECEIVER);
    }

    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = RECEIVER.require(context, thisArg, "round");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        TemporalRoundSettings roundSettings = TemporalRoundSettings.parseRoundTo(context, args, TemporalUnit.HOUR);
        if (context.hasPendingException() || roundSettings == null) {
            return JSUndefined.INSTANCE;
        }

        long nanos = roundSettings.roundedNanoseconds(plainTime.getIsoTime());
        return JSTemporalPlainTime.create(context, IsoTime.createFromNanoseconds(nanos));
    }

    public static JSValue second(JSContext context, JSValue thisArg, JSValue[] args) {
        return TemporalTimeProperty.SECOND.read(context, thisArg, RECEIVER);
    }

    public static JSValue since(JSContext context, JSValue thisArg, JSValue[] args) {
        return difference(context, thisArg, args, true);
    }

    public static JSValue subtract(JSContext context, JSValue thisArg, JSValue[] args) {
        return addOrSubtract(context, thisArg, args, -1);
    }

    public static JSValue toJSON(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.read(context, thisArg, "toJSON", plainTime -> new JSString(plainTime.getIsoTime().toString()));
    }

    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = RECEIVER.require(context, thisArg, "toLocaleString");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue dateTimeFormat = TemporalLocaleFormat.create(context, args, "dateStyle");
        if (dateTimeFormat == null) {
            return JSUndefined.INSTANCE;
        }
        return JSIntlObject.dateTimeFormatFormat(context, dateTimeFormat, new JSValue[]{plainTime});
    }

    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = RECEIVER.require(context, thisArg, "toString");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSValue optionsValue = JSValue.argumentOrUndefined(args, 0);
        TemporalPlainTimeToStringSettings toStringSettings = getToStringSettings(context, optionsValue);
        if (context.hasPendingException() || toStringSettings == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime roundedTime = IsoTime.createFromNanoseconds(toStringSettings.roundedTime(plainTime.getIsoTime()));
        return new JSString(toStringSettings.format(roundedTime));
    }

    public static JSValue until(JSContext context, JSValue thisArg, JSValue[] args) {
        return difference(context, thisArg, args, false);
    }

    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return RECEIVER.rejectValueOf(context);
    }

    public static JSValue with(JSContext context, JSValue thisArg, JSValue[] args) {
        JSTemporalPlainTime plainTime = RECEIVER.require(context, thisArg, "with");
        if (plainTime == null) {
            return JSUndefined.INSTANCE;
        }
        JSObject fields = TemporalUtils.requirePartialTemporalObject(context, args,
                "Temporal error: Must specify at least one time field.");
        if (fields == null) {
            return JSUndefined.INSTANCE;
        }

        return TemporalPlainTimeConstructor.timeFromFields(context, fields, JSValue.argumentOrUndefined(args, 1),
                plainTime.getIsoTime(), "Temporal error: Must specify at least one time field.");
    }
}
