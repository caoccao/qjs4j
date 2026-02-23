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

package com.caoccao.qjs4j.core;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Represents a JavaScript Date object.
 * Stores the clipped epoch milliseconds like QuickJS.
 */
public final class JSDate extends JSObject {
    public static final int FIELD_DATE = 2;
    public static final int FIELD_DAY = 7;
    public static final int FIELD_HOURS = 3;
    public static final int FIELD_MILLISECONDS = 6;
    public static final int FIELD_MINUTES = 4;
    public static final int FIELD_MONTH = 1;
    public static final int FIELD_SECONDS = 5;
    public static final int FIELD_TIMEZONE_OFFSET = 8;
    public static final int FIELD_YEAR = 0;
    public static final double MAX_TIME_VALUE = 8.64e15;
    public static final String NAME = "Date";
    public static final DateTimeFormatter TO_STRING_FORMATTER_LONG =
            DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (zzzz)", Locale.ENGLISH);
    public static final DateTimeFormatter TO_STRING_FORMATTER_SHORT =
            DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (z)", Locale.ENGLISH);
    private static final long MILLIS_PER_DAY = 86_400_000L;
    private static final int[] MONTH_DAYS = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    private static final DateTimeFormatter PARSE_TO_STRING_FORMATTER =
            DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'XX", Locale.ENGLISH);
    private static final DateTimeFormatter PARSE_UTC_STRING_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);
    private double timeValue;

    public JSDate() {
        this(System.currentTimeMillis());
    }

    public JSDate(long timeValue) {
        this((double) timeValue);
    }

    public JSDate(double timeValue) {
        super();
        this.timeValue = timeValue;
    }

    private static long clampToLong(double value) {
        if (value < Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        if (value >= 0x1p63) {
            return Long.MAX_VALUE;
        }
        return (long) value;
    }

    public static JSObject create(JSContext context, JSValue... args) {
        final double timeValue;
        if (args.length == 0) {
            timeValue = dateNow();
        } else if (args.length == 1) {
            JSValue arg = args[0];
            if (arg instanceof JSDate date) {
                timeValue = timeClip(date.timeValue);
            } else {
                JSValue primitive = JSTypeConversions.toPrimitive(context, arg, JSTypeConversions.PreferredType.DEFAULT);
                if (context.hasPendingException()) {
                    return context.throwTypeError("cannot convert to primitive");
                }
                if (primitive instanceof JSString str) {
                    timeValue = parseDateString(str.value());
                } else {
                    timeValue = timeClip(JSTypeConversions.toNumber(context, primitive).value());
                }
            }
        } else {
            double[] fields = {0, 0, 1, 0, 0, 0, 0};
            int n = Math.min(args.length, 7);
            for (int i = 0; i < n; i++) {
                fields[i] = JSTypeConversions.toNumber(context, args[i]).value();
            }
            timeValue = setDateFieldsChecked(fields, true);
        }
        return context.createJSDate(timeValue);
    }

    public static long dateNow() {
        return System.currentTimeMillis();
    }

    private static long daysFromYear(long y) {
        return 365L * (y - 1970)
                + Math.floorDiv(y - 1969, 4)
                - Math.floorDiv(y - 1901, 100)
                + Math.floorDiv(y - 1601, 400);
    }

    private static int daysInYear(long y) {
        return 365 + (y % 4 == 0 ? 1 : 0) - (y % 100 == 0 ? 1 : 0) + (y % 400 == 0 ? 1 : 0);
    }

    public static String formatToString(JSContext context, ZonedDateTime zdt) {
        JSContext.StackFrame stackFrame = context.getCurrentStackFrame();
        DateTimeFormatter formatter = stackFrame != null && !"<eval>".equals(stackFrame.filename())
                ? TO_STRING_FORMATTER_LONG
                : TO_STRING_FORMATTER_SHORT;
        return zdt.format(formatter);
    }

    public static int getDateFields(double timeValue, double[] fields, boolean isLocal, boolean force) {
        long d;
        long days;
        long wd;
        long y;
        int tz = 0;

        if (Double.isNaN(timeValue)) {
            if (!force) {
                return 0;
            }
            d = 0;
        } else {
            d = (long) timeValue;
            if (isLocal) {
                tz = -getTimezoneOffset(d);
                d += (long) tz * 60_000L;
            }
        }

        long h = mathMod(d, MILLIS_PER_DAY);
        days = (d - h) / MILLIS_PER_DAY;
        int ms = (int) (h % 1000);
        h = (h - ms) / 1000;
        int s = (int) (h % 60);
        h = (h - s) / 60;
        int m = (int) (h % 60);
        int hour = (int) ((h - m) / 60);
        wd = mathMod(days + 4, 7);

        YearFromDaysResult yearResult = yearFromDays(days);
        y = yearResult.year;
        days = yearResult.remainingDays;

        int month;
        for (month = 0; month < 11; month++) {
            int monthDays = MONTH_DAYS[month];
            if (month == 1) {
                monthDays += daysInYear(y) - 365;
            }
            if (days < monthDays) {
                break;
            }
            days -= monthDays;
        }

        fields[FIELD_YEAR] = y;
        fields[FIELD_MONTH] = month;
        fields[FIELD_DATE] = days + 1;
        fields[FIELD_HOURS] = hour;
        fields[FIELD_MINUTES] = m;
        fields[FIELD_SECONDS] = s;
        fields[FIELD_MILLISECONDS] = ms;
        fields[FIELD_DAY] = wd;
        fields[FIELD_TIMEZONE_OFFSET] = tz;
        return 1;
    }

    public static int getTimezoneOffset(long epochMillis) {
        ZoneOffset zoneOffset = ZoneId.systemDefault()
                .getRules()
                .getOffset(Instant.ofEpochMilli(epochMillis));
        return -zoneOffset.getTotalSeconds() / 60;
    }

    private static long mathMod(long a, long b) {
        long m = a % b;
        return m + (m < 0 ? b : 0);
    }

    public static double parseDateString(String input) {
        if (input == null) {
            return Double.NaN;
        }
        String str = input.trim();
        if (str.isEmpty()) {
            return Double.NaN;
        }

        // Try ISO 8601 date-time string format first (following QuickJS js_date_parse_isostring)
        double isoResult = parseISODateString(str);
        if (!Double.isNaN(isoResult)) {
            return isoResult;
        }

        try {
            return ZonedDateTime.parse(str, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(str, PARSE_UTC_STRING_FORMATTER);
            return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        String noParen = str.replaceFirst("\\s*\\([^)]*\\)\\s*$", "");
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(noParen, PARSE_TO_STRING_FORMATTER);
            return offsetDateTime.toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        // Fallback to lenient SimpleDateFormat for legacy date formats
        // This replaces the deprecated Date.parse() method
        try {
            SimpleDateFormat format = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
            format.setLenient(true);
            return format.parse(str).getTime();
        } catch (ParseException ignored) {
        }

        return Double.NaN;
    }

    /**
     * Parse ISO 8601 date-time string format following QuickJS js_date_parse_isostring.
     * Handles partial ISO strings: YYYY, YYYY-MM, YYYY-MM-DD, YYYY-MM-DDTHH:mm[:ss[.sss]][Z|±HH:mm]
     * Also supports extended years: ±YYYYYY
     * Date-only forms are treated as UTC; date-time forms without timezone are local.
     */
    private static double parseISODateString(String str) {
        int len = str.length();
        int pos = 0;

        // fields: year, month(0-based), day(1-based), hours, minutes, seconds, ms, (unused), tzOffset
        int[] fields = {0, 0, 1, 0, 0, 0, 0, 0, 0};
        boolean isLocal = false;

        // Parse year: yyyy or [+-]yyyyyy
        if (pos < len && (str.charAt(pos) == '+' || str.charAt(pos) == '-')) {
            int sign = str.charAt(pos) == '-' ? -1 : 1;
            pos++;
            int[] result = parseDigits(str, pos, 6, 6);
            if (result == null) {
                return Double.NaN;
            }
            fields[0] = result[0] * sign;
            pos = result[1];
            if (sign == -1 && fields[0] == 0) {
                return Double.NaN; // reject -000000
            }
        } else {
            int[] result = parseDigits(str, pos, 4, 4);
            if (result == null) {
                return Double.NaN;
            }
            fields[0] = result[0];
            pos = result[1];
        }

        // Optional month: -MM
        if (pos < len && str.charAt(pos) == '-') {
            pos++;
            int[] monthResult = parseDigits(str, pos, 2, 2);
            if (monthResult == null) {
                return Double.NaN;
            }
            fields[1] = monthResult[0];
            pos = monthResult[1];
            if (fields[1] < 1) {
                return Double.NaN;
            }
            fields[1] -= 1; // convert to 0-based

            // Optional day: -DD
            if (pos < len && str.charAt(pos) == '-') {
                pos++;
                int[] dayResult = parseDigits(str, pos, 2, 2);
                if (dayResult == null) {
                    return Double.NaN;
                }
                fields[2] = dayResult[0];
                pos = dayResult[1];
                if (fields[2] < 1) {
                    return Double.NaN;
                }
            }
        }

        // Optional time: THH:mm[:ss[.sss]]
        if (pos < len && str.charAt(pos) == 'T') {
            pos++;
            isLocal = true;

            int[] hourResult = parseDigits(str, pos, 2, 2);
            if (hourResult == null) {
                fields[3] = 100; // reject unconditionally in validation
            } else {
                pos = hourResult[1];
                if (pos >= len || str.charAt(pos) != ':') {
                    fields[3] = 100;
                } else {
                    fields[3] = hourResult[0];
                    pos++; // skip ':'
                    int[] minResult = parseDigits(str, pos, 2, 2);
                    if (minResult == null) {
                        fields[3] = 100;
                    } else {
                        fields[4] = minResult[0];
                        pos = minResult[1];

                        // Optional seconds: :ss
                        if (pos < len && str.charAt(pos) == ':') {
                            pos++;
                            int[] secResult = parseDigits(str, pos, 2, 2);
                            if (secResult == null) {
                                return Double.NaN;
                            }
                            fields[5] = secResult[0];
                            pos = secResult[1];

                            // Optional milliseconds: .sss (up to 9 fractional digits)
                            if (pos < len && (str.charAt(pos) == '.' || str.charAt(pos) == ',')) {
                                pos++;
                                int msStart = pos;
                                int msValue = 0;
                                int msDigits = 0;
                                while (pos < len && msDigits < 9 && Character.isDigit(str.charAt(pos))) {
                                    msValue = msValue * 10 + (str.charAt(pos) - '0');
                                    msDigits++;
                                    pos++;
                                }
                                if (msDigits == 0) {
                                    return Double.NaN;
                                }
                                while (msDigits < 3) {
                                    msValue *= 10;
                                    msDigits++;
                                }
                                while (msDigits > 3) {
                                    msValue /= 10;
                                    msDigits--;
                                }
                                fields[6] = msValue;
                            }
                        }
                    }
                }
            }
        }

        // Parse timezone offset if present: Z or [+-]HH:mm or [+-]HHmm
        if (pos < len) {
            isLocal = false;
            char tzChar = str.charAt(pos);
            if (tzChar == 'Z') {
                pos++;
                fields[8] = 0;
            } else if (tzChar == '+' || tzChar == '-') {
                int tzSign = tzChar == '-' ? -1 : 1;
                pos++;
                int[] tzHourResult = parseDigits(str, pos, 2, 2);
                if (tzHourResult == null) {
                    return Double.NaN;
                }
                pos = tzHourResult[1];
                int tzMinutes = 0;
                if (pos < len && str.charAt(pos) == ':') {
                    pos++;
                }
                if (pos < len && Character.isDigit(str.charAt(pos))) {
                    int[] tzMinResult = parseDigits(str, pos, 2, 2);
                    if (tzMinResult == null) {
                        return Double.NaN;
                    }
                    tzMinutes = tzMinResult[0];
                    pos = tzMinResult[1];
                }
                fields[8] = tzSign * (tzHourResult[0] * 60 + tzMinutes);
            } else {
                return Double.NaN; // extraneous characters
            }
        }

        // Error if extraneous characters remain
        if (pos != len) {
            return Double.NaN;
        }

        // Validate fields (following QuickJS js_Date_parse)
        int[] fieldMax = {0, 11, 31, 24, 59, 59};
        for (int i = 1; i < 6; i++) {
            if (fields[i] > fieldMax[i]) {
                return Double.NaN;
            }
        }
        // Special case: 24:00:00.000 is only valid with zero minutes/seconds/ms
        if (fields[3] == 24 && (fields[4] | fields[5] | fields[6]) != 0) {
            return Double.NaN;
        }

        // Convert to epoch milliseconds
        double[] dateFields = new double[7];
        for (int i = 0; i < 7; i++) {
            dateFields[i] = fields[i];
        }
        return setDateFields(dateFields, isLocal) - (double) fields[8] * 60000;
    }

    /**
     * Parse exactly between minDigits and maxDigits decimal digits from str starting at pos.
     *
     * @return int[]{value, newPos} or null if not enough digits
     */
    private static int[] parseDigits(String str, int pos, int minDigits, int maxDigits) {
        int start = pos;
        int value = 0;
        while (pos < str.length() && pos - start < maxDigits && Character.isDigit(str.charAt(pos))) {
            value = value * 10 + (str.charAt(pos) - '0');
            pos++;
        }
        if (pos - start < minDigits) {
            return null;
        }
        return new int[]{value, pos};
    }

    public static double setDateFields(double[] fields, boolean isLocal) {
        double year = fields[0];
        double month = fields[1];
        double date = fields[2];

        double yearMonth = year + Math.floor(month / 12);
        double monthNormalized = month % 12;
        if (monthNormalized < 0) {
            monthNormalized += 12;
        }

        if (yearMonth < -271_821 || yearMonth > 275_760) {
            return Double.NaN;
        }

        int y = (int) yearMonth;
        int m = (int) monthNormalized;

        long days = daysFromYear(y);
        for (int i = 0; i < m; i++) {
            days += MONTH_DAYS[i];
            if (i == 1) {
                days += daysInYear(y) - 365;
            }
        }

        double day = days + date - 1;

        double hours = fields[3];
        double minutes = fields[4];
        double seconds = fields[5];
        double millis = fields[6];

        double time = hours * 3_600_000;
        time += minutes * 60_000;
        time += seconds * 1_000;
        time += millis;

        double tv = day * MILLIS_PER_DAY + time;
        if (!Double.isFinite(tv)) {
            return Double.NaN;
        }

        if (isLocal) {
            long ti = clampToLong(tv);
            tv += (double) getTimezoneOffset(ti) * 60_000;
        }

        return timeClip(tv);
    }

    public static double setDateFieldsChecked(double[] fields, boolean isLocal) {
        for (int i = 0; i < 7; i++) {
            double a = fields[i];
            if (!Double.isFinite(a)) {
                return Double.NaN;
            }
            fields[i] = trunc(a);
            if (i == 0 && fields[0] >= 0 && fields[0] < 100) {
                fields[0] += 1900;
            }
        }
        return setDateFields(fields, isLocal);
    }

    public static double timeClip(double t) {
        if (t >= -MAX_TIME_VALUE && t <= MAX_TIME_VALUE) {
            return trunc(t) + 0.0;
        }
        return Double.NaN;
    }

    public static double trunc(double value) {
        return value < 0 ? Math.ceil(value) : Math.floor(value);
    }

    private static YearFromDaysResult yearFromDays(long days) {
        long y = Math.floorDiv(days * 10_000, 3_652_425) + 1970;
        long d = days;
        while (true) {
            long d1 = d - daysFromYear(y);
            if (d1 < 0) {
                y--;
                d1 += daysInYear(y);
            } else {
                long nd = daysInYear(y);
                if (d1 < nd) {
                    return new YearFromDaysResult(y, d1);
                }
                d1 -= nd;
                y++;
            }
        }
    }

    public ZonedDateTime getLocalZonedDateTime() {
        if (!Double.isFinite(timeValue)) {
            return null;
        }
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli((long) timeValue), ZoneId.systemDefault());
    }

    public double getTimeValue() {
        return timeValue;
    }

    public ZonedDateTime getZonedDateTime() {
        if (!Double.isFinite(timeValue)) {
            return null;
        }
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli((long) timeValue), ZoneOffset.UTC);
    }

    public void setTimeValue(double timeValue) {
        this.timeValue = timeValue;
    }

    @Override
    public String toString() {
        ZonedDateTime zonedDateTime = getZonedDateTime();
        return zonedDateTime == null ? "JSDate[NaN]" : "JSDate[" + zonedDateTime + "]";
    }

    private record YearFromDaysResult(long year, long remainingDays) {
    }
}
