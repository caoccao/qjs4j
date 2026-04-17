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

package com.caoccao.qjs4j.core.temporal;

import com.caoccao.qjs4j.core.*;

import java.util.Locale;

/**
 * Shared utilities for Temporal types.
 */
public final class TemporalUtils {
    private static final int[] CHINESE_LUNAR_YEAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ae0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
            0x14b63
    };
    private static final int[] CHINESE_LUNAR_YEAR_INFO_EXTENSION_2051_TO_2100 = {
            0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, 0x092e0,
            0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, 0x092d0,
            0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, 0x0b273,
            0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, 0x0e968,
            0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, 0x0d520
    };
    private static final int[] DANGI_LUNAR_YEAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x0da95, 0x0b550, 0x056a0, 0x0ada2, 0x095d0, 0x04bb7,
            0x049b0, 0x0a4b0, 0x0b4b5, 0x06a90, 0x0ad40, 0x0bb54, 0x02b60, 0x095b0, 0x05372, 0x04970,
            0x06566, 0x0e4a0, 0x0ea50, 0x16a95, 0x05b50, 0x02b60, 0x18ae3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b690, 0x056d0, 0x125b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0d557,
            0x0b4a0, 0x0b550, 0x15555, 0x04db0, 0x025b0, 0x18573, 0x052b0, 0x0a9b8, 0x06950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05270, 0x07263, 0x0d950, 0x06b57, 0x056a0,
            0x09ad0, 0x04dd5, 0x04ae0, 0x0a4e0, 0x0d4d4, 0x0d250, 0x0d598, 0x0b540, 0x0d6a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a9b4, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0b756, 0x02b60, 0x095b0,
            0x04b75, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06d98, 0x05ad0, 0x02b60, 0x096e5, 0x092e0,
            0x0c960, 0x0e954, 0x0d4a0, 0x0da50, 0x07552, 0x056c0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x1b4a3, 0x0b550, 0x055d9, 0x04ba0, 0x0a5b0, 0x09575, 0x052b0, 0x0a950,
            0x0b954, 0x06aa0, 0x0ad50, 0x06b52, 0x04b60, 0x0a6e6, 0x0a570, 0x05270, 0x06a65, 0x0d930,
            0x05aa0, 0x0b6a3, 0x096d0, 0x04afb, 0x04ae0, 0x0a4d0, 0x1d0d6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b6a0, 0x096d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0b250, 0x1b255, 0x06d40, 0x0ada0,
            0x18b63
    };
    private static final String[] SUPPORTED_CALENDAR_IDENTIFIERS = {
            "buddhist", "chinese", "coptic", "dangi", "ethioaa", "ethiopic",
            "gregory", "hebrew", "indian", "islamic-civil", "islamic-tbla",
            "islamic-umalqura", "iso8601", "japanese", "persian", "roc"
    };

    private TemporalUtils() {
    }

    private static boolean canParseCalendarLikeString(JSContext context, String calendarLikeText) {
        JSValue originalPendingException = context.getPendingException();
        String calendarLikeBaseText = calendarLikeText;
        int annotationStart = calendarLikeBaseText.indexOf('[');
        if (annotationStart >= 0) {
            calendarLikeBaseText = calendarLikeBaseText.substring(0, annotationStart);
        }

        if (TemporalParser.parseDateString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (TemporalParser.parseDateTimeString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (TemporalParser.parseYearMonthString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (TemporalParser.parseMonthDayString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (TemporalParser.parseTimeString(context, calendarLikeBaseText) != null && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (calendarLikeBaseText.length() == 5
                && Character.isDigit(calendarLikeBaseText.charAt(0))
                && Character.isDigit(calendarLikeBaseText.charAt(1))
                && calendarLikeBaseText.charAt(2) == '-'
                && Character.isDigit(calendarLikeBaseText.charAt(3))
                && Character.isDigit(calendarLikeBaseText.charAt(4))
                && TemporalParser.parseMonthDayString(context, "--" + calendarLikeBaseText) != null
                && !context.hasPendingException()) {
            return true;
        }
        context.clearPendingException();

        if (originalPendingException != null) {
            context.setPendingException(originalPendingException);
        }
        return false;
    }

    private static String canonicalizeCalendarIdentifier(String calendarIdentifier) {
        if (calendarIdentifier == null) {
            return null;
        }
        String normalizedCalendarIdentifier = calendarIdentifier.toLowerCase(Locale.ROOT);
        if ("islamicc".equals(normalizedCalendarIdentifier)) {
            return "islamic-civil";
        }
        if ("ethiopic-amete-alem".equals(normalizedCalendarIdentifier)) {
            return "ethioaa";
        }
        if ("gregorian".equals(normalizedCalendarIdentifier)) {
            return "gregory";
        }
        return normalizedCalendarIdentifier;
    }

    /**
     * Generic receiver type check for Temporal prototype methods.
     * Returns the cast value, or null after throwing TypeError.
     */
    @SuppressWarnings("unchecked")
    public static <T> T checkReceiver(JSContext context, JSValue thisArg, Class<T> expectedType, String typeName, String methodName) {
        if (!expectedType.isInstance(thisArg)) {
            context.throwTypeError("Method " + typeName + ".prototype." + methodName + " called on incompatible receiver");
            return null;
        }
        return (T) thisArg;
    }

    public static String firstCalendarAnnotation(String text) {
        int annotationStart = text.indexOf('[');
        while (annotationStart >= 0) {
            int annotationEnd = text.indexOf(']', annotationStart);
            if (annotationEnd <= annotationStart) {
                return null;
            }
            String annotationContent = text.substring(annotationStart + 1, annotationEnd);
            if (!annotationContent.isEmpty() && annotationContent.charAt(0) == '!') {
                annotationContent = annotationContent.substring(1);
            }
            int equalSignIndex = annotationContent.indexOf('=');
            if (equalSignIndex > 0) {
                String annotationKey = annotationContent.substring(0, equalSignIndex);
                if ("u-ca".equals(annotationKey)) {
                    return annotationContent.substring(equalSignIndex + 1);
                }
            }
            annotationStart = text.indexOf('[', annotationEnd + 1);
        }
        return null;
    }

    /**
     * Gets a calendar name display option from options.
     * Returns "auto", "always", "never", or "critical".
     */
    public static String getCalendarNameOption(JSContext context, JSValue options) {
        if (options instanceof JSUndefined || options == null) {
            return "auto";
        }
        if (!(options instanceof JSObject optionsObj)) {
            context.throwTypeError("Temporal error: Option must be object: options.");
            return null;
        }
        String calendarNameOption = getStringOption(context, optionsObj, "calendarName", "auto");
        if (context.hasPendingException()) {
            return null;
        }
        if (!"auto".equals(calendarNameOption)
                && !"always".equals(calendarNameOption)
                && !"never".equals(calendarNameOption)
                && !"critical".equals(calendarNameOption)) {
            context.throwRangeError("Temporal error: Invalid calendarName option: " + calendarNameOption);
            return null;
        }
        return calendarNameOption;
    }

    /**
     * Gets an integer field from a JSObject, returning the default if undefined.
     */
    public static int getIntegerField(JSContext context, JSObject sourceObject, String optionKey, int defaultValue) {
        JSValue value = sourceObject.get(PropertyKey.fromString(optionKey));
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
        if (value instanceof JSUndefined || value == null) {
            return defaultValue;
        }
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
        if (!Double.isFinite(numericValue)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Integer.MIN_VALUE;
        }
        return (int) numericValue;
    }

    private static Integer getLunisolarYearInfo(String calendarId, int calendarYear) {
        if ("dangi".equals(calendarId)) {
            if (calendarYear < 1900 || calendarYear > 1900 + DANGI_LUNAR_YEAR_INFO.length - 1) {
                return null;
            }
            return DANGI_LUNAR_YEAR_INFO[calendarYear - 1900];
        }
        if (calendarYear >= 1900 && calendarYear <= 1900 + CHINESE_LUNAR_YEAR_INFO.length - 1) {
            return CHINESE_LUNAR_YEAR_INFO[calendarYear - 1900];
        }
        if (calendarYear >= 2051 && calendarYear <= 2100) {
            return CHINESE_LUNAR_YEAR_INFO_EXTENSION_2051_TO_2100[calendarYear - 2051];
        }
        return null;
    }

    /**
     * Gets the overflow option from an options object.
     * Returns "constrain" or "reject".
     */
    public static String getOverflowOption(JSContext context, JSValue options) {
        if (options instanceof JSUndefined || options == null) {
            return "constrain";
        }
        if (!(options instanceof JSObject optionsObj)) {
            context.throwTypeError("Temporal error: Option must be object: options.");
            return null;
        }
        String overflow = getStringOption(context, optionsObj, "overflow", "constrain");
        if (context.hasPendingException()) {
            return null;
        }
        if (!"constrain".equals(overflow) && !"reject".equals(overflow)) {
            context.throwRangeError("Temporal error: Invalid overflow option: " + overflow);
            return null;
        }
        return overflow;
    }

    /**
     * Gets a string option from an options object.
     */
    public static String getStringOption(JSContext context, JSObject options, String optionKey, String defaultValue) {
        JSValue value = options.get(PropertyKey.fromString(optionKey));
        if (context.hasPendingException()) {
            return null;
        }
        if (value instanceof JSUndefined || value == null) {
            return defaultValue;
        }
        JSString stringValue = JSTypeConversions.toString(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        return stringValue.value();
    }

    private static boolean isSupportedCalendarIdentifier(String calendarIdentifier) {
        if (calendarIdentifier == null) {
            return false;
        }
        String canonicalCalendarIdentifier = canonicalizeCalendarIdentifier(calendarIdentifier);
        for (String supportedCalendarIdentifier : SUPPORTED_CALENDAR_IDENTIFIERS) {
            if (supportedCalendarIdentifier.equals(canonicalCalendarIdentifier)) {
                return true;
            }
        }
        return false;
    }

    public static int islamicDaysBeforeYear(int islamicYear) {
        return (int) (354L * (islamicYear - 1L) + Math.floorDiv(11L * islamicYear + 3L, 30L));
    }

    /**
     * Returns true if firstDate surpasses secondDate in the direction indicated by sign.
     * Positive sign means firstDate is later; negative sign means firstDate is earlier.
     */
    public static boolean isoDateSurpasses(int sign, IsoDate firstDate, IsoDate secondDate) {
        return sign * firstDate.compareTo(secondDate) > 0;
    }

    public static int lunisolarLeapMonth(String calendarId, int calendarYear) {
        Integer yearInfo = getLunisolarYearInfo(calendarId, calendarYear);
        if (yearInfo == null) {
            return 0;
        }
        return yearInfo & 0x0F;
    }

    public static int lunisolarLeapMonthDays(String calendarId, int calendarYear) {
        int leapMonth = lunisolarLeapMonth(calendarId, calendarYear);
        if (leapMonth == 0) {
            return 0;
        }
        Integer yearInfo = getLunisolarYearInfo(calendarId, calendarYear);
        if (yearInfo == null) {
            return 0;
        }
        return (yearInfo & 0x10000) != 0 ? 30 : 29;
    }

    public static int lunisolarMaxYear(String calendarId) {
        if ("dangi".equals(calendarId)) {
            return 1900 + DANGI_LUNAR_YEAR_INFO.length - 1;
        }
        return 2100;
    }

    public static int lunisolarMonthDays(String calendarId, int calendarYear, int calendarMonth) {
        Integer yearInfo = getLunisolarYearInfo(calendarId, calendarYear);
        if (yearInfo == null) {
            return 0;
        }
        int monthMask = 0x10000 >> calendarMonth;
        return (yearInfo & monthMask) != 0 ? 30 : 29;
    }

    public static int lunisolarYearDays(String calendarId, int calendarYear) {
        Integer yearInfo = getLunisolarYearInfo(calendarId, calendarYear);
        if (yearInfo == null) {
            return 0;
        }
        int totalDays = 348;
        int monthInfoMask = 0x8000;
        for (int monthIndex = 0; monthIndex < 12; monthIndex++) {
            if ((yearInfo & monthInfoMask) != 0) {
                totalDays++;
            }
            monthInfoMask >>= 1;
        }
        return totalDays + lunisolarLeapMonthDays(calendarId, calendarYear);
    }

    /**
     * Appends calendar annotation to string if needed.
     */
    public static String maybeAppendCalendar(String dateTimeString, String calendarId, String calendarNameOption) {
        switch (calendarNameOption) {
            case "never":
                return dateTimeString;
            case "always":
                return dateTimeString + "[u-ca=" + calendarId + "]";
            case "critical":
                return dateTimeString + "[!u-ca=" + calendarId + "]";
            case "auto":
            default:
                if ("iso8601".equals(calendarId)) {
                    return dateTimeString;
                }
                return dateTimeString + "[u-ca=" + calendarId + "]";
        }
    }

    /**
     * Formats a month code in "M01"..."M12" format.
     */
    public static String monthCode(int month) {
        return String.format(Locale.ROOT, "M%02d", month);
    }

    public static int toArithmeticPersianYear(int persianYear) {
        if (persianYear <= 0) {
            return persianYear - 1;
        }
        return persianYear;
    }

    /**
     * Converts a JSValue to a finite integer or throws a RangeError.
     * Returns Integer.MIN_VALUE if a pending exception was set.
     */
    public static int toIntegerThrowOnInfinity(JSContext context, JSValue value) {
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Integer.MIN_VALUE;
        }
        if (!Double.isFinite(numericValue)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Integer.MIN_VALUE;
        }
        return (int) numericValue;
    }

    /**
     * Converts a JSValue to a finite integral long or throws a RangeError.
     * Unlike toIntegerThrowOnInfinity, this rejects fractional values.
     * Returns Long.MIN_VALUE if a pending exception was set.
     */
    public static long toLongIfIntegral(JSContext context, JSValue value) {
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return Long.MIN_VALUE;
        }
        if (!Double.isFinite(numericValue)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Long.MIN_VALUE;
        }
        if (numericValue != Math.floor(numericValue)) {
            context.throwRangeError("Temporal error: Expected finite integer.");
            return Long.MIN_VALUE;
        }
        return (long) numericValue;
    }

    public static String toTemporalCalendarWithISODefault(JSContext context, JSValue calendarValue) {
        if (calendarValue instanceof JSUndefined || calendarValue == null) {
            return "iso8601";
        }
        if (calendarValue instanceof JSTemporalPlainDate temporalPlainDate) {
            return temporalPlainDate.getCalendarId();
        }
        if (calendarValue instanceof JSTemporalPlainDateTime temporalPlainDateTime) {
            return temporalPlainDateTime.getCalendarId();
        }
        if (calendarValue instanceof JSTemporalPlainMonthDay temporalPlainMonthDay) {
            return temporalPlainMonthDay.getCalendarId();
        }
        if (calendarValue instanceof JSTemporalPlainYearMonth temporalPlainYearMonth) {
            return temporalPlainYearMonth.getCalendarId();
        }
        if (calendarValue instanceof JSTemporalZonedDateTime temporalZonedDateTime) {
            return temporalZonedDateTime.getCalendarId();
        }
        if (!(calendarValue instanceof JSString calendarString)) {
            context.throwTypeError("Temporal error: Calendar must be string.");
            return null;
        }

        String canonicalCalendarId = canonicalizeCalendarIdentifier(calendarString.value());
        if (isSupportedCalendarIdentifier(canonicalCalendarId)) {
            return canonicalCalendarId;
        }

        String calendarStringValue = calendarString.value();
        if (!canParseCalendarLikeString(context, calendarStringValue)) {
            context.throwRangeError("Temporal error: Invalid calendar.");
            return null;
        }

        String firstCalendarAnnotation = firstCalendarAnnotation(calendarStringValue);
        if (firstCalendarAnnotation != null) {
            String validatedCalendar = validateCalendar(context, new JSString(firstCalendarAnnotation));
            if (context.hasPendingException()) {
                return null;
            }
            return validatedCalendar;
        }
        return "iso8601";
    }

    /**
     * Validates that a calendar value is a string, matching V8's error message.
     */
    public static String validateCalendar(JSContext context, JSValue calendarValue) {
        if (calendarValue instanceof JSUndefined || calendarValue == null) {
            return "iso8601";
        }
        if (!(calendarValue instanceof JSString calendarString)) {
            context.throwTypeError("Temporal error: Calendar must be string.");
            return null;
        }
        String canonicalCalendarId = canonicalizeCalendarIdentifier(calendarString.value());
        if (!isSupportedCalendarIdentifier(canonicalCalendarId)) {
            context.throwRangeError("Temporal error: Invalid calendar.");
            return null;
        }
        return canonicalCalendarId;
    }
}
