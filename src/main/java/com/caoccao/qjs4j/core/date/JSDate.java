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

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.error.JSStackFrame;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneRules;
import java.util.Locale;

/**
 * Represents a JavaScript Date object. Stores the clipped epoch milliseconds like QuickJS.
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
    private static final long MILLIS_PER_DAY = 86_400_000L;
    private static final int[] MONTH_DAYS = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    public static final String NAME = "Date";
    private static final ZoneRules SYSTEM_ZONE_RULES = ZoneId.systemDefault().getRules();
    public static final DateTimeFormatter TO_STRING_FORMATTER_LONG = DateTimeFormatter
            .ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (zzzz)", Locale.ENGLISH);
    public static final DateTimeFormatter TO_STRING_FORMATTER_SHORT = DateTimeFormatter
            .ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (z)", Locale.ENGLISH);
    private double timeValue;

    public JSDate(JSContext context) {
        this(context, System.currentTimeMillis());
    }

    public JSDate(JSContext context, double timeValue) {
        super(context);
        this.timeValue = timeValue;
    }

    public JSDate(JSContext context, long timeValue) {
        this(context, (double) timeValue);
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

    private static long clampToLong(double value) {
        if (value < Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        if (value >= 0x1p63) {
            return Long.MAX_VALUE;
        }
        return (long) value;
    }

    public static JSValue create(JSContext context, JSValue... args) {
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
                JSValue primitive = JSTypeConversions.toPrimitive(context, arg,
                        JSTypeConversions.PreferredType.DEFAULT);
                if (context.hasPendingException()) {
                    // The thrown value may be a primitive: casting it to JSObject failed inside the engine.
                    return context.getPendingException();
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
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
            }
            timeValue = setDateFieldsChecked(fields, true);
        }
        return context.createJSDate(timeValue);
    }

    public static long dateNow() {
        return System.currentTimeMillis();
    }

    private static long daysFromYear(long y) {
        return 365L * (y - 1970) + Math.floorDiv(y - 1969, 4) - Math.floorDiv(y - 1901, 100)
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
                // The whole offset, seconds included: before standard time a zone's offset is its local mean
                // time, Berlin's +0:53:28, and dropping the 28 seconds put every such date 28 seconds off.
                int offsetSeconds = SYSTEM_ZONE_RULES.getOffset(Instant.ofEpochSecond(Math.floorDiv(d, 1000L)))
                        .getTotalSeconds();
                timezone = offsetSeconds / 60;
                d += offsetSeconds * 1000L;
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

    /**
     * LocalTZA(t, false): the offset of a local wall-clock time, in seconds.
     * <p>
     * The offset used to be looked up with the wall-clock value taken as a UTC instant, so a time in the spring-forward
     * gap got the offset after the transition and a time in the autumn overlap resolved to its second occurrence. The
     * specification and V8 use the offset before the transition in both cases, which is what
     * {@link ZoneRules#getOffset(LocalDateTime)} returns.
     *
     * @param zoneRules
     *            the time zone's rules
     * @param localMillis
     *            milliseconds since the epoch of the local date and time, read as if they were UTC
     * @return the zone's offset from UTC in effect at that wall-clock time
     */
    static int getLocalOffsetSeconds(ZoneRules zoneRules, long localMillis) {
        LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(Math.floorDiv(localMillis, 1000L), 0, ZoneOffset.UTC);
        return zoneRules.getOffset(localDateTime).getTotalSeconds();
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

    /**
     * MakeDate(MakeDay(year, month, date), MakeTime(hour, min, sec, ms)) without TimeClip.
     *
     * @param fields
     *            year, month (0-based), date, hours, minutes, seconds, milliseconds
     * @return the time value before clipping, or {@code NaN} when the year is out of range
     */
    static double makeDate(double[] fields) {
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
        return tv;
    }

    private static long mathMod(long a, long b) {
        long m = a % b;
        return m + (m < 0 ? b : 0);
    }

    /**
     * Date.parse: the date time string format, then the legacy formats V8 accepts.
     *
     * @param input
     *            the string, or null
     * @return the time value, or {@code NaN}
     * @see JSDateParser
     */
    public static double parseDateString(String input) {
        if (input == null) {
            return Double.NaN;
        }
        return JSDateParser.parse(input);
    }

    public static double setDateFields(double[] fields, boolean isLocal) {
        double tv = makeDate(fields);
        if (Double.isNaN(tv)) {
            return Double.NaN;
        }

        if (isLocal) {
            tv -= getLocalOffsetSeconds(SYSTEM_ZONE_RULES, clampToLong(tv)) * 1000D;
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

    private record YearFromDaysResult(long year, long remainingDays) {
    }
}
