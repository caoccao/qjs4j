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

package com.caoccao.qjs4j.core.date;

import com.caoccao.qjs4j.unicode.UnicodeData;

/**
 * {@code Date.parse}: the ECMAScript date time string format first, then the legacy formats V8 accepts.
 * <p>
 * A port of V8's {@code DateParser} ({@code src/date/dateparser*}). The input is split into numbers, words, signs and
 * other symbols; a strict pass reads an ISO 8601 date time string and hands whatever it cannot read to a permissive
 * pass, which collects up to three day numbers, up to four time numbers and a time zone, and decides at the end which
 * day number is the year, the month and the day. The previous implementation tried an ISO reader, one regular
 * expression and four {@code java.time} patterns, so {@code Jan 1 2024}, {@code 1/2/2024} and every other common legacy
 * form parsed to {@code NaN}; it also trimmed the input, which made {@code " 2024-01-01"} an ISO date (UTC) where V8
 * reads a legacy date (local time), and accepted a comma as the ISO decimal separator, which V8 refuses.
 */
final class JSDateParser {
    private static final int AM_PM = 4;
    private static final int DAY = 2;
    private static final int HOUR = 3;
    private static final int INVALID_KEYWORD = 0;
    /**
     * Keyword table: three-letter prefix, keyword type, value. Only a month name may be longer than its prefix.
     */
    private static final Object[][] KEYWORDS = {{"jan", 1, 1}, {"feb", 1, 2}, {"mar", 1, 3}, {"apr", 1, 4},
            {"may", 1, 5}, {"jun", 1, 6}, {"jul", 1, 7}, {"aug", 1, 8}, {"sep", 1, 9}, {"oct", 1, 10}, {"nov", 1, 11},
            {"dec", 1, 12}, {"am", 4, 0}, {"pm", 4, 12}, {"ut", 2, 0}, {"utc", 2, 0}, {"z", 2, 0}, {"gmt", 2, 0},
            {"cdt", 2, -5}, {"cst", 2, -6}, {"edt", 2, -4}, {"est", 2, -5}, {"mdt", 2, -6}, {"mst", 2, -7},
            {"pdt", 2, -7}, {"pst", 2, -8}, {"t", 3, 0}};
    /**
     * V8 reads a numeral's first nine significant digits and ignores the rest.
     */
    private static final int MAX_SIGNIFICANT_DIGITS = 9;
    /**
     * Local time may be up to ten days beyond the time value range before it is converted to UTC.
     */
    private static final double MAX_TIME_BEFORE_UTC = JSDate.MAX_TIME_VALUE + 864_000_000D;
    private static final int MILLISECOND = 6;
    private static final int MINUTE = 4;
    private static final int MONTH = 1;
    private static final int MONTH_NAME = 1;
    private static final int NONE = Integer.MAX_VALUE;
    private static final int SECOND = 5;
    private static final int TAG_END_OF_INPUT = -2;
    private static final int TAG_INVALID = -6;
    private static final int TAG_NUMBER = -3;
    private static final int TAG_SYMBOL = -4;
    private static final int TAG_UNKNOWN = -5;
    private static final int TAG_WHITE_SPACE = -1;
    private static final int TIME_SEPARATOR = 3;
    private static final int TIME_ZONE_NAME = 2;
    private static final int YEAR = 0;

    private JSDateParser() {
    }

    private static boolean between(int x, int low, int high) {
        return x >= low && x <= high;
    }

    private static boolean isClockComponent(Token token, boolean midnight) {
        return token.isFixedLengthNumber(2) && isMinute(token.value) && (!midnight || token.value == 0);
    }

    private static boolean isDay(int x) {
        return between(x, 1, 31);
    }

    private static boolean isHour(int x) {
        return between(x, 0, 23);
    }

    private static boolean isMinute(int x) {
        return between(x, 0, 59);
    }

    private static boolean isMonth(int x) {
        return between(x, 1, 12);
    }

    /**
     * Parse a date string into a time value.
     *
     * @param input
     *            the string
     * @return the time value, or {@code NaN} when the string is not a date V8 accepts
     */
    static double parse(String input) {
        Tokenizer scanner = new Tokenizer(input);
        DayComposer day = new DayComposer();
        TimeComposer time = new TimeComposer();
        TimeZoneComposer timeZone = new TimeZoneComposer();
        Token nextUnhandledToken = parseES5DateTime(scanner, day, time, timeZone);
        if (nextUnhandledToken.tag == TAG_INVALID) {
            return Double.NaN;
        }
        boolean hasReadNumber = !day.isEmpty();
        // Whatever the strict pass left is read by the legacy rules.
        for (Token token = nextUnhandledToken; token.tag != TAG_END_OF_INPUT; token = scanner.next()) {
            if (token.tag == TAG_NUMBER) {
                hasReadNumber = true;
                int n = token.value;
                if (scanner.skipSymbol(':')) {
                    if (scanner.skipSymbol(':')) {
                        // n + "::"
                        if (!time.isEmpty()) {
                            return Double.NaN;
                        }
                        time.add(n);
                        time.add(0);
                    } else {
                        // n + ":"
                        if (!time.add(n)) {
                            return Double.NaN;
                        }
                        if (scanner.peek().isSymbol('.')) {
                            scanner.next();
                        }
                    }
                } else if (scanner.skipSymbol('.') && time.isExpecting(n)) {
                    time.add(n);
                    if (scanner.peek().tag != TAG_NUMBER) {
                        return Double.NaN;
                    }
                    int milliseconds = readMilliseconds(scanner.next());
                    time.addFinal(milliseconds);
                } else if (timeZone.isExpecting(n)) {
                    timeZone.minute = n;
                } else if (time.isExpecting(n)) {
                    time.addFinal(n);
                    // The time must be followed by the end, white space, "Z", "+" or "-".
                    Token peek = scanner.peek();
                    if (peek.tag != TAG_END_OF_INPUT && peek.tag != TAG_WHITE_SPACE && !peek.isKeywordZ()
                            && !peek.isAsciiSign()) {
                        return Double.NaN;
                    }
                } else {
                    if (!day.add(n)) {
                        return Double.NaN;
                    }
                    scanner.skipSymbol('-');
                }
            } else if (token.isKeyword()) {
                if (token.tag == AM_PM && !time.isEmpty()) {
                    time.hourOffset = token.value;
                } else if (token.tag == MONTH_NAME) {
                    day.namedMonth = token.value;
                    scanner.skipSymbol('-');
                } else if (token.tag == TIME_ZONE_NAME && hasReadNumber) {
                    timeZone.set(token.value);
                } else {
                    // A garbage word is illegal once a number has been read, and must be separated from the
                    // first number.
                    if (hasReadNumber || scanner.peek().tag == TAG_NUMBER) {
                        return Double.NaN;
                    }
                }
            } else if (token.isAsciiSign() && (timeZone.isUTC() || !time.isEmpty())) {
                // A UTC offset, only after "UTC" or a time; the number may be absent.
                timeZone.sign = token.value == '-' ? -1 : 1;
                int n = 0;
                int length = 0;
                if (scanner.peek().tag == TAG_NUMBER) {
                    Token numberToken = scanner.next();
                    length = numberToken.length;
                    n = numberToken.value;
                }
                hasReadNumber = true;
                if (scanner.peek().isSymbol(':')) {
                    timeZone.hour = n;
                    timeZone.minute = NONE;
                } else if (length == 1 || length == 2) {
                    // GMT-8
                    timeZone.hour = n;
                    timeZone.minute = 0;
                } else if (length == 3 || length == 4) {
                    // GMT-0800
                    timeZone.hour = n / 100;
                    timeZone.minute = n % 100;
                } else {
                    return Double.NaN;
                }
            } else if ((token.isAsciiSign() || token.isSymbol(')')) && hasReadNumber) {
                // An extra sign or ')' is illegal once a number has been read.
                return Double.NaN;
            }
            // Other characters and white space are ignored.
        }

        double[] fields = new double[7];
        if (!day.write(fields) || !time.write(fields)) {
            return Double.NaN;
        }
        double date = JSDate.makeDate(fields);
        if (Double.isNaN(date)) {
            return Double.NaN;
        }
        if (timeZone.isLocal()) {
            if (date < -MAX_TIME_BEFORE_UTC || date > MAX_TIME_BEFORE_UTC) {
                return Double.NaN;
            }
            return JSDate.setDateFields(fields, true);
        }
        date -= timeZone.offsetSeconds() * 1000D;
        return JSDate.timeClip(date);
    }

    /**
     * The ECMAScript date time string format, {@code [+-YY]YYYY[-MM[-DD]][THH:mm[:ss[.sss]][Z|+HH:mm|+HHmm]]}, with the
     * keywords case-insensitive as in V8.
     *
     * @return the end of input when the whole string is in the format; {@link #TAG_INVALID} when it starts in the
     *         format and then breaks it after the time; otherwise the first token the legacy rules must read
     */
    private static Token parseES5DateTime(Tokenizer scanner, DayComposer day, TimeComposer time,
            TimeZoneComposer timeZone) {
        // Mandatory date: [('-'|'+')yy]yyyy[':'MM[':'DD]]
        if (scanner.peek().isAsciiSign()) {
            // Keep the sign token, so invalid dates can be detected.
            Token signToken = scanner.next();
            if (!scanner.peek().isFixedLengthNumber(6)) {
                return signToken;
            }
            int sign = signToken.value == '-' ? -1 : 1;
            int year = scanner.next().value;
            if (sign < 0 && year == 0) {
                return signToken;
            }
            day.add(sign * year);
        } else if (scanner.peek().isFixedLengthNumber(4)) {
            day.add(scanner.next().value);
        } else {
            return scanner.next();
        }
        if (scanner.skipSymbol('-')) {
            if (!scanner.peek().isFixedLengthNumber(2) || !isMonth(scanner.peek().value)) {
                return scanner.next();
            }
            day.add(scanner.next().value);
            if (scanner.skipSymbol('-')) {
                if (!scanner.peek().isFixedLengthNumber(2) || !isDay(scanner.peek().value)) {
                    return scanner.next();
                }
                day.add(scanner.next().value);
            }
        }
        // Optional time: 'T'HH':'mm[':'ss['.'sss]]Z
        if (scanner.peek().tag != TIME_SEPARATOR) {
            if (scanner.peek().tag != TAG_END_OF_INPUT) {
                return scanner.next();
            }
        } else {
            scanner.next();
            if (!scanner.peek().isFixedLengthNumber(2) || !between(scanner.peek().value, 0, 24)) {
                return Token.INVALID;
            }
            // 24:00[:00[.000]] is allowed, but no other time starting with 24.
            boolean hourIs24 = scanner.peek().value == 24;
            time.add(scanner.next().value);
            if (!scanner.skipSymbol(':')) {
                return Token.INVALID;
            }
            if (!isClockComponent(scanner.peek(), hourIs24)) {
                return Token.INVALID;
            }
            time.add(scanner.next().value);
            if (scanner.skipSymbol(':')) {
                if (!isClockComponent(scanner.peek(), hourIs24)) {
                    return Token.INVALID;
                }
                time.add(scanner.next().value);
                if (scanner.skipSymbol('.')) {
                    if (scanner.peek().tag != TAG_NUMBER || (hourIs24 && scanner.peek().value > 0)) {
                        return Token.INVALID;
                    }
                    // More or fewer than the mandated three digits are allowed.
                    time.add(readMilliseconds(scanner.next()));
                }
            }
            // Optional time zone: 'Z' | ('+'|'-')hh':'mm | ('+'|'-')hhmm
            if (scanner.peek().isKeywordZ()) {
                scanner.next();
                timeZone.set(0);
            } else if (scanner.peek().isAsciiSign()) {
                timeZone.sign = scanner.next().value == '+' ? 1 : -1;
                if (scanner.peek().isFixedLengthNumber(4)) {
                    int hourMinute = scanner.next().value;
                    int hour = hourMinute / 100;
                    int minute = hourMinute % 100;
                    if (!isHour(hour) || !isMinute(minute)) {
                        return Token.INVALID;
                    }
                    timeZone.hour = hour;
                    timeZone.minute = minute;
                } else {
                    if (!scanner.peek().isFixedLengthNumber(2) || !isHour(scanner.peek().value)) {
                        return Token.INVALID;
                    }
                    timeZone.hour = scanner.next().value;
                    if (!scanner.skipSymbol(':')) {
                        return Token.INVALID;
                    }
                    if (!scanner.peek().isFixedLengthNumber(2) || !isMinute(scanner.peek().value)) {
                        return Token.INVALID;
                    }
                    timeZone.minute = scanner.next().value;
                }
            }
            if (scanner.peek().tag != TAG_END_OF_INPUT) {
                return Token.INVALID;
            }
        }
        // A date-only form is UTC; a date-time form without an offset is local time.
        if (timeZone.isEmpty() && time.isEmpty()) {
            timeZone.set(0);
        }
        day.isoDate = true;
        return Token.END_OF_INPUT;
    }

    /**
     * Read a fraction of a second as milliseconds: the first three significant digits, inferred from the value and the
     * number of digits so leading zeros count.
     */
    private static int readMilliseconds(Token number) {
        int value = number.value;
        int length = number.length;
        if (length == 1) {
            value *= 100;
        } else if (length == 2) {
            value *= 10;
        } else if (length > 3) {
            length = Math.min(length, MAX_SIGNIFICANT_DIGITS);
            int factor = 1;
            do {
                factor *= 10;
                length--;
            } while (length > 3);
            value /= factor;
        }
        return value;
    }

    /**
     * Up to three day numbers and an optional month name; which number is the year is decided at the end.
     */
    private static final class DayComposer {
        private static final int SIZE = 3;
        private final int[] components = new int[SIZE];
        private int index;
        private boolean isoDate;
        private int namedMonth = NONE;

        boolean add(int n) {
            if (index == SIZE) {
                return false;
            }
            components[index++] = n;
            return true;
        }

        boolean isEmpty() {
            return index == 0;
        }

        boolean write(double[] output) {
            if (index < 1) {
                return false;
            }
            // Day and month default to 1; so does a missing year, which then reads as 2001.
            while (index < SIZE) {
                components[index++] = 1;
            }
            int year;
            int month;
            int dayOfMonth;
            if (namedMonth == NONE) {
                if (isoDate || !isDay(components[0])) {
                    // YMD
                    year = components[0];
                    month = components[1];
                    dayOfMonth = components[2];
                } else {
                    // MD(Y)
                    month = components[0];
                    dayOfMonth = components[1];
                    year = components[2];
                }
            } else {
                month = namedMonth;
                if (!isDay(components[0])) {
                    // YMD, MYD or YDM
                    year = components[0];
                    dayOfMonth = components[1];
                } else {
                    // DMY, MDY or DYM
                    dayOfMonth = components[0];
                    year = components[1];
                }
            }
            if (!isoDate) {
                if (between(year, 0, 49)) {
                    year += 2000;
                } else if (between(year, 50, 99)) {
                    year += 1900;
                }
            }
            if (!isMonth(month) || !isDay(dayOfMonth)) {
                return false;
            }
            output[YEAR] = year;
            output[MONTH] = month - 1;
            output[DAY] = dayOfMonth;
            return true;
        }
    }

    /**
     * Hour, minute, second and millisecond, with an optional AM/PM offset.
     */
    private static final class TimeComposer {
        private static final int SIZE = 4;
        private final int[] components = new int[SIZE];
        private int hourOffset = NONE;
        private int index;

        boolean add(int n) {
            if (index == SIZE) {
                return false;
            }
            components[index++] = n;
            return true;
        }

        void addFinal(int n) {
            if (add(n)) {
                while (index < SIZE) {
                    components[index++] = 0;
                }
            }
        }

        boolean isEmpty() {
            return index == 0;
        }

        boolean isExpecting(int n) {
            return (index == 1 && isMinute(n)) || (index == 2 && isMinute(n)) || (index == 3 && between(n, 0, 999));
        }

        boolean write(double[] output) {
            while (index < SIZE) {
                components[index++] = 0;
            }
            int hour = components[0];
            int minute = components[1];
            int second = components[2];
            int millisecond = components[3];
            if (hourOffset != NONE) {
                if (!between(hour, 0, 12)) {
                    return false;
                }
                hour = hour % 12 + hourOffset;
            }
            if (!isHour(hour) || !isMinute(minute) || !isMinute(second) || !between(millisecond, 0, 999)) {
                // Hour 24 is allowed when everything after it is zero.
                if (hour != 24 || minute != 0 || second != 0 || millisecond != 0) {
                    return false;
                }
            }
            output[HOUR] = hour;
            output[MINUTE] = minute;
            output[SECOND] = second;
            output[MILLISECOND] = millisecond;
            return true;
        }
    }

    /**
     * Sign, hour and minute of a UTC offset; no sign means local time.
     */
    private static final class TimeZoneComposer {
        private int hour = NONE;
        private int minute = NONE;
        private int sign = NONE;

        boolean isEmpty() {
            return hour == NONE;
        }

        boolean isExpecting(int n) {
            return hour != NONE && minute == NONE && isMinute(n);
        }

        boolean isLocal() {
            return sign == NONE;
        }

        boolean isUTC() {
            return hour == 0 && minute == 0;
        }

        long offsetSeconds() {
            long seconds = (hour == NONE ? 0L : hour) * 3600L + (minute == NONE ? 0L : minute) * 60L;
            return sign < 0 ? -seconds : seconds;
        }

        void set(int offsetInHours) {
            sign = offsetInHours < 0 ? -1 : 1;
            hour = Math.abs(offsetInHours);
            minute = 0;
        }
    }

    /**
     * A token: a number (value and digit count), a symbol (the character), a word (keyword type as the tag, keyword
     * value, length), white space, something else, or the end.
     */
    private static final class Token {
        static final Token END_OF_INPUT = new Token(TAG_END_OF_INPUT, 0, 0);
        static final Token INVALID = new Token(TAG_INVALID, 0, 0);
        static final Token UNKNOWN = new Token(TAG_UNKNOWN, 0, 0);
        final int length;
        final int tag;
        final int value;

        Token(int tag, int value, int length) {
            this.tag = tag;
            this.value = value;
            this.length = length;
        }

        boolean isAsciiSign() {
            return tag == TAG_SYMBOL && (value == '+' || value == '-');
        }

        boolean isFixedLengthNumber(int expectedLength) {
            return tag == TAG_NUMBER && length == expectedLength;
        }

        boolean isKeyword() {
            return tag >= 0;
        }

        boolean isKeywordZ() {
            return tag == TIME_ZONE_NAME && length == 1 && value == 0;
        }

        boolean isSymbol(char symbol) {
            return tag == TAG_SYMBOL && value == symbol;
        }
    }

    /**
     * Splits the input into tokens with one token of lookahead. A NUL character ends the input, as in V8.
     */
    private static final class Tokenizer {
        private char ch;
        private final String input;
        private Token next;
        private int position;

        Tokenizer(String input) {
            this.input = input;
            this.position = -1;
            advance();
            this.next = scan();
        }

        private void advance() {
            position++;
            ch = position < input.length() ? input.charAt(position) : 0;
        }

        private boolean isAsciiDigit() {
            return ch >= '0' && ch <= '9';
        }

        private boolean isWhiteSpace() {
            return UnicodeData.isWhiteSpace(ch) || UnicodeData.isLineTerminator(ch);
        }

        private boolean isWordCharacter() {
            return ch >= 'A' && !isWhiteSpace();
        }

        Token next() {
            Token result = next;
            next = scan();
            return result;
        }

        Token peek() {
            return next;
        }

        private Token scan() {
            int start = position;
            if (ch == 0) {
                return Token.END_OF_INPUT;
            }
            if (isAsciiDigit()) {
                int value = 0;
                int digits = 0;
                while (ch == '0') {
                    advance();
                }
                while (isAsciiDigit()) {
                    if (digits < MAX_SIGNIFICANT_DIGITS) {
                        value = value * 10 + ch - '0';
                    }
                    digits++;
                    advance();
                }
                return new Token(TAG_NUMBER, value, position - start);
            }
            if (ch == ':' || ch == '-' || ch == '+' || ch == '.' || ch == ')') {
                char symbol = ch;
                advance();
                return new Token(TAG_SYMBOL, symbol, 1);
            }
            if (isWordCharacter()) {
                StringBuilder prefix = new StringBuilder(3);
                int length = 0;
                while (isWordCharacter()) {
                    if (length < 3) {
                        // V8 lower-cases with `| 0x20`, which leaves letters outside ASCII unmatched.
                        prefix.append((char) (ch | 0x20));
                    }
                    length++;
                    advance();
                }
                return lookupKeyword(prefix.toString(), length);
            }
            if (isWhiteSpace()) {
                while (isWhiteSpace()) {
                    advance();
                }
                return new Token(TAG_WHITE_SPACE, 0, position - start);
            }
            if (ch == '(') {
                // A parenthesised comment, nested, possibly unclosed.
                int balance = 0;
                do {
                    if (ch == ')') {
                        balance--;
                    } else if (ch == '(') {
                        balance++;
                    }
                    advance();
                } while (balance > 0 && ch != 0);
                return Token.UNKNOWN;
            }
            advance();
            return Token.UNKNOWN;
        }

        boolean skipSymbol(char symbol) {
            if (next.isSymbol(symbol)) {
                next();
                return true;
            }
            return false;
        }

        private static Token lookupKeyword(String prefix, int length) {
            for (Object[] keyword : KEYWORDS) {
                int type = (int) keyword[1];
                if (keyword[0].equals(prefix) && (length <= 3 || type == MONTH_NAME)) {
                    return new Token(type, (int) keyword[2], length);
                }
            }
            return new Token(INVALID_KEYWORD, 0, length);
        }
    }
}
