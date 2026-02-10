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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.*;

import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of Intl object and Intl.* prototype methods.
 */
public final class IntlObject {
    private static final String[] FORMAT_STYLE_VALUES = {"short", "medium", "long", "full"};

    private static List<String> canonicalizeLocaleList(JSContext context, JSValue localeValue) {
        List<String> localeTags = new ArrayList<>();
        if (localeValue == null || localeValue.isNullOrUndefined()) {
            return localeTags;
        }
        if (localeValue instanceof JSArray localeArray) {
            for (long i = 0; i < localeArray.getLength(); i++) {
                localeTags.add(JSTypeConversions.toString(context, localeArray.get(i)).value());
            }
        } else {
            localeTags.add(JSTypeConversions.toString(context, localeValue).value());
        }
        LinkedHashSet<String> canonicalLocales = new LinkedHashSet<>();
        for (String localeTag : localeTags) {
            canonicalLocales.add(parseLocaleTag(localeTag).toLanguageTag());
        }
        return new ArrayList<>(canonicalLocales);
    }

    private static String getOptionString(JSContext context, JSValue optionsValue, String key) {
        if (!(optionsValue instanceof JSObject optionsObject)) {
            return null;
        }
        JSValue rawValue = optionsObject.get(key);
        if (rawValue == null || rawValue.isNullOrUndefined()) {
            return null;
        }
        return JSTypeConversions.toString(context, rawValue).value();
    }

    public static JSValue collatorCompare(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlCollator collator)) {
            return context.throwTypeError("Intl.Collator.prototype.compare called on incompatible receiver");
        }
        String left = args.length > 0 ? JSTypeConversions.toString(context, args[0]).value() : "undefined";
        String right = args.length > 1 ? JSTypeConversions.toString(context, args[1]).value() : "undefined";
        return new JSNumber(collator.compare(left, right));
    }

    public static JSValue collatorResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlCollator collator)) {
            return context.throwTypeError("Intl.Collator.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(collator.getLocale().toLanguageTag()));
        resolvedOptions.set("sensitivity", new JSString(collator.getSensitivity()));
        resolvedOptions.set("usage", new JSString("sort"));
        return resolvedOptions;
    }

    public static JSValue createCollator(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            String sensitivity = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "sensitivity"),
                    "variant",
                    "base", "accent", "case", "variant");
            JSIntlCollator collator = new JSIntlCollator(locale, sensitivity);
            collator.setPrototype(prototype);
            return collator;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createDateTimeFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            String dateStyleOption = getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "dateStyle");
            String timeStyleOption = getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "timeStyle");
            FormatStyle dateStyle = parseFormatStyle(dateStyleOption);
            FormatStyle timeStyle = parseFormatStyle(timeStyleOption);
            if (dateStyle == null && timeStyle == null) {
                dateStyle = FormatStyle.MEDIUM;
                timeStyle = FormatStyle.MEDIUM;
            }
            JSIntlDateTimeFormat dateTimeFormat = new JSIntlDateTimeFormat(locale, dateStyle, timeStyle);
            dateTimeFormat.setPrototype(prototype);
            return dateTimeFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createListFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            String style = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "style"),
                    "long",
                    "long", "short", "narrow");
            String type = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "type"),
                    "conjunction",
                    "conjunction", "disjunction", "unit");
            JSIntlListFormat listFormat = new JSIntlListFormat(locale, style, type);
            listFormat.setPrototype(prototype);
            return listFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createLocale(JSContext context, JSObject prototype, JSValue[] args) {
        if (args.length == 0 || args[0].isNullOrUndefined()) {
            return context.throwTypeError("Intl.Locale requires a locale argument");
        }
        try {
            String localeTag = JSTypeConversions.toString(context, args[0]).value();
            Locale locale = parseLocaleTag(localeTag);
            JSIntlLocale intlLocale = new JSIntlLocale(locale, locale.toLanguageTag());
            intlLocale.setPrototype(prototype);
            return intlLocale;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createNumberFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            String style = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "style"),
                    "decimal",
                    "decimal", "currency", "percent");
            String currency = getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "currency");
            if ("currency".equals(style) && (currency == null || currency.length() != 3)) {
                throw new IllegalArgumentException("Currency code must be a 3-letter ISO 4217 code");
            }
            JSIntlNumberFormat numberFormat = new JSIntlNumberFormat(locale, style, currency);
            numberFormat.setPrototype(prototype);
            return numberFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createPluralRules(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            String type = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "type"),
                    "cardinal",
                    "cardinal", "ordinal");
            JSIntlPluralRules pluralRules = new JSIntlPluralRules(locale, type);
            pluralRules.setPrototype(prototype);
            return pluralRules;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createRelativeTimeFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            String style = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "style"),
                    "long",
                    "long", "short", "narrow");
            String numeric = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "numeric"),
                    "always",
                    "always", "auto");
            JSIntlRelativeTimeFormat relativeTimeFormat = new JSIntlRelativeTimeFormat(locale, style, numeric);
            relativeTimeFormat.setPrototype(prototype);
            return relativeTimeFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue dateTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.format called on incompatible receiver");
        }
        double epochMillis;
        if (args.length == 0 || args[0].isUndefined()) {
            epochMillis = System.currentTimeMillis();
        } else if (args[0] instanceof JSDate jsDate) {
            epochMillis = jsDate.getTimeValue();
        } else {
            epochMillis = JSTypeConversions.toNumber(context, args[0]).value();
        }
        return new JSString(dateTimeFormat.format(epochMillis));
    }

    public static JSValue dateTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(dateTimeFormat.getLocale().toLanguageTag()));
        resolvedOptions.set("timeZone", new JSString(java.time.ZoneId.systemDefault().getId()));
        if (dateTimeFormat.getDateStyle() != null) {
            resolvedOptions.set("dateStyle",
                    new JSString(dateTimeFormat.getDateStyle().name().toLowerCase(Locale.ROOT)));
        }
        if (dateTimeFormat.getTimeStyle() != null) {
            resolvedOptions.set("timeStyle",
                    new JSString(dateTimeFormat.getTimeStyle().name().toLowerCase(Locale.ROOT)));
        }
        return resolvedOptions;
    }

    public static JSValue getCanonicalLocales(JSContext context, JSValue thisArg, JSValue[] args) {
        try {
            List<String> localeList = canonicalizeLocaleList(
                    context,
                    args.length > 0 ? args[0] : JSUndefined.INSTANCE);
            JSArray localesArray = context.createJSArray();
            for (String localeTag : localeList) {
                localesArray.push(new JSString(localeTag));
            }
            return localesArray;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue listFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlListFormat listFormat)) {
            return context.throwTypeError("Intl.ListFormat.prototype.format called on incompatible receiver");
        }
        if (args.length == 0 || !(args[0] instanceof JSArray listArray)) {
            return context.throwTypeError("Intl.ListFormat.prototype.format requires an array");
        }
        List<String> values = new ArrayList<>();
        for (long i = 0; i < listArray.getLength(); i++) {
            values.add(JSTypeConversions.toString(context, listArray.get(i)).value());
        }
        return new JSString(listFormat.format(values));
    }

    public static JSValue listFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlListFormat listFormat)) {
            return context.throwTypeError("Intl.ListFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(listFormat.getLocale().toLanguageTag()));
        resolvedOptions.set("style", new JSString(listFormat.getStyle()));
        resolvedOptions.set("type", new JSString(listFormat.getType()));
        return resolvedOptions;
    }

    public static JSValue localeGetBaseName(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.baseName called on incompatible receiver");
        }
        return new JSString(intlLocale.getBaseName());
    }

    public static JSValue localeGetLanguage(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.language called on incompatible receiver");
        }
        return new JSString(intlLocale.getLanguage());
    }

    public static JSValue localeGetRegion(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.region called on incompatible receiver");
        }
        return intlLocale.getRegion().isEmpty() ? JSUndefined.INSTANCE : new JSString(intlLocale.getRegion());
    }

    public static JSValue localeGetScript(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.script called on incompatible receiver");
        }
        return intlLocale.getScript().isEmpty() ? JSUndefined.INSTANCE : new JSString(intlLocale.getScript());
    }

    public static JSValue localeToString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.toString called on incompatible receiver");
        }
        return new JSString(intlLocale.getTag());
    }

    private static String normalizeOption(String rawValue, String defaultValue, String... allowedValues) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        for (String allowedValue : allowedValues) {
            if (allowedValue.equals(rawValue)) {
                return allowedValue;
            }
        }
        throw new IllegalArgumentException("Invalid option value: " + rawValue);
    }

    public static JSValue numberFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.format called on incompatible receiver");
        }
        double value = args.length > 0 ? JSTypeConversions.toNumber(context, args[0]).value() : 0;
        return new JSString(numberFormat.format(value));
    }

    public static JSValue numberFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(numberFormat.getLocale().toLanguageTag()));
        resolvedOptions.set("style", new JSString(numberFormat.getStyle()));
        if (numberFormat.getCurrency() != null) {
            resolvedOptions.set("currency", new JSString(numberFormat.getCurrency().toUpperCase(Locale.ROOT)));
        }
        return resolvedOptions;
    }

    private static Locale parseLocaleTag(String localeTag) {
        if (localeTag == null) {
            throw new IllegalArgumentException("Invalid language tag: null");
        }
        String normalizedTag = localeTag.strip();
        if (normalizedTag.isEmpty()) {
            throw new IllegalArgumentException("Invalid language tag: " + localeTag);
        }
        Locale locale = Locale.forLanguageTag(normalizedTag);
        if (locale.getLanguage().isEmpty()) {
            throw new IllegalArgumentException("Invalid language tag: " + localeTag);
        }
        return locale;
    }

    private static FormatStyle parseFormatStyle(String rawStyle) {
        if (rawStyle == null) {
            return null;
        }
        String normalizedStyle = normalizeOption(rawStyle, null, FORMAT_STYLE_VALUES);
        return switch (normalizedStyle) {
            case "short" -> FormatStyle.SHORT;
            case "medium" -> FormatStyle.MEDIUM;
            case "long" -> FormatStyle.LONG;
            case "full" -> FormatStyle.FULL;
            default -> throw new IllegalArgumentException("Invalid format style: " + rawStyle);
        };
    }

    public static JSValue pluralRulesResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(pluralRules.getLocale().toLanguageTag()));
        resolvedOptions.set("type", new JSString(pluralRules.getType()));
        JSArray categories = context.createJSArray();
        if ("ordinal".equals(pluralRules.getType())) {
            categories.push(new JSString("one"));
            categories.push(new JSString("two"));
            categories.push(new JSString("few"));
            categories.push(new JSString("other"));
        } else {
            categories.push(new JSString("one"));
            categories.push(new JSString("other"));
        }
        resolvedOptions.set("pluralCategories", categories);
        return resolvedOptions;
    }

    public static JSValue pluralRulesSelect(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.select called on incompatible receiver");
        }
        double value = args.length > 0 ? JSTypeConversions.toNumber(context, args[0]).value() : Double.NaN;
        return new JSString(pluralRules.select(value));
    }

    public static JSValue relativeTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.format called on incompatible receiver");
        }
        if (args.length < 2) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.format requires value and unit");
        }
        try {
            double value = JSTypeConversions.toNumber(context, args[0]).value();
            String unit = JSTypeConversions.toString(context, args[1]).value();
            return new JSString(relativeTimeFormat.format(value, unit));
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue relativeTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(relativeTimeFormat.getLocale().toLanguageTag()));
        resolvedOptions.set("style", new JSString(relativeTimeFormat.getStyle()));
        resolvedOptions.set("numeric", new JSString(relativeTimeFormat.getNumeric()));
        return resolvedOptions;
    }

    private static Locale resolveLocale(JSContext context, JSValue[] args, int index) {
        if (index >= args.length || args[index].isNullOrUndefined()) {
            return Locale.getDefault();
        }
        List<String> canonicalLocales = canonicalizeLocaleList(context, args[index]);
        if (canonicalLocales.isEmpty()) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(canonicalLocales.get(0));
    }

    public static JSValue supportedLocalesOf(JSContext context, JSValue thisArg, JSValue[] args) {
        try {
            JSArray localesArray = context.createJSArray();
            if (args.length > 0) {
                for (String localeTag : canonicalizeLocaleList(context, args[0])) {
                    localesArray.push(new JSString(localeTag));
                }
            }
            return localesArray;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }
}
