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

import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Intl.DisplayNames instance object.
 * Stores all resolved options per ECMA-402 §12.
 */
public final class JSIntlDisplayNames extends JSObject {
    public static final String NAME = "Intl.DisplayNames";
    private static final Pattern CALENDAR_KEY = Pattern.compile("^[a-zA-Z0-9]{3,8}(-[a-zA-Z0-9]{3,8})*$");
    private static final Pattern CURRENCY_CODE = Pattern.compile("^[a-zA-Z]{3}$");
    private static final Set<String> KNOWN_CALENDARS = Set.of(
            "buddhist", "chinese", "coptic", "dangi", "ethioaa", "ethiopic",
            "gregory", "hebrew", "indian", "islamic-civil",
            "islamic-tbla", "islamic-umalqura", "iso8601",
            "japanese", "persian", "roc"
    );
    private static final Pattern LANGUAGE_SUBTAG = Pattern.compile("^[a-zA-Z]{2,3}$|^[a-zA-Z]{5,8}$");
    private static final Pattern REGION_SUBTAG = Pattern.compile("^[a-zA-Z]{2}$|^[0-9]{3}$");
    private static final Pattern SCRIPT_SUBTAG = Pattern.compile("^[a-zA-Z]{4}$");
    private static final Set<String> VALID_DATE_TIME_FIELDS = Set.of(
            "era", "year", "quarter", "month", "weekOfYear", "weekday",
            "day", "dayPeriod", "hour", "minute", "second", "timeZoneName"
    );
    private static final Pattern VARIANT_SUBTAG = Pattern.compile("^[a-zA-Z0-9]{5,8}$|^[0-9][a-zA-Z0-9]{3}$");
    private final String fallback;
    private final String languageDisplay;
    private final Locale locale;
    private final String style;
    private final String type;

    public JSIntlDisplayNames(Locale locale, String style, String type, String fallback, String languageDisplay) {
        this.locale = locale;
        this.style = style;
        this.type = type;
        this.fallback = fallback;
        this.languageDisplay = languageDisplay;
    }

    /**
     * Validates code as a structurally valid unicode_language_id per ECMA-402.
     * Format: language ("-" script)? ("-" region)? ("-" variant)*
     * Rejects: "root", bare script subtags, singletons, underscore separators.
     */
    private static boolean isStructurallyValidLanguageTag(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        // Reject underscore separator
        if (code.contains("_")) {
            return false;
        }
        // Reject leading/trailing separator or consecutive separators
        if (code.endsWith("-")) {
            return false;
        }
        // Reject "root"
        if ("root".equalsIgnoreCase(code)) {
            return false;
        }
        String[] parts = code.split("-");
        int index = 0;

        // First part must be a language subtag
        if (index >= parts.length) {
            return false;
        }
        String languagePart = parts[index];
        if (!LANGUAGE_SUBTAG.matcher(languagePart).matches()) {
            return false;
        }
        index++;

        // Optional script subtag (4 alpha)
        boolean hasScript = false;
        if (index < parts.length && SCRIPT_SUBTAG.matcher(parts[index]).matches()) {
            hasScript = true;
            index++;
            // Reject duplicate script
            if (index < parts.length && SCRIPT_SUBTAG.matcher(parts[index]).matches()) {
                return false;
            }
        }

        // Optional region subtag (2 alpha or 3 digit)
        boolean hasRegion = false;
        if (index < parts.length && REGION_SUBTAG.matcher(parts[index]).matches()) {
            hasRegion = true;
            index++;
            // Reject duplicate region
            if (index < parts.length && REGION_SUBTAG.matcher(parts[index]).matches()) {
                return false;
            }
        }

        // Optional variant subtags
        Set<String> seenVariants = new java.util.HashSet<>();
        while (index < parts.length) {
            String part = parts[index];
            if (VARIANT_SUBTAG.matcher(part).matches()) {
                String normalized = part.toLowerCase(java.util.Locale.ROOT);
                if (!seenVariants.add(normalized)) {
                    return false; // Duplicate variant
                }
                index++;
            } else if (part.length() == 1) {
                // Singleton - not allowed in unicode_language_id
                return false;
            } else {
                return false;
            }
        }

        return true;
    }

    public String getFallback() {
        return fallback;
    }

    public String getLanguageDisplay() {
        return languageDisplay;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getStyle() {
        return style;
    }

    public String getType() {
        return type;
    }

    /**
     * Intl.DisplayNames.prototype.of(code)
     * Returns a display name string for the given code, or undefined if fallback is "none" and no name found.
     */
    public JSValue of(JSContext context, String code) {
        if (code == null || code.isEmpty()) {
            context.throwRangeError("invalid code for DisplayNames: " + code);
            return null;
        }
        return switch (type) {
            case "language" -> ofLanguage(context, code);
            case "region" -> ofRegion(context, code);
            case "script" -> ofScript(context, code);
            case "currency" -> ofCurrency(context, code);
            case "calendar" -> ofCalendar(context, code);
            case "dateTimeField" -> ofDateTimeField(context, code);
            default -> {
                context.throwRangeError("invalid type: " + type);
                yield null;
            }
        };
    }

    private JSValue ofCalendar(JSContext context, String code) {
        if (!CALENDAR_KEY.matcher(code).matches()) {
            context.throwRangeError("invalid calendar code for DisplayNames: " + code);
            return null;
        }
        // Reject underscore separator
        if (code.contains("_")) {
            context.throwRangeError("invalid calendar code for DisplayNames: " + code);
            return null;
        }
        String lowerCode = code.toLowerCase(java.util.Locale.ROOT);
        if (KNOWN_CALENDARS.contains(lowerCode)) {
            // Return display name for known calendars (using code as display name)
            return new JSString(code);
        }
        if ("none".equals(fallback)) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(code);
    }

    private JSValue ofCurrency(JSContext context, String code) {
        if (!CURRENCY_CODE.matcher(code).matches()) {
            context.throwRangeError("invalid currency code for DisplayNames: " + code);
            return null;
        }
        try {
            Currency currency = Currency.getInstance(code.toUpperCase(java.util.Locale.ROOT));
            String displayName = currency.getDisplayName(locale);
            if (displayName.isEmpty()) {
                displayName = code.toUpperCase(java.util.Locale.ROOT);
            }
            // Valid currency code → always return display name (even if it equals code)
            return new JSString(displayName);
        } catch (IllegalArgumentException e) {
            if ("none".equals(fallback)) {
                return JSUndefined.INSTANCE;
            }
            return new JSString(code);
        }
    }

    private JSValue ofDateTimeField(JSContext context, String code) {
        if (!VALID_DATE_TIME_FIELDS.contains(code)) {
            context.throwRangeError("invalid dateTimeField code for DisplayNames: " + code);
            return null;
        }
        // dateTimeField display names not available via standard Java APIs, use fallback
        if ("none".equals(fallback)) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(code);
    }

    private JSValue ofLanguage(JSContext context, String code) {
        // Validate: must be a structurally valid unicode_language_id
        // unicode_language_id = unicode_language_subtag ("-" unicode_script_subtag)? ("-" unicode_region_subtag)? ("-" unicode_variant_subtag)*
        if (!isStructurallyValidLanguageTag(code)) {
            context.throwRangeError("invalid language code for DisplayNames: " + code);
            return null;
        }
        Locale codeLocale = Locale.forLanguageTag(code);
        String displayName;
        if ("standard".equals(languageDisplay)) {
            displayName = codeLocale.getDisplayName(locale);
        } else {
            // "dialect" mode - default
            displayName = codeLocale.getDisplayName(locale);
        }
        if (displayName.isEmpty() || displayName.equals(code)) {
            if ("none".equals(fallback)) {
                return JSUndefined.INSTANCE;
            }
            return new JSString(code);
        }
        return new JSString(displayName);
    }

    private JSValue ofRegion(JSContext context, String code) {
        if (!REGION_SUBTAG.matcher(code).matches()) {
            context.throwRangeError("invalid region code for DisplayNames: " + code);
            return null;
        }
        String upperCode = code.toUpperCase(java.util.Locale.ROOT);
        Locale regionLocale = new Locale.Builder().setRegion(upperCode).build();
        String displayName = regionLocale.getDisplayCountry(locale);
        if (displayName.isEmpty() || displayName.equals(upperCode)) {
            if ("none".equals(fallback)) {
                return JSUndefined.INSTANCE;
            }
            return new JSString(code);
        }
        return new JSString(displayName);
    }

    private JSValue ofScript(JSContext context, String code) {
        if (!SCRIPT_SUBTAG.matcher(code).matches()) {
            context.throwRangeError("invalid script code for DisplayNames: " + code);
            return null;
        }
        String titleCode = code.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                + code.substring(1).toLowerCase(java.util.Locale.ROOT);
        Locale scriptLocale = new Locale.Builder().setScript(titleCode).build();
        String displayName = scriptLocale.getDisplayScript(locale);
        if (displayName.isEmpty() || displayName.equals(titleCode)) {
            if ("none".equals(fallback)) {
                return JSUndefined.INSTANCE;
            }
            return new JSString(code);
        }
        return new JSString(displayName);
    }
}
