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

import com.caoccao.qjs4j.core.JSContext;

import java.math.BigInteger;

final class IsoParsingState {
    private static final long NANOSECONDS_PER_HOUR = 3_600_000_000_000L;
    private static final long NANOSECONDS_PER_MICROSECOND = 1_000L;
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;
    private static final long NANOSECONDS_PER_MINUTE = 60_000_000_000L;
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    private final String input;
    private int position;

    IsoParsingState(String input) {
        this.input = input;
        this.position = 0;
    }

    private static long[] decomposeTimeFraction(long fractionalNanoseconds) {
        long minutePortion = fractionalNanoseconds / NANOSECONDS_PER_MINUTE;
        long remainingAfterMinutes = fractionalNanoseconds % NANOSECONDS_PER_MINUTE;
        long secondPortion = remainingAfterMinutes / NANOSECONDS_PER_SECOND;
        long remainingAfterSeconds = remainingAfterMinutes % NANOSECONDS_PER_SECOND;
        long millisecondPortion = remainingAfterSeconds / NANOSECONDS_PER_MILLISECOND;
        long remainingAfterMilliseconds = remainingAfterSeconds % NANOSECONDS_PER_MILLISECOND;
        long microsecondPortion = remainingAfterMilliseconds / NANOSECONDS_PER_MICROSECOND;
        long nanosecondPortion = remainingAfterMilliseconds % NANOSECONDS_PER_MICROSECOND;
        return new long[]{
                minutePortion,
                secondPortion,
                millisecondPortion,
                microsecondPortion,
                nanosecondPortion
        };
    }

    private static boolean isAmbiguousFourDigitTime(String value) {
        if (value.length() != 4
                || !isAsciiDigit(value.charAt(0))
                || !isAsciiDigit(value.charAt(1))
                || !isAsciiDigit(value.charAt(2))
                || !isAsciiDigit(value.charAt(3))) {
            return false;
        }
        int month = parseFixedTwoDigits(value, 0);
        int dayOfMonth = parseFixedTwoDigits(value, 2);
        return new IsoDate(1972, month, dayOfMonth).isValid();
    }

    private static boolean isAmbiguousSixDigitTime(String value) {
        if (value.length() != 6
                || !isAsciiDigit(value.charAt(0))
                || !isAsciiDigit(value.charAt(1))
                || !isAsciiDigit(value.charAt(2))
                || !isAsciiDigit(value.charAt(3))
                || !isAsciiDigit(value.charAt(4))
                || !isAsciiDigit(value.charAt(5))) {
            return false;
        }
        int month = parseFixedTwoDigits(value, 4);
        return month >= 1 && month <= 12;
    }

    static boolean isAmbiguousTimeStringWithoutDesignator(String input) {
        String candidate = input;
        int annotationStart = candidate.indexOf('[');
        if (annotationStart >= 0) {
            candidate = candidate.substring(0, annotationStart);
        }
        if (isAmbiguousFourDigitTime(candidate) || isAmbiguousSixDigitTime(candidate)) {
            return true;
        }
        if (candidate.length() == 5
                && isAsciiDigit(candidate.charAt(0))
                && isAsciiDigit(candidate.charAt(1))
                && candidate.charAt(2) == '-'
                && isAsciiDigit(candidate.charAt(3))
                && isAsciiDigit(candidate.charAt(4))) {
            int month = parseFixedTwoDigits(candidate, 0);
            int dayOfMonth = parseFixedTwoDigits(candidate, 3);
            return new IsoDate(1972, month, dayOfMonth).isValid();
        }
        if (candidate.length() == 7
                && isAsciiDigit(candidate.charAt(0))
                && isAsciiDigit(candidate.charAt(1))
                && isAsciiDigit(candidate.charAt(2))
                && isAsciiDigit(candidate.charAt(3))
                && candidate.charAt(4) == '-'
                && isAsciiDigit(candidate.charAt(5))
                && isAsciiDigit(candidate.charAt(6))) {
            int month = parseFixedTwoDigits(candidate, 5);
            return month >= 1 && month <= 12;
        }
        return false;
    }

    static boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }

    static boolean isValidIsoYearMonthDateForParsing(int year, int month, int dayOfMonth) {
        if (month < 1 || month > 12) {
            return false;
        }
        if (dayOfMonth < 1 || dayOfMonth > IsoDate.daysInMonth(year, month)) {
            return false;
        }
        if (year < -271821 || year > 275760) {
            return false;
        }
        if (year == -271821 && month < 4) {
            return false;
        }
        return year != 275760 || month <= 9;
    }

    static int parseFixedTwoDigits(String value, int index) {
        return (value.charAt(index) - '0') * 10 + (value.charAt(index + 1) - '0');
    }

    void advanceOne() {
        position++;
    }

    char current() {
        return input.charAt(position);
    }

    private boolean hasTwoDigits(String value, int index) {
        return index + 2 <= value.length()
                && isAsciiDigit(value.charAt(index))
                && isAsciiDigit(value.charAt(index + 1));
    }

    String input() {
        return input;
    }

    int inputLength() {
        return input.length();
    }

    private boolean isLowercaseAnnotationKey(String annotationKey) {
        if (annotationKey.isEmpty()) {
            return false;
        }
        for (int index = 0; index < annotationKey.length(); index++) {
            char character = annotationKey.charAt(index);
            if (character >= 'A' && character <= 'Z') {
                return false;
            }
            if (!Character.isLetterOrDigit(character) && character != '-' && character != '_') {
                return false;
            }
        }
        return true;
    }

    private boolean isMinutePrecisionOffsetTimeZoneAnnotation(String value) {
        if (value.length() < 3) {
            return false;
        }
        char sign = value.charAt(0);
        if (sign != '+' && sign != '-') {
            return false;
        }
        int index = 1;
        if (!hasTwoDigits(value, index)) {
            return false;
        }
        int hours = (value.charAt(index) - '0') * 10 + (value.charAt(index + 1) - '0');
        if (hours > 23) {
            return false;
        }
        index += 2;
        if (index == value.length()) {
            return true;
        }

        boolean extendedFormat;
        if (value.charAt(index) == ':') {
            extendedFormat = true;
            index++;
        } else if (isAsciiDigit(value.charAt(index))) {
            extendedFormat = false;
        } else {
            return false;
        }

        if (index + 2 > value.length()
                || !isAsciiDigit(value.charAt(index))
                || !isAsciiDigit(value.charAt(index + 1))) {
            return false;
        }
        int minutes = (value.charAt(index) - '0') * 10 + (value.charAt(index + 1) - '0');
        if (minutes > 59) {
            return false;
        }
        index += 2;
        if (index == value.length()) {
            return true;
        }

        if (extendedFormat) {
            if (value.charAt(index) != ':') {
                return false;
            }
        } else if (!isAsciiDigit(value.charAt(index))) {
            return false;
        }
        return false;
    }

    private boolean isOffsetTimeZoneAnnotation(String annotationValue) {
        return !annotationValue.isEmpty()
                && (annotationValue.charAt(0) == '+' || annotationValue.charAt(0) == '-');
    }

    private boolean isValidIsoDateForParsing(int year, int month, int dayOfMonth, boolean enforceIsoDateRange) {
        if (month < 1 || month > 12) {
            return false;
        }
        if (dayOfMonth < 1 || dayOfMonth > IsoDate.daysInMonth(year, month)) {
            return false;
        }
        if (!enforceIsoDateRange) {
            return true;
        }
        return new IsoDate(year, month, dayOfMonth).isValid();
    }

    IsoDate parseDate(JSContext context) {
        return parseDate(context, true);
    }

    IsoDate parseDate(JSContext context, boolean enforceIsoDateRange) {
        int year = parseYear(context);
        if (context.hasPendingException()) {
            return null;
        }
        boolean hasSeparator = position < input.length() && input.charAt(position) == '-';
        if (hasSeparator) {
            position++;
        }
        int month = parseTwoDigits(context, "month");
        if (context.hasPendingException()) {
            return null;
        }
        if (hasSeparator) {
            if (position >= input.length() || input.charAt(position) != '-') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            position++;
        } else {
            if (position < input.length() && input.charAt(position) == '-') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        }
        int dayOfMonth = parseTwoDigits(context, "day");
        if (context.hasPendingException()) {
            return null;
        }
        if (!isValidIsoDateForParsing(year, month, dayOfMonth, enforceIsoDateRange)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return new IsoDate(year, month, dayOfMonth);
    }

    TemporalDuration parseDuration(JSContext context) {
        boolean negative = false;
        if (position < input.length() && (input.charAt(position) == '-' || input.charAt(position) == '\u2212')) {
            negative = true;
            position++;
        } else if (position < input.length() && input.charAt(position) == '+') {
            position++;
        }

        if (position >= input.length() || (input.charAt(position) != 'P' && input.charAt(position) != 'p')) {
            context.throwRangeError("Temporal error: Invalid duration string.");
            return null;
        }
        position++;

        long years = 0;
        long months = 0;
        long weeks = 0;
        long days = 0;
        long hours = 0;
        long minutes = 0;
        long seconds = 0;
        long milliseconds = 0;
        long microseconds = 0;
        long nanoseconds = 0;
        boolean inTimePart = false;
        boolean hasComponent = false;
        boolean hasDateComponent = false;
        boolean hasTimeComponent = false;
        boolean hasFractionalTimeComponent = false;
        int lastDateUnitOrder = -1;
        int lastTimeUnitOrder = -1;

        while (position < input.length()) {
            char currentChar = input.charAt(position);
            if (currentChar == 'T' || currentChar == 't') {
                if (inTimePart) {
                    context.throwRangeError("Temporal error: Invalid duration string.");
                    return null;
                }
                inTimePart = true;
                position++;
                continue;
            }
            if (!isAsciiDigit(currentChar)) {
                context.throwRangeError("Temporal error: Invalid duration string.");
                return null;
            }
            int numberStart = position;
            while (position < input.length() && isAsciiDigit(input.charAt(position))) {
                position++;
            }
            long number = parseLongWithRangeCheckFromDigits(context, numberStart, position);
            if (context.hasPendingException()) {
                return null;
            }
            int fractionalNanoseconds = 0;
            boolean hasFraction = false;
            if (position < input.length() && (input.charAt(position) == '.' || input.charAt(position) == ',')) {
                hasFraction = true;
                position++;
                fractionalNanoseconds = parseFractionalNanoseconds(
                        context,
                        true,
                        "Temporal error: Invalid duration string.",
                        "Temporal error: Invalid duration string.");
                if (context.hasPendingException()) {
                    return null;
                }
            }

            if (position >= input.length()) {
                context.throwRangeError("Temporal error: Invalid duration string.");
                return null;
            }
            char unit = input.charAt(position);
            position++;

            if (!inTimePart) {
                if (hasFraction) {
                    context.throwRangeError("Temporal error: Invalid duration string.");
                    return null;
                }
                int currentDateUnitOrder = switch (unit) {
                    case 'Y', 'y' -> 0;
                    case 'M', 'm' -> 1;
                    case 'W', 'w' -> 2;
                    case 'D', 'd' -> 3;
                    default -> -1;
                };
                if (currentDateUnitOrder < 0 || currentDateUnitOrder <= lastDateUnitOrder) {
                    context.throwRangeError("Temporal error: Invalid duration string.");
                    return null;
                }
                lastDateUnitOrder = currentDateUnitOrder;
                hasDateComponent = true;
                switch (unit) {
                    case 'Y', 'y' -> years = number;
                    case 'M', 'm' -> months = number;
                    case 'W', 'w' -> weeks = number;
                    case 'D', 'd' -> days = number;
                    default -> {
                        context.throwRangeError("Temporal error: Invalid duration string.");
                        return null;
                    }
                }
            } else {
                int currentTimeUnitOrder = switch (unit) {
                    case 'H', 'h' -> 0;
                    case 'M', 'm' -> 1;
                    case 'S', 's' -> 2;
                    default -> -1;
                };
                if (currentTimeUnitOrder < 0 || currentTimeUnitOrder <= lastTimeUnitOrder || hasFractionalTimeComponent) {
                    context.throwRangeError("Temporal error: Invalid duration string.");
                    return null;
                }
                lastTimeUnitOrder = currentTimeUnitOrder;
                hasTimeComponent = true;
                switch (unit) {
                    case 'H', 'h' -> {
                        hours = number;
                        if (hasFraction) {
                            hasFractionalTimeComponent = true;
                            long fractionalHourNanoseconds = fractionalNanoseconds * 3_600L;
                            long[] decomposition = decomposeTimeFraction(fractionalHourNanoseconds);
                            minutes = decomposition[0];
                            seconds = decomposition[1];
                            milliseconds = decomposition[2];
                            microseconds = decomposition[3];
                            nanoseconds = decomposition[4];
                        }
                    }
                    case 'M', 'm' -> {
                        minutes = number;
                        if (hasFraction) {
                            hasFractionalTimeComponent = true;
                            long fractionalMinuteNanoseconds = fractionalNanoseconds * 60L;
                            long[] decomposition = decomposeTimeFraction(fractionalMinuteNanoseconds);
                            seconds = decomposition[1];
                            milliseconds = decomposition[2];
                            microseconds = decomposition[3];
                            nanoseconds = decomposition[4];
                        }
                    }
                    case 'S', 's' -> {
                        seconds = number;
                        if (hasFraction) {
                            hasFractionalTimeComponent = true;
                            milliseconds = fractionalNanoseconds / NANOSECONDS_PER_MILLISECOND;
                            int remainingFractionalNanoseconds = (int) (fractionalNanoseconds % NANOSECONDS_PER_MILLISECOND);
                            microseconds = remainingFractionalNanoseconds / NANOSECONDS_PER_MICROSECOND;
                            nanoseconds = remainingFractionalNanoseconds % NANOSECONDS_PER_MICROSECOND;
                        }
                    }
                    default -> {
                        context.throwRangeError("Temporal error: Invalid duration string.");
                        return null;
                    }
                }
            }
            hasComponent = true;
        }

        if (!hasComponent) {
            context.throwRangeError("Temporal error: Invalid duration string.");
            return null;
        }
        if (inTimePart && !hasTimeComponent) {
            context.throwRangeError("Temporal error: Invalid duration string.");
            return null;
        }
        if (!hasDateComponent && !hasTimeComponent) {
            context.throwRangeError("Temporal error: Invalid duration string.");
            return null;
        }

        int sign;
        if (negative) {
            sign = -1;
        } else {
            sign = 1;
        }
        return new TemporalDuration(
                years * sign, months * sign, weeks * sign, days * sign,
                hours * sign, minutes * sign, seconds * sign,
                milliseconds * sign, microseconds * sign, nanoseconds * sign);
    }

    private int parseFractionalNanoseconds(
            JSContext context,
            boolean rejectMoreThanNineDigits,
            String emptyFractionErrorMessage,
            String overflowFractionErrorMessage) {
        int fractionStart = position;
        while (position < input.length() && isAsciiDigit(input.charAt(position))) {
            position++;
        }
        if (fractionStart == position) {
            context.throwRangeError(emptyFractionErrorMessage);
            return 0;
        }
        int digitCount = position - fractionStart;
        if (digitCount > 9 && rejectMoreThanNineDigits) {
            context.throwRangeError(overflowFractionErrorMessage);
            return 0;
        }
        int parseDigitCount = Math.min(digitCount, 9);
        int value = 0;
        for (int index = 0; index < parseDigitCount; index++) {
            value = value * 10 + (input.charAt(fractionStart + index) - '0');
        }
        for (int index = parseDigitCount; index < 9; index++) {
            value *= 10;
        }
        return value;
    }

    ParsedAnnotations parseInstantAnnotations(JSContext context) {
        int timeZoneAnnotationCount = 0;
        int calendarAnnotationCount = 0;
        boolean hasCriticalCalendarAnnotation = false;
        String firstTimeZoneAnnotation = null;
        String firstCalendarAnnotation = null;
        while (position < input.length() && input.charAt(position) == '[') {
            position++;
            boolean critical = false;
            if (position < input.length() && input.charAt(position) == '!') {
                critical = true;
                position++;
            }
            int contentStart = position;
            while (position < input.length() && input.charAt(position) != ']') {
                position++;
            }
            if (position >= input.length() || contentStart == position) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            String content = input.substring(contentStart, position);
            position++;

            int equalSignIndex = content.indexOf('=');
            if (equalSignIndex >= 0) {
                String annotationKey = content.substring(0, equalSignIndex);
                if (equalSignIndex == content.length() - 1 || !isLowercaseAnnotationKey(annotationKey)) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                if ("u-ca".equals(annotationKey)) {
                    calendarAnnotationCount++;
                    if (firstCalendarAnnotation == null) {
                        firstCalendarAnnotation = content.substring(equalSignIndex + 1);
                    }
                    if (critical) {
                        hasCriticalCalendarAnnotation = true;
                    }
                } else if (critical) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
            } else {
                timeZoneAnnotationCount++;
                if (timeZoneAnnotationCount > 1) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                if (isOffsetTimeZoneAnnotation(content)
                        && !isMinutePrecisionOffsetTimeZoneAnnotation(content)) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                if (firstTimeZoneAnnotation == null) {
                    firstTimeZoneAnnotation = content;
                }
            }
        }
        if (calendarAnnotationCount > 1 && hasCriticalCalendarAnnotation) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return new ParsedAnnotations(firstCalendarAnnotation, firstTimeZoneAnnotation);
    }

    IsoOffset parseInstantOffsetNanoseconds(JSContext context) {
        if (position >= input.length()) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }
        char signCharacter = input.charAt(position);
        if (signCharacter == 'Z' || signCharacter == 'z') {
            position++;
            return new IsoOffset(0, BigInteger.ZERO);
        }
        int sign;
        if (signCharacter == '+') {
            sign = 1;
        } else if (signCharacter == '-') {
            sign = -1;
        } else {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }
        position++;

        int offsetHour = parseTwoDigits(context, "offset hour");
        if (context.hasPendingException()) {
            return null;
        }
        if (offsetHour > 23) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }

        int offsetMinute = 0;
        int offsetSecond = 0;
        int fractionalNanoseconds = 0;
        boolean hasMinute = false;
        boolean hasSecond = false;
        boolean extendedFormat = false;

        if (position < input.length() && input.charAt(position) == ':') {
            extendedFormat = true;
            position++;
            hasMinute = true;
            offsetMinute = parseTwoDigits(context, "offset minute");
            if (context.hasPendingException()) {
                return null;
            }
        } else if (position < input.length() && isAsciiDigit(input.charAt(position))) {
            hasMinute = true;
            offsetMinute = parseTwoDigits(context, "offset minute");
            if (context.hasPendingException()) {
                return null;
            }
        }

        if (hasMinute) {
            if (offsetMinute > 59) {
                context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                return null;
            }
            if (extendedFormat) {
                if (position < input.length() && input.charAt(position) == ':') {
                    position++;
                    hasSecond = true;
                    offsetSecond = parseTwoDigits(context, "offset second");
                    if (context.hasPendingException()) {
                        return null;
                    }
                } else if (position < input.length() && isAsciiDigit(input.charAt(position))) {
                    context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                    return null;
                }
            } else if (position < input.length() && isAsciiDigit(input.charAt(position))) {
                hasSecond = true;
                offsetSecond = parseTwoDigits(context, "offset second");
                if (context.hasPendingException()) {
                    return null;
                }
            } else if (position < input.length() && input.charAt(position) == ':') {
                context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                return null;
            }
        } else if (position < input.length()
                && (input.charAt(position) == ':' || input.charAt(position) == '.' || input.charAt(position) == ',')) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }

        if (hasSecond) {
            if (offsetSecond > 59) {
                context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                return null;
            }
            if (position < input.length() && (input.charAt(position) == '.' || input.charAt(position) == ',')) {
                position++;
                fractionalNanoseconds = parseFractionalNanoseconds(
                        context,
                        true,
                        "Temporal error: Instant argument must be Instant or string.",
                        "Temporal error: Invalid ISO date.");
                if (context.hasPendingException()) {
                    return null;
                }
            }
        } else if (position < input.length() && (input.charAt(position) == '.' || input.charAt(position) == ',')) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }

        int offsetSeconds = sign * (offsetHour * 3600 + offsetMinute * 60 + offsetSecond);
        long offsetNanoseconds = offsetHour * NANOSECONDS_PER_HOUR
                + offsetMinute * NANOSECONDS_PER_MINUTE
                + offsetSecond * NANOSECONDS_PER_SECOND
                + fractionalNanoseconds;
        if (sign < 0) {
            offsetNanoseconds = -offsetNanoseconds;
        }
        return new IsoOffset(offsetSeconds, BigInteger.valueOf(offsetNanoseconds));
    }

    IsoTime parseInstantTime(JSContext context) {
        int hour = parseTwoDigits(context, "hour");
        if (context.hasPendingException()) {
            return null;
        }
        boolean extendedFormat = position < input.length() && input.charAt(position) == ':';
        int minute = 0;
        int second = 0;
        int millisecond = 0;
        int microsecond = 0;
        int nanosecond = 0;
        boolean hasSecond = false;

        if (extendedFormat) {
            position++;
            if (!hasTwoDigits(input, position)) {
                context.throwRangeError("Temporal error: Invalid time");
                return null;
            }
            minute = parseTwoDigits(context, "minute");
            if (context.hasPendingException()) {
                return null;
            }
            if (position < input.length() && input.charAt(position) == ':') {
                position++;
                hasSecond = true;
                second = parseTwoDigits(context, "second");
                if (context.hasPendingException()) {
                    return null;
                }
            } else if (position < input.length() && isAsciiDigit(input.charAt(position))) {
                context.throwRangeError("Temporal error: Invalid time");
                return null;
            }
        } else if (position < input.length() && isAsciiDigit(input.charAt(position))) {
            minute = parseTwoDigits(context, "minute");
            if (context.hasPendingException()) {
                return null;
            }
            if (position < input.length() && isAsciiDigit(input.charAt(position))) {
                hasSecond = true;
                second = parseTwoDigits(context, "second");
                if (context.hasPendingException()) {
                    return null;
                }
            }
        }

        if (hasSecond && position < input.length() && (input.charAt(position) == '.' || input.charAt(position) == ',')) {
            position++;
            int fractionalNanoseconds = parseFractionalNanoseconds(
                    context,
                    true,
                    "Temporal error: Instant argument must be Instant or string.",
                    "Temporal error: Invalid ISO date.");
            if (context.hasPendingException()) {
                return null;
            }
            millisecond = (int) (fractionalNanoseconds / NANOSECONDS_PER_MILLISECOND);
            int remainingAfterMilliseconds = (int) (fractionalNanoseconds % NANOSECONDS_PER_MILLISECOND);
            microsecond = (int) (remainingAfterMilliseconds / NANOSECONDS_PER_MICROSECOND);
            nanosecond = (int) (remainingAfterMilliseconds % NANOSECONDS_PER_MICROSECOND);
        } else if (!hasSecond && position < input.length() && (input.charAt(position) == '.' || input.charAt(position) == ',')) {
            context.throwRangeError("Temporal error: Invalid time");
            return null;
        }

        int constrainedSecond;
        if (second == 60) {
            constrainedSecond = 59;
        } else {
            constrainedSecond = second;
        }
        IsoTime parsedIsoTime = new IsoTime(hour, minute, constrainedSecond, millisecond, microsecond, nanosecond);
        if (!parsedIsoTime.isValid()) {
            context.throwRangeError("Temporal error: Invalid time");
            return null;
        }
        return parsedIsoTime;
    }

    private long parseLongWithRangeCheckFromDigits(JSContext context, int startIndex, int endIndex) {
        long value = 0L;
        for (int index = startIndex; index < endIndex; index++) {
            int digit = input.charAt(index) - '0';
            if (value > (Long.MAX_VALUE - digit) / 10L) {
                BigInteger fallbackValue = new BigInteger(input.substring(startIndex, endIndex));
                return toLongWithRangeCheck(context, fallbackValue);
            }
            value = value * 10L + digit;
        }
        return value;
    }

    int parseTwoDigits(JSContext context, String fieldName) {
        if (position + 2 > input.length()) {
            context.throwRangeError("Temporal error: Invalid character while parsing " + fieldName + " value.");
            return 0;
        }
        char firstDigit = input.charAt(position);
        char secondDigit = input.charAt(position + 1);
        if (!isAsciiDigit(firstDigit) || !isAsciiDigit(secondDigit)) {
            context.throwRangeError("Temporal error: Invalid character while parsing " + fieldName + " value.");
            return 0;
        }
        position += 2;
        return (firstDigit - '0') * 10 + (secondDigit - '0');
    }

    int parseYear(JSContext context) {
        if (position >= input.length()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return 0;
        }
        char signCharacter = input.charAt(position);
        if (signCharacter == '+' || signCharacter == '-') {
            boolean negative = signCharacter == '-';
            position++;
            if (position + 6 > input.length()) {
                context.throwRangeError("Temporal error: Invalid character while parsing year value.");
                return 0;
            }
            int startPosition = position;
            int endPosition = position + 6;
            int year = 0;
            for (int index = startPosition; index < endPosition; index++) {
                char currentCharacter = input.charAt(index);
                if (!isAsciiDigit(currentCharacter)) {
                    context.throwRangeError("Temporal error: Invalid character while parsing year value.");
                    return 0;
                }
                year = year * 10 + (currentCharacter - '0');
            }
            position = endPosition;
            if (negative && year == 0) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return 0;
            }
            if (negative) {
                return -year;
            }
            return year;
        }

        if (!isAsciiDigit(signCharacter)) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return 0;
        }
        if (position + 4 > input.length()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return 0;
        }
        int year = 0;
        for (int digitIndex = 0; digitIndex < 4; digitIndex++) {
            char currentCharacter = input.charAt(position + digitIndex);
            if (!isAsciiDigit(currentCharacter)) {
                context.throwRangeError("Temporal error: Invalid character while parsing year value.");
                return 0;
            }
            year = year * 10 + (currentCharacter - '0');
        }
        position += 4;
        return year;
    }

    int position() {
        return position;
    }

    void setPosition(int position) {
        this.position = position;
    }

    private long toLongWithRangeCheck(JSContext context, BigInteger value) {
        if (value.bitLength() > 63) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return Long.MIN_VALUE;
        }
        return value.longValue();
    }

    static final class ParsedAnnotations {
        private final String calendarAnnotation;
        private final String timeZoneAnnotation;

        private ParsedAnnotations(String calendarAnnotation, String timeZoneAnnotation) {
            this.calendarAnnotation = calendarAnnotation;
            this.timeZoneAnnotation = timeZoneAnnotation;
        }

        String calendarAnnotation() {
            return calendarAnnotation;
        }

        String timeZoneAnnotation() {
            return timeZoneAnnotation;
        }
    }
}
