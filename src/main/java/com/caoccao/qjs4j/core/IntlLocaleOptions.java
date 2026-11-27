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

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import static com.caoccao.qjs4j.core.IntlFormatOptions.readStringOption;

/** Intl.Locale option overrides, read before applying them to the input tag. */
record IntlLocaleOptions(String language, String script, String region, String variants, String calendar,
        String collation, String firstDayOfWeek, String hourCycle, String caseFirst, Boolean numeric,
        String numberingSystem) {

    private static final Set<String> HOUR_CYCLES = Set.of("h11", "h12", "h23", "h24");

    static IntlLocaleOptions read(JSContext context, JSValue options) {
        String language = readStringOption(context, options, "language");
        String script = readStringOption(context, options, "script");
        String region = readStringOption(context, options, "region");
        String variants = readStringOption(context, options, "variants");
        if (context.hasPendingException()) {
            return null;
        }
        if (language != null && !IntlLocaleTag.isValidLanguageSubtag(language)) {
            throw invalid("language", language);
        }
        if (script != null && !IntlLocaleTag.isValidScriptSubtag(script)) {
            throw invalid("script", script);
        }
        if (region != null && !IntlLocaleTag.isValidRegionSubtag(region)) {
            throw invalid("region", region);
        }
        if (variants != null && !IntlLocaleTag.isValidVariantsSubtag(variants)) {
            throw invalid("variants", variants);
        }
        String calendar = readStringOption(context, options, "calendar");
        String collation = readStringOption(context, options, "collation");
        String hourCycle = readStringOption(context, options, "hourCycle");
        String caseFirst = readStringOption(context, options, "caseFirst");
        if (context.hasPendingException()) {
            return null;
        }
        Boolean numeric = null;
        if (options instanceof JSObject object) {
            JSValue value = object.get(PropertyKey.fromString("numeric"));
            if (context.hasPendingException()) {
                return null;
            }
            if (value != null && !(value instanceof JSUndefined)) {
                numeric = JSTypeConversions.toBoolean(value) == JSBoolean.TRUE;
            }
        }
        String numberingSystem = readStringOption(context, options, "numberingSystem");
        String firstDay = readStringOption(context, options, "firstDayOfWeek");
        if (context.hasPendingException()) {
            return null;
        }
        String weekday = firstDay == null ? null : weekdayToString(firstDay);
        if (weekday != null && !IntlFormatOptions.UNICODE_TYPE_PATTERN.matcher(weekday).matches()) {
            throw invalid("firstDayOfWeek", firstDay);
        }
        validateUnicodeType("calendar", calendar);
        validateUnicodeType("collation", collation);
        if (hourCycle != null && !HOUR_CYCLES.contains(hourCycle)) {
            throw invalid("hourCycle", hourCycle);
        }
        if (caseFirst != null && !caseFirst.equals("lower") && !caseFirst.equals("upper")
                && !caseFirst.equals("false")) {
            throw invalid("caseFirst", caseFirst);
        }
        validateUnicodeType("numberingSystem", numberingSystem);
        return new IntlLocaleOptions(language, script, region, variants, calendar, collation, weekday, hourCycle,
                caseFirst, numeric, numberingSystem);
    }

    private static IllegalArgumentException invalid(String name, String value) {
        return new IllegalArgumentException("invalid " + name + ": " + value);
    }

    private static void validateUnicodeType(String name, String value) {
        if (value != null && !IntlFormatOptions.UNICODE_TYPE_PATTERN.matcher(value).matches()) {
            throw invalid(name, value);
        }
    }

    void applyLanguageId(Locale.Builder builder) {
        if (language != null) {
            builder.setLanguage(language);
        }
        if (script != null) {
            builder.setScript(script);
        }
        if (region != null) {
            builder.setRegion(region);
        }
        if (variants != null) {
            builder.setVariant(variants);
        }
    }

    void applyExtensions(Locale.Builder builder, Map<String, String> extensions) {
        put(extensions, "ca", calendar);
        put(extensions, "co", collation);
        put(extensions, "fw", firstDayOfWeek);
        put(extensions, "hc", hourCycle);
        put(extensions, "kf", caseFirst);
        put(extensions, "kn", numeric == null ? null : numeric.toString());
        put(extensions, "nu", numberingSystem);
        extensions.computeIfPresent("ca", (key, value) -> JSIntlObject.canonicalizeCalendar(value));

        StringJoiner fields = new StringJoiner("-");
        String attributes = extensions.remove("_attr");
        if (attributes != null && !attributes.isEmpty()) {
            fields.add(attributes);
        }
        for (Map.Entry<String, String> entry : extensions.entrySet()) {
            fields.add(entry.getKey());
            String value = entry.getValue();
            if (!value.isEmpty() && !value.equals("true")) {
                fields.add(value);
            }
        }
        if (fields.length() > 0) {
            builder.setExtension('u', fields.toString());
        }
    }

    private static void put(Map<String, String> extensions, String key, String value) {
        if (value != null) {
            extensions.put(key, value);
        }
    }

    private static String weekdayToString(String value) {
        return switch (value) {
            case "0", "7" -> "sun";
            case "1" -> "mon";
            case "2" -> "tue";
            case "3" -> "wed";
            case "4" -> "thu";
            case "5" -> "fri";
            case "6" -> "sat";
            default -> value;
        };
    }
}
