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
import java.time.zone.ZoneRules;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern NOTE_DATETIME_WITH_SPACE_PATTERN = Pattern.compile(
            "^(?<year>(?:[+-]\\d{6}|\\d{4}))-(?<month>\\d{1,2})-(?<day>\\d{1,2})\\s+"
                    + "(?<hour>\\d{1,2}):(?<minute>\\d{1,2})"
                    + "(?::(?<second>\\d{1,2})(?:[\\.,](?<fraction>\\d{1,9}))?)?"
                    + "(?:(?<z>Z)|(?<tzSign>[+-])(?<tzHour>\\d{2})(?::?(?<tzMinute>\\d{2}))?)?$");
    private static final DateTimeFormatter PARSE_TO_STRING_FORMATTER =
            DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'XX", Locale.ENGLISH);
    private static final DateTimeFormatter PARSE_UTC_STRING_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);
    private static final ZoneRules SYSTEM_ZONE_RULES = ZoneId.systemDefault().getRules();
    private double timeValue;

    public JSDate(JSContext context) {
        this(context, System.currentTimeMillis());
    }

    public JSDate(JSContext context, long timeValue) {
        this(context, (double) timeValue);
    }

    public JSDate(JSContext context, double timeValue) {
        super(context);
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
            } else if (arg instanceof JSNumber num) {
                timeValue = timeClip(num.value());
            } else {
                JSValue primitive = JSTypeConversions.toPrimitive(context, arg, JSTypeConversions.PreferredType.DEFAULT);
                if (context.hasPendingException()) {
                    return (JSObject) context.getPendingException();
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
        JSStackFrame stackFrame = context.getCurrentStackFrame();
        DateTimeFormatter formatter = stackFrame != null && !"<eval>".equals(stackFrame.filename())
                ? TO_STRING_FORMATTER_LONG
                : TO_STRING_FORMATTER_SHORT;
        return zdt.format(formatter);
    }

    public static int getDateFields(double timeValue, double[] fields, boolean isLocal, boolean force) {
        long d;
        long days;
        long weekday;
        long year;
        int timezone = 0;

        if (Double.isNaN(timeValue)) {
            if (!force) {
                return 0;
            }
            d = 0;
        } else {
            d = (long) timeValue;
            if (isLocal) {
                timezone = -getTimezoneOffset(d);
                d += (long) timezone * 60_000L;
            }
        }

        long h = mathMod(d, MILLIS_PER_DAY);
        days = (d - h) / MILLIS_PER_DAY;
        int milliseconds = (int) (h % 1000);
        h = (h - milliseconds) / 1000;
        int seconds = (int) (h % 60);
        h = (h - seconds) / 60;
        int minutes = (int) (h % 60);
        int hour = (int) ((h - minutes) / 60);
        weekday = mathMod(days + 4, 7);

        YearFromDaysResult yearResult = yearFromDays(days);
        year = yearResult.year;
        days = yearResult.remainingDays;

        int month;
        for (month = 0; month < 11; month++) {
            int monthDays = MONTH_DAYS[month];
            if (month == 1) {
                monthDays += daysInYear(year) - 365;
            }
            if (days < monthDays) {
                break;
            }
            days -= monthDays;
        }

        fields[FIELD_YEAR] = year;
        fields[FIELD_MONTH] = month;
        fields[FIELD_DATE] = days + 1;
        fields[FIELD_HOURS] = hour;
        fields[FIELD_MINUTES] = minutes;
        fields[FIELD_SECONDS] = seconds;
        fields[FIELD_MILLISECONDS] = milliseconds;
        fields[FIELD_DAY] = weekday;
        fields[FIELD_TIMEZONE_OFFSET] = timezone;
        return 1;
    }

    public static String getTimezoneName(long epochMillis, boolean longFormat) {
        ZoneId zone = ZoneId.systemDefault();
        Instant instant = Instant.ofEpochMilli(epochMillis);
        ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, zone);
        // Use DateTimeFormatter pattern to get DST-aware timezone name
        // "zzzz" gives full name like "Central European Standard Time"
        // "z" gives short name like "CET"
        DateTimeFormatter formatter = longFormat
                ? DateTimeFormatter.ofPattern("zzzz", Locale.ENGLISH)
                : DateTimeFormatter.ofPattern("z", Locale.ENGLISH);
        return zdt.format(formatter);
    }

    public static int getTimezoneOffset(long epochMillis) {
        long epochSecond = Math.floorDiv(epochMillis, 1000L);
        ZoneOffset zoneOffset = SYSTEM_ZONE_RULES.getOffset(Instant.ofEpochSecond(epochSecond));
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

        double noteDateTimeResult = parseNoteDateTimeWithSpace(str);
        if (!Double.isNaN(noteDateTimeResult)) {
            return noteDateTimeResult;
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
                int tzMinutes;
                if (pos < len && str.charAt(pos) == ':') {
                    pos++;
                    int[] tzMinResult = parseDigits(str, pos, 2, 2);
                    if (tzMinResult == null) {
                        return Double.NaN;
                    }
                    tzMinutes = tzMinResult[0];
                    pos = tzMinResult[1];
                } else {
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

    private static double parseNoteDateTimeWithSpace(String str) {
        Matcher matcher = NOTE_DATETIME_WITH_SPACE_PATTERN.matcher(str);
        if (!matcher.matches()) {
            return Double.NaN;
        }

        int year = Integer.parseInt(matcher.group("year"));
        int month = Integer.parseInt(matcher.group("month"));
        int day = Integer.parseInt(matcher.group("day"));
        int hour = Integer.parseInt(matcher.group("hour"));
        int minute = Integer.parseInt(matcher.group("minute"));
        int second = 0;
        int millisecond = 0;

        String secondGroup = matcher.group("second");
        if (secondGroup != null) {
            second = Integer.parseInt(secondGroup);
            String fractionGroup = matcher.group("fraction");
            if (fractionGroup != null) {
                if (fractionGroup.length() >= 3) {
                    millisecond = Integer.parseInt(fractionGroup.substring(0, 3));
                } else if (fractionGroup.length() == 2) {
                    millisecond = Integer.parseInt(fractionGroup) * 10;
                } else {
                    millisecond = Integer.parseInt(fractionGroup) * 100;
                }
            }
        }

        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return Double.NaN;
        }
        if (hour > 24 || minute > 59 || second > 59) {
            return Double.NaN;
        }
        if (hour == 24 && (minute != 0 || second != 0 || millisecond != 0)) {
            return Double.NaN;
        }

        double[] fields = new double[7];
        fields[0] = year;
        fields[1] = month - 1;
        fields[2] = day;
        fields[3] = hour;
        fields[4] = minute;
        fields[5] = second;
        fields[6] = millisecond;

        if (matcher.group("z") != null) {
            return setDateFields(fields, false);
        }

        String tzSign = matcher.group("tzSign");
        if (tzSign != null) {
            int timezoneHour = Integer.parseInt(matcher.group("tzHour"));
            String timezoneMinuteGroup = matcher.group("tzMinute");
            int timezoneMinute = timezoneMinuteGroup == null ? 0 : Integer.parseInt(timezoneMinuteGroup);
            if (timezoneHour > 23 || timezoneMinute > 59) {
                return Double.NaN;
            }
            int offsetMinutes = timezoneHour * 60 + timezoneMinute;
            if ("-".equals(tzSign)) {
                offsetMinutes = -offsetMinutes;
            }
            return setDateFields(fields, false) - (double) offsetMinutes * 60_000;
        }

        return setDateFields(fields, true);
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
