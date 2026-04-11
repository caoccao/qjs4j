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
 * Implementation of Temporal.PlainDateTime constructor and static methods.
 */
public final class TemporalPlainDateTimeConstructor {
    private static final long MAX_SUPPORTED_EPOCH_DAY = new IsoDate(275760, 9, 13).toEpochDay();
    private static final long MIN_SUPPORTED_EPOCH_DAY = new IsoDate(-271821, 4, 19).toEpochDay();

    private TemporalPlainDateTimeConstructor() {
    }

    private static boolean calendarUsesEras(String calendarId) {
        return "buddhist".equals(calendarId)
                || "coptic".equals(calendarId)
                || "ethioaa".equals(calendarId)
                || "ethiopic".equals(calendarId)
                || "gregory".equals(calendarId)
                || "hebrew".equals(calendarId)
                || "indian".equals(calendarId)
                || "islamic-civil".equals(calendarId)
                || "islamic-tbla".equals(calendarId)
                || "islamic-umalqura".equals(calendarId)
                || "japanese".equals(calendarId)
                || "persian".equals(calendarId)
                || "roc".equals(calendarId);
    }

    private static String canonicalizeEraForCalendar(JSContext context, String calendarId, String era) {
        if (era == null) {
            context.throwRangeError("Temporal error: Invalid era.");
            return null;
        }
        String normalizedEra = era.toLowerCase();
        return switch (calendarId) {
            case "gregory" -> switch (normalizedEra) {
                case "ce", "ad" -> "ce";
                case "bce", "bc" -> "bce";
                default -> invalidEra(context);
            };
            case "japanese" -> switch (normalizedEra) {
                case "ce", "ad" -> "ce";
                case "bce", "bc" -> "bce";
                case "meiji", "taisho", "showa", "heisei", "reiwa" -> normalizedEra;
                default -> invalidEra(context);
            };
            case "roc" -> switch (normalizedEra) {
                case "roc", "minguo" -> "roc";
                case "broc", "before-roc" -> "broc";
                default -> invalidEra(context);
            };
            case "buddhist" -> "be".equals(normalizedEra) ? "be" : invalidEra(context);
            case "coptic" -> "am".equals(normalizedEra) ? "am" : invalidEra(context);
            case "ethioaa" -> "aa".equals(normalizedEra) ? "aa" : invalidEra(context);
            case "ethiopic" -> ("aa".equals(normalizedEra) || "am".equals(normalizedEra))
                    ? normalizedEra
                    : invalidEra(context);
            case "hebrew" -> "am".equals(normalizedEra) ? "am" : invalidEra(context);
            case "indian" -> ("shaka".equals(normalizedEra) || "saka".equals(normalizedEra))
                    ? "shaka"
                    : invalidEra(context);
            case "islamic-civil", "islamic-tbla", "islamic-umalqura" -> switch (normalizedEra) {
                case "ah", "bh" -> normalizedEra;
                default -> invalidEra(context);
            };
            case "persian" -> "ap".equals(normalizedEra) ? "ap" : invalidEra(context);
            default -> invalidEra(context);
        };
    }

    /**
     * Temporal.PlainDateTime.compare(one, two)
     */
    public static JSValue compare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue oneArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue twoArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSTemporalPlainDateTime firstDateTime = toTemporalDateTimeObject(context, oneArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSTemporalPlainDateTime secondDateTime = toTemporalDateTimeObject(context, twoArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of(IsoDateTime.compareIsoDateTime(firstDateTime.getIsoDateTime(), secondDateTime.getIsoDateTime()));
    }

    /**
     * Temporal.PlainDateTime(isoYear, isoMonth, isoDay, hour?, minute?, second?, millisecond?, microsecond?, nanosecond?, calendar?)
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        if (context.getConstructorNewTarget() == null) {
            context.throwTypeError("Method invoked on an object that is not Temporal.PlainDateTime.");
            return JSUndefined.INSTANCE;
        }

        int isoYear = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int isoMonth = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;
        int isoDay = TemporalUtils.toIntegerThrowOnInfinity(context, args.length > 2 ? args[2] : JSUndefined.INSTANCE);
        if (context.hasPendingException()) return JSUndefined.INSTANCE;

        int hour = 0, minute = 0, second = 0, millisecond = 0, microsecond = 0, nanosecond = 0;
        if (args.length > 3 && !(args[3] instanceof JSUndefined)) {
            hour = TemporalUtils.toIntegerThrowOnInfinity(context, args[3]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 4 && !(args[4] instanceof JSUndefined)) {
            minute = TemporalUtils.toIntegerThrowOnInfinity(context, args[4]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 5 && !(args[5] instanceof JSUndefined)) {
            second = TemporalUtils.toIntegerThrowOnInfinity(context, args[5]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 6 && !(args[6] instanceof JSUndefined)) {
            millisecond = TemporalUtils.toIntegerThrowOnInfinity(context, args[6]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 7 && !(args[7] instanceof JSUndefined)) {
            microsecond = TemporalUtils.toIntegerThrowOnInfinity(context, args[7]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }
        if (args.length > 8 && !(args[8] instanceof JSUndefined)) {
            nanosecond = TemporalUtils.toIntegerThrowOnInfinity(context, args[8]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        String calendarId = "iso8601";
        if (args.length > 9 && !(args[9] instanceof JSUndefined)) {
            calendarId = TemporalUtils.validateCalendar(context, args[9]);
            if (context.hasPendingException()) return JSUndefined.INSTANCE;
        }

        if (!IsoDate.isValidIsoDate(isoYear, isoMonth, isoDay)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        if (!IsoTime.isValidTime(hour, minute, second, millisecond, microsecond, nanosecond)) {
            context.throwRangeError("Temporal error: Invalid time");
            return JSUndefined.INSTANCE;
        }

        IsoDate date = new IsoDate(isoYear, isoMonth, isoDay);
        IsoTime time = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
        if (!isValidPlainDateTimeRange(date, time)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }

        IsoDateTime isoDateTime = new IsoDateTime(date, time);
        JSObject resolvedPrototype = TemporalPlainDateConstructor.resolveTemporalPrototype(context, "PlainDateTime");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return createPlainDateTime(context, isoDateTime, calendarId, resolvedPrototype);
    }

    public static JSTemporalPlainDateTime createPlainDateTime(JSContext context, IsoDateTime isoDateTime, String calendarId) {
        JSObject prototype = TemporalPlainDateConstructor.getTemporalPrototype(context, "PlainDateTime");
        return createPlainDateTime(context, isoDateTime, calendarId, prototype);
    }

    static JSTemporalPlainDateTime createPlainDateTime(JSContext context, IsoDateTime isoDateTime, String calendarId, JSObject prototype) {
        JSTemporalPlainDateTime plainDateTime = new JSTemporalPlainDateTime(context, isoDateTime, calendarId);
        if (prototype != null) {
            plainDateTime.setPrototype(prototype);
        }
        return plainDateTime;
    }

    static JSValue dateTimeFromFields(JSContext context, JSObject fields, JSValue options) {
        JSValue calendarValue = fields.get(PropertyKey.fromString("calendar"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSValue dayValue = fields.get(PropertyKey.fromString("day"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasDay = !(dayValue instanceof JSUndefined) && dayValue != null;
        int dayOfMonth = Integer.MIN_VALUE;
        if (hasDay) {
            dayOfMonth = TemporalUtils.toIntegerThrowOnInfinity(context, dayValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        int hour = TemporalUtils.getIntegerField(context, fields, "hour", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int microsecond = TemporalUtils.getIntegerField(context, fields, "microsecond", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int millisecond = TemporalUtils.getIntegerField(context, fields, "millisecond", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int minute = TemporalUtils.getIntegerField(context, fields, "minute", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSValue monthValue = fields.get(PropertyKey.fromString("month"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasMonth = !(monthValue instanceof JSUndefined) && monthValue != null;
        int month = Integer.MIN_VALUE;
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
        ParsedMonthCode parsedMonthCode = null;
        if (hasMonthCode) {
            String monthCodeText;
            if (monthCodeValue instanceof JSString monthCodeString) {
                monthCodeText = monthCodeString.value();
            } else if (monthCodeValue instanceof JSObject) {
                JSValue primitiveMonthCode =
                        JSTypeConversions.toPrimitive(context, monthCodeValue, JSTypeConversions.PreferredType.STRING);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (!(primitiveMonthCode instanceof JSString primitiveMonthCodeString)) {
                    context.throwTypeError("Temporal error: Month code must be string.");
                    return JSUndefined.INSTANCE;
                }
                monthCodeText = primitiveMonthCodeString.value();
            } else {
                context.throwTypeError("Temporal error: Month code must be string.");
                return JSUndefined.INSTANCE;
            }
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            parsedMonthCode = parseMonthCodeSyntax(context, monthCodeText);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        int nanosecond = TemporalUtils.getIntegerField(context, fields, "nanosecond", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        int second = TemporalUtils.getIntegerField(context, fields, "second", 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSValue yearValue = fields.get(PropertyKey.fromString("year"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean hasYear = !(yearValue instanceof JSUndefined) && yearValue != null;
        int year = Integer.MIN_VALUE;
        if (hasYear) {
            year = TemporalUtils.toIntegerThrowOnInfinity(context, yearValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        String calendarId = "iso8601";
        if (!(calendarValue instanceof JSUndefined) && calendarValue != null) {
            calendarId = TemporalUtils.toTemporalCalendarWithISODefault(context, calendarValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        boolean hasEra = false;
        boolean hasEraYear = false;
        String era = null;
        Integer eraYear = null;
        if (calendarUsesEras(calendarId)) {
            JSValue eraValue = fields.get(PropertyKey.fromString("era"));
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

            JSValue eraYearValue = fields.get(PropertyKey.fromString("eraYear"));
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

            if (hasEra != hasEraYear) {
                context.throwTypeError("Temporal error: DateTime argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
            if (!hasYear && hasEra && hasEraYear) {
                String canonicalEra = canonicalizeEraForCalendar(context, calendarId, era);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                year = yearFromEraAndEraYear(calendarId, canonicalEra, eraYear);
                hasYear = true;
            } else if (hasEra && hasEraYear) {
                String canonicalEra = canonicalizeEraForCalendar(context, calendarId, era);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                int expectedYear = yearFromEraAndEraYear(calendarId, canonicalEra, eraYear);
                if (year != expectedYear) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return JSUndefined.INSTANCE;
                }
            }
        }

        if (!hasYear || !hasDay || (!hasMonth && !hasMonthCode)) {
            context.throwTypeError("Temporal error: DateTime argument must be object or string.");
            return JSUndefined.INSTANCE;
        }

        String overflow = TemporalUtils.getOverflowOption(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        String monthCodeText = null;
        if (parsedMonthCode != null) {
            monthCodeText = TemporalUtils.monthCode(parsedMonthCode.month());
            if (parsedMonthCode.leapMonth()) {
                monthCodeText += "L";
            }
        }
        Integer monthFromProperty = hasMonth ? month : null;
        IsoDate resultDate = TemporalCalendarMath.calendarDateToIsoDate(
                context,
                calendarId,
                year,
                monthFromProperty,
                monthCodeText,
                dayOfMonth,
                overflow);
        if (context.hasPendingException() || resultDate == null) {
            return JSUndefined.INSTANCE;
        }

        IsoTime resultTime;
        if ("reject".equals(overflow)) {
            if (!IsoTime.isValidTime(hour, minute, second, millisecond, microsecond, nanosecond)) {
                context.throwRangeError("Temporal error: Invalid time");
                return JSUndefined.INSTANCE;
            }
            resultTime = new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
        } else {
            resultTime = IsoTime.constrain(hour, minute, second, millisecond, microsecond, nanosecond);
        }

        if (!isValidPlainDateTimeRange(resultDate, resultTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        return createPlainDateTime(context, new IsoDateTime(resultDate, resultTime), calendarId);
    }

    static JSValue dateTimeFromString(JSContext context, String input) {
        TemporalParser.ParsedDateTime parsed = TemporalParser.parseDateTimeString(context, input);
        if (parsed == null) {
            return JSUndefined.INSTANCE;
        }
        if (!isValidPlainDateTimeRange(parsed.date(), parsed.time())) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return JSUndefined.INSTANCE;
        }
        return createPlainDateTime(context, new IsoDateTime(parsed.date(), parsed.time()), parsed.calendar());
    }

    /**
     * Temporal.PlainDateTime.from(item, options?)
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue item = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        return toTemporalDateTime(context, item, options);
    }

    private static String invalidEra(JSContext context) {
        context.throwRangeError("Temporal error: Invalid era.");
        return null;
    }

    private static boolean isTemporalPlainDateTimePrototype(JSContext context, JSObject candidate) {
        JSValue temporal = context.getGlobalObject().get(PropertyKey.fromString("Temporal"));
        if (!(temporal instanceof JSObject temporalObject)) {
            return false;
        }
        JSValue constructor = temporalObject.get(PropertyKey.fromString("PlainDateTime"));
        if (!(constructor instanceof JSObject constructorObject)) {
            return false;
        }
        JSValue prototype = constructorObject.get(PropertyKey.PROTOTYPE);
        return prototype == candidate;
    }

    private static boolean isValidPlainDateTimeRange(IsoDate date, IsoTime time) {
        long epochDay = date.toEpochDay();
        if (epochDay < MIN_SUPPORTED_EPOCH_DAY || epochDay > MAX_SUPPORTED_EPOCH_DAY) {
            return false;
        }
        return epochDay != MIN_SUPPORTED_EPOCH_DAY || time.totalNanoseconds() != 0L;
    }

    private static ParsedMonthCode parseMonthCodeSyntax(JSContext context, String monthCode) {
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
        return new ParsedMonthCode(month, leapMonth);
    }

    private static int resolveJapaneseYearFromEra(String era, int eraYear) {
        if ("ce".equals(era)) {
            return eraYear;
        }
        if ("bce".equals(era)) {
            return 1 - eraYear;
        }
        if ("meiji".equals(era)) {
            return 1867 + eraYear;
        }
        if ("taisho".equals(era)) {
            return 1911 + eraYear;
        }
        if ("showa".equals(era)) {
            return 1925 + eraYear;
        }
        if ("heisei".equals(era)) {
            return 1988 + eraYear;
        }
        if ("reiwa".equals(era)) {
            return 2018 + eraYear;
        }
        return eraYear;
    }

    /**
     * ToTemporalDateTime abstract operation.
     */
    public static JSValue toTemporalDateTime(JSContext context, JSValue item, JSValue options) {
        if (item instanceof JSTemporalPlainDateTime plainDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return createPlainDateTime(context, plainDateTime.getIsoDateTime(), plainDateTime.getCalendarId());
        }
        if (item instanceof JSTemporalPlainDate plainDate) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return createPlainDateTime(
                    context,
                    new IsoDateTime(plainDate.getIsoDate(), IsoTime.MIDNIGHT),
                    plainDate.getCalendarId());
        }
        if (item instanceof JSTemporalZonedDateTime zonedDateTime) {
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            IsoDateTime localDateTime = TemporalTimeZone.epochNsToDateTimeInZone(
                    zonedDateTime.getEpochNanoseconds(),
                    zonedDateTime.getTimeZoneId());
            return createPlainDateTime(context, localDateTime, zonedDateTime.getCalendarId());
        }
        if (item instanceof JSObject itemObj) {
            if (isTemporalPlainDateTimePrototype(context, itemObj)) {
                context.throwTypeError("Temporal error: DateTime argument must be object or string.");
                return JSUndefined.INSTANCE;
            }
            return dateTimeFromFields(context, itemObj, options);
        }
        if (item instanceof JSString itemStr) {
            JSValue parsed = dateTimeFromString(context, itemStr.value());
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            TemporalUtils.getOverflowOption(context, options);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return parsed;
        }
        context.throwTypeError("Temporal error: DateTime argument must be object or string.");
        return JSUndefined.INSTANCE;
    }

    public static JSTemporalPlainDateTime toTemporalDateTimeObject(JSContext context, JSValue item) {
        JSValue result = toTemporalDateTime(context, item, JSUndefined.INSTANCE);
        if (context.hasPendingException()) {
            return null;
        }
        return (JSTemporalPlainDateTime) result;
    }

    private static int yearFromEraAndEraYear(String calendarId, String era, int eraYear) {
        return switch (calendarId) {
            case "gregory" -> "bce".equals(era) ? 1 - eraYear : eraYear;
            case "japanese" -> resolveJapaneseYearFromEra(era, eraYear);
            case "roc" -> "broc".equals(era) ? 1 - eraYear : eraYear;
            case "buddhist" -> eraYear;
            case "ethiopic" -> "aa".equals(era) ? eraYear - 5500 : eraYear;
            case "islamic-civil", "islamic-tbla", "islamic-umalqura" -> "bh".equals(era) ? 1 - eraYear : eraYear;
            default -> eraYear;
        };
    }

    private record ParsedMonthCode(int month, boolean leapMonth) {
    }
}
