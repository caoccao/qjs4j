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

import com.caoccao.qjs4j.core.temporal.IsoCalendarDate;
import com.caoccao.qjs4j.core.temporal.IsoDate;
import com.caoccao.qjs4j.core.temporal.TemporalCalendarId;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.*;
import java.time.chrono.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Intl.DateTimeFormat instance object. Stores all resolved options per ECMA-402.
 */
public final class JSIntlDateTimeFormat extends JSObject {

    private static final String[] EARTHLY_BRANCHES = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] HEAVENLY_STEMS = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};

    public static final String NAME = "Intl.DateTimeFormat";
    private JSFunction boundFormatFunction;
    private final String calendar;
    private final IntlDateFields dateFields;
    private final FormatStyle dateStyle;
    private final String formatPattern;
    private final Integer fractionalSecondDigits;
    private final boolean hasDefaultDateComponents;
    private final boolean hasDefaultTimeComponents;
    private final String hourCycle;
    private final String hourCycleForInstant;
    private final Locale locale;
    private final String numberingSystem;
    private volatile List<IntlDateTimePattern.Token> patternTokens;
    private final IntlTimeFields timeFields;
    private final FormatStyle timeStyle;

    private final String timeZone;
    private final String timeZoneNameOption;

    JSIntlDateTimeFormat(JSContext context, Locale locale, FormatStyle dateStyle, FormatStyle timeStyle,
            String calendar, String numberingSystem, String timeZone, String hourCycle, String hourCycleForInstant,
            IntlDateFields dateFields, IntlTimeFields timeFields, Integer fractionalSecondDigits,
            String timeZoneNameOption, boolean hasDefaultDateComponents, boolean hasDefaultTimeComponents) {
        super(context);
        this.dateFields = dateFields;
        this.timeFields = timeFields;
        this.locale = locale;
        this.dateStyle = dateStyle;
        this.timeStyle = timeStyle;
        this.calendar = calendar;
        this.numberingSystem = numberingSystem;
        this.timeZone = timeZone;
        this.hourCycle = hourCycle;
        this.hourCycleForInstant = hourCycleForInstant;
        this.fractionalSecondDigits = fractionalSecondDigits;
        this.timeZoneNameOption = timeZoneNameOption;
        this.hasDefaultDateComponents = hasDefaultDateComponents;
        this.hasDefaultTimeComponents = hasDefaultTimeComponents;
        this.formatPattern = new IntlDateTimePattern(locale, calendar, dateStyle, timeStyle, hourCycle, dateFields,
                timeFields, fractionalSecondDigits, timeZoneNameOption).buildFormatPattern();
    }

    public JSIntlDateTimeFormat(JSContext context, Locale locale, FormatStyle dateStyle, FormatStyle timeStyle,
            String calendar, String numberingSystem, String timeZone, String hourCycle, String hourCycleForInstant,
            String weekdayOption, String eraOption, String yearOption, String monthOption, String dayOption,
            String dayPeriodOption, String hourOption, String minuteOption, String secondOption,
            Integer fractionalSecondDigits, String timeZoneNameOption, boolean hasDefaultDateComponents,
            boolean hasDefaultTimeComponents) {
        this(context, locale, dateStyle, timeStyle, calendar, numberingSystem, timeZone, hourCycle, hourCycleForInstant,
                new IntlDateFields(weekdayOption, eraOption, yearOption, monthOption, dayOption),
                new IntlTimeFields(dayPeriodOption, hourOption, minuteOption, secondOption), fractionalSecondDigits,
                timeZoneNameOption, hasDefaultDateComponents, hasDefaultTimeComponents);
    }

    private String applyNumberingSystem(String text) {
        String system = numberingSystem;
        if (system == null) {
            system = "ar".equals(locale.getLanguage()) ? "arab" : "latn";
        }
        return switch (system) {
            case "arab" -> IntlNumberingSystem.localize(text, system, true);
            case "deva", "hanidec" -> IntlNumberingSystem.localize(text, system, false);
            default -> text;
        };
    }

    IntlDateFields dateFields() {
        return dateFields;
    }

    /**
     * Decompose a formatted date into typed parts using the format pattern.
     */
    private List<DatePart> decomposeParts(ZonedDateTime dateTime) {
        List<DatePart> parts = new ArrayList<>();
        LunarDate lunarDate = null;
        boolean useLunarParts = isChineseOrDangiCalendar();
        if (useLunarParts) {
            lunarDate = toLunisolarDate(dateTime.toLocalDate(), resolveTemporalCalendarId());
            if (isLunarYearOnlyPattern()) {
                String yearName = chineseYearName(lunarDate.relatedYear());
                if ("zh".equals(locale.getLanguage())) {
                    parts.add(new DatePart("yearName", yearName));
                    parts.add(new DatePart("literal", "年"));
                } else {
                    parts.add(new DatePart("relatedYear", Integer.toString(lunarDate.relatedYear())));
                    parts.add(new DatePart("yearName", yearName));
                }
                return parts;
            }
        }
        List<IntlDateTimePattern.Token> tokens = patternTokens;
        if (tokens == null) {
            tokens = IntlDateTimePattern.tokenize(formatPattern);
            patternTokens = tokens;
        }
        Chronology chronology = resolveChronology(calendar);
        IsoCalendarDate calendarDate = null;
        if ((chronology == null && calendar != null && !useLunarParts)
                || (isIslamicCalendar() && hasExplicitNumericDateOptions())) {
            calendarDate = new IsoDate(dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth())
                    .toIsoCalendarDate(resolveTemporalCalendarId());
        }
        for (IntlDateTimePattern.Token token : tokens) {
            char field = token.field;
            if (field == '\0') {
                if (!token.text.isEmpty()) {
                    appendLiteral(parts, token.text);
                }
            } else if (lunarDate != null && field == 'y') {
                parts.add(new DatePart("relatedYear", applyNumberingSystem(Integer.toString(lunarDate.relatedYear()))));
                parts.add(new DatePart("yearName", chineseYearName(lunarDate.relatedYear())));
            } else {
                String type = fieldCharToType(field, useLunarParts);
                String value = formatField(dateTime, token, chronology, calendarDate, lunarDate);
                if ("literal".equals(type)) {
                    appendLiteral(parts, value);
                } else {
                    parts.add(new DatePart(type, isNumericDatePartType(type) ? applyNumberingSystem(value) : value));
                }
            }
        }
        return parts;
    }

    public String format(double epochMillis) {
        List<DatePart> parts = formatToPartsList(epochMillis);
        StringBuilder result = new StringBuilder();
        for (DatePart part : parts) {
            result.append(part.value());
        }
        return applyNumberingSystem(result.toString());
    }

    /**
     * Format a single field from a ZonedDateTime.
     */
    private String formatField(ZonedDateTime dateTime, IntlDateTimePattern.Token token, Chronology chronology,
            IsoCalendarDate calendarDateFields, LunarDate lunarDate) {
        char field = token.field;
        int width = token.text.length();
        if ((field == 'a' || field == 'b' || field == 'B') && timeFields.dayPeriod() != null
                && "en".equals(locale.getLanguage())) {
            return resolveEnglishDayPeriod(dateTime.getHour(), timeFields.dayPeriod());
        }
        if (field == 'G' && dateFields.era() != null) {
            int year = dateTime.getYear();
            return resolveEraValue(year);
        }
        if (lunarDate != null) {
            if (field == 'y') {
                return Integer.toString(lunarDate.relatedYear());
            }
            if (field == 'M' || field == 'L') {
                String monthValue;
                if (width >= 2) {
                    monthValue = String.format(locale, "%02d", lunarDate.month());
                } else {
                    monthValue = Integer.toString(lunarDate.month());
                }
                if (lunarDate.leapMonth()) {
                    return monthValue + "bis";
                }
                return monthValue;
            }
            if (field == 'd') {
                if (width >= 2) {
                    return String.format(locale, "%02d", lunarDate.day());
                }
                return Integer.toString(lunarDate.day());
            }
        }
        if (calendarDateFields != null) {
            if (field == 'y') {
                return Integer.toString(calendarDateFields.year());
            }
            if (field == 'M' || field == 'L') {
                if ("hebrew".equals(calendar)) {
                    return resolveHebrewMonthName(calendarDateFields.monthCode());
                }
                if (width >= 2) {
                    return String.format(locale, "%02d", calendarDateFields.month());
                }
                return Integer.toString(calendarDateFields.month());
            }
            if (field == 'd') {
                if (width >= 2) {
                    return String.format(locale, "%02d", calendarDateFields.day());
                }
                return Integer.toString(calendarDateFields.day());
            }
        }
        return token.format(dateTime, locale, chronology);
    }

    /**
     * Format a date into typed parts for formatToParts().
     */
    public List<DatePart> formatToPartsList(double epochMillis) {
        if (!Double.isFinite(epochMillis)) {
            return List.of(new DatePart("literal", "Invalid Date"));
        }
        ZoneId zoneId = resolveZoneId();
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli((long) epochMillis), zoneId);
        return decomposeParts(dateTime);
    }

    public JSFunction getBoundFormatFunction() {
        return boundFormatFunction;
    }

    public String getCalendar() {
        return calendar;
    }

    public FormatStyle getDateStyle() {
        return dateStyle;
    }

    public String getDayOption() {
        return dateFields.day();
    }

    /**
     * Adjust hour-related fields and AM/PM markers in a pattern to match the requested hourCycle. h11=K(0-11),
     * h12=h(1-12), h23=H(0-23), h24=k(1-24).
     */

    public String getDayPeriodOption() {
        return timeFields.dayPeriod();
    }

    public String getEraOption() {
        return dateFields.era();
    }

    public Integer getFractionalSecondDigits() {
        return fractionalSecondDigits;
    }

    public String getHourCycle() {
        return hourCycle;
    }

    public String getHourCycleForInstant() {
        return hourCycleForInstant;
    }

    public String getHourOption() {
        return timeFields.hour();
    }

    public Locale getLocale() {
        return locale;
    }

    public String getMinuteOption() {
        return timeFields.minute();
    }

    public String getMonthOption() {
        return dateFields.month();
    }

    public String getNumberingSystem() {
        return numberingSystem;
    }

    public String getSecondOption() {
        return timeFields.second();
    }

    public FormatStyle getTimeStyle() {
        return timeStyle;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getTimeZoneNameOption() {
        return timeZoneNameOption;
    }

    public String getWeekdayOption() {
        return dateFields.weekday();
    }

    public String getYearOption() {
        return dateFields.year();
    }

    public boolean hasDefaultDateComponents() {
        return hasDefaultDateComponents;
    }

    public boolean hasDefaultTimeComponents() {
        return hasDefaultTimeComponents;
    }

    private boolean hasExplicitNumericDateOptions() {
        boolean hasNumericYear = "numeric".equals(dateFields.year()) || "2-digit".equals(dateFields.year());
        boolean hasNumericMonth = "numeric".equals(dateFields.month()) || "2-digit".equals(dateFields.month());
        boolean hasNumericDay = "numeric".equals(dateFields.day()) || "2-digit".equals(dateFields.day());
        return hasNumericYear && hasNumericMonth && hasNumericDay;
    }

    /**
     * Check if this formatter uses a text-based month (short, long, or narrow).
     */
    public boolean hasTextMonth() {
        return "short".equals(dateFields.month()) || "long".equals(dateFields.month())
                || "narrow".equals(dateFields.month());
    }

    private boolean isChineseOrDangiCalendar() {
        if (calendar == null) {
            return false;
        }
        return "chinese".equals(calendar) || "dangi".equals(calendar);
    }

    private boolean isIslamicCalendar() {
        if (calendar == null) {
            return false;
        }
        return "islamic-civil".equals(calendar) || "islamic-tbla".equals(calendar)
                || "islamic-umalqura".equals(calendar) || "islamic".equals(calendar) || "islamic-rgsa".equals(calendar);
    }

    private boolean isLunarYearOnlyPattern() {
        boolean hasDateOnlyYear = dateFields.year() != null && dateFields.month() == null && dateFields.day() == null
                && dateFields.weekday() == null;
        boolean hasNoTimeFields = !timeFields.specified() && fractionalSecondDigits == null
                && timeZoneNameOption == null;
        return hasDateOnlyYear && hasNoTimeFields;
    }

    private String resolveEraValue(int isoYear) {
        if (calendar == null || "gregory".equals(calendar)) {
            boolean beforeChrist = isoYear <= 0;
            return switch (dateFields.era()) {
                case "long" -> beforeChrist ? "Before Christ" : "Anno Domini";
                case "narrow" -> beforeChrist ? "B" : "A";
                default -> beforeChrist ? "BC" : "AD";
            };
        }
        if ("japanese".equals(calendar)) {
            if (isoYear >= 2019) {
                return "Reiwa";
            }
            if (isoYear >= 1989) {
                return "Heisei";
            }
            if (isoYear >= 1926) {
                return "Showa";
            }
            if (isoYear >= 1912) {
                return "Taisho";
            }
            if (isoYear >= 1873) {
                return "Meiji";
            }
            if (isoYear > 0) {
                return "CE";
            }
            return "BCE";
        }
        if ("roc".equals(calendar)) {
            return isoYear >= 1912 ? "Minguo" : "Before R.O.C.";
        }
        if ("islamic-civil".equals(calendar) || "islamic-tbla".equals(calendar) || "islamic-umalqura".equals(calendar)
                || "islamic".equals(calendar) || "islamic-rgsa".equals(calendar)) {
            return isoYear >= 622 ? "AH" : "Before AH";
        }
        if ("buddhist".equals(calendar) || "ethioaa".equals(calendar) || "hebrew".equals(calendar)
                || "indian".equals(calendar) || "persian".equals(calendar)) {
            return calendar;
        }
        return isoYear <= 0 ? "BC" : "AD";
    }

    private String resolveHebrewMonthName(String monthCode) {
        return switch (monthCode) {
            case "M01" -> "Tishri";
            case "M02" -> "Heshvan";
            case "M03" -> "Kislev";
            case "M04" -> "Tevet";
            case "M05" -> "Shevat";
            case "M05L" -> "Adar I";
            case "M06" -> "Adar";
            case "M07" -> "Nisan";
            case "M08" -> "Iyar";
            case "M09" -> "Sivan";
            case "M10" -> "Tamuz";
            case "M11" -> "Av";
            case "M12" -> "Elul";
            default -> monthCode;
        };
    }

    private TemporalCalendarId resolveTemporalCalendarId() {
        TemporalCalendarId temporalCalendarId = TemporalCalendarId.fromIdentifier(calendar);
        if (temporalCalendarId == null) {
            return TemporalCalendarId.ISO8601;
        }
        return temporalCalendarId;
    }

    private ZoneId resolveZoneId() {
        if (timeZone != null) {
            try {
                return ZoneId.of(timeZone);
            } catch (Exception e) {
                // Try ZoneId.SHORT_IDS for abbreviated timezone names (EST, MST, HST, etc.)
                try {
                    return ZoneId.of(timeZone, ZoneId.SHORT_IDS);
                } catch (Exception e2) {
                    // Fall back to java.util.TimeZone which knows more aliases
                    java.util.TimeZone tz = java.util.TimeZone.getTimeZone(timeZone);
                    if (!"GMT".equals(tz.getID()) || "GMT".equalsIgnoreCase(timeZone)) {
                        return tz.toZoneId();
                    }
                }
            }
        }
        return ZoneId.systemDefault();
    }

    public void setBoundFormatFunction(JSFunction boundFormatFunction) {
        this.boundFormatFunction = boundFormatFunction;
    }

    IntlTimeFields timeFields() {
        return timeFields;
    }

    /**
     * Finish the literal parts. Adjacent literals are one part in V8: a quoted pattern literal such as the "at" of "{1}
     * 'at' {0}" is not a separate part from the spaces around it. And V8 reverts ICU 72's U+202F NARROW NO-BREAK SPACE
     * to U+0020 in formatted dates (crbug.com/1414292): CLDR 42+ patterns put it before AM/PM and inside "2024 г.",
     * where web content expects an ordinary space. The JDK's CLDR data has the same character, and the formatter used
     * to insert it before English AM/PM even on JDK 17. V8 reverts it in format() only; doing it in the parts too keeps
     * format(), formatToParts(), formatRange() and toLocaleString() consistent, which test262 checks.
     */
    private static void appendLiteral(List<DatePart> parts, String text) {
        String value = text.replace('\u202F', ' ');
        int last = parts.size() - 1;
        if (last >= 0 && "literal".equals(parts.get(last).type())) {
            parts.set(last, new DatePart("literal", parts.get(last).value() + value));
        } else {
            parts.add(new DatePart("literal", value));
        }
    }

    private static String chineseYearName(int relatedYear) {
        int stemIndex = Math.floorMod(relatedYear - 4, 10);
        int branchIndex = Math.floorMod(relatedYear - 4, 12);
        return HEAVENLY_STEMS[stemIndex] + EARTHLY_BRANCHES[branchIndex];
    }

    /**
     * Map a pattern field character to its ECMA-402 part type name.
     */
    private static String fieldCharToType(char c, boolean useLunarRelatedYear) {
        return switch (c) {
            case 'y' -> useLunarRelatedYear ? "relatedYear" : "year";
            case 'M', 'L' -> "month";
            case 'd' -> "day";
            case 'h', 'H', 'k', 'K' -> "hour";
            case 'm' -> "minute";
            case 's' -> "second";
            case 'S' -> "fractionalSecond";
            case 'E', 'e', 'c' -> "weekday";
            case 'G' -> "era";
            case 'a', 'b', 'B' -> "dayPeriod";
            case 'z', 'Z', 'v', 'V', 'O', 'X', 'x' -> "timeZoneName";
            default -> "literal";
        };
    }

    private static boolean isNumericDatePartType(String datePartType) {
        return "year".equals(datePartType) || "relatedYear".equals(datePartType) || "month".equals(datePartType)
                || "day".equals(datePartType) || "hour".equals(datePartType) || "minute".equals(datePartType)
                || "second".equals(datePartType) || "fractionalSecond".equals(datePartType);
    }

    static Chronology resolveChronology(String calendar) {
        if (calendar == null || "gregory".equals(calendar) || "iso8601".equals(calendar)) {
            return IsoChronology.INSTANCE;
        }
        return switch (calendar) {
            case "buddhist" -> ThaiBuddhistChronology.INSTANCE;
            case "japanese" -> JapaneseChronology.INSTANCE;
            case "roc" -> MinguoChronology.INSTANCE;
            case "islamic-civil", "islamic-tbla", "islamic-umalqura", "islamic", "islamic-rgsa" ->
                HijrahChronology.INSTANCE;
            default -> null;
        };
    }

    private static String resolveEnglishDayPeriod(int hourOfDay, String dayPeriodStyle) {
        if (hourOfDay == 12) {
            return "narrow".equals(dayPeriodStyle) ? "n" : "noon";
        }
        if (hourOfDay >= 6 && hourOfDay < 12) {
            return "in the morning";
        }
        if (hourOfDay >= 13 && hourOfDay < 18) {
            return "in the afternoon";
        }
        if (hourOfDay >= 18 && hourOfDay < 21) {
            return "in the evening";
        }
        return "at night";
    }

    private static LunarDate toLunisolarDate(LocalDate gregorianDate, TemporalCalendarId calendarId) {
        IsoDate isoDate = new IsoDate(gregorianDate.getYear(), gregorianDate.getMonthValue(),
                gregorianDate.getDayOfMonth());
        IsoCalendarDate calendarDateFields = isoDate.toIsoCalendarDate(calendarId);
        String monthCode = calendarDateFields.monthCode();
        int monthNumber = Integer.parseInt(monthCode.substring(1, 3));
        boolean leapMonth = monthCode.endsWith("L");
        return new LunarDate(calendarDateFields.year(), monthNumber, calendarDateFields.day(), leapMonth);
    }

    /**
     * A typed part from date formatting.
     */
    public record DatePart(String type, String value) {
    }

    private record LunarDate(int relatedYear, int month, int day, boolean leapMonth) {
    }
}
