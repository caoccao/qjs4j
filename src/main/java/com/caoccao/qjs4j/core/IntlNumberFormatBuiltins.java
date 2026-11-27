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

import java.math.BigDecimal;
import java.util.*;

/**
 * NumberFormat and PluralRules built-ins and their shared digit and rounding options.
 */
final class IntlNumberFormatBuiltins {
    private static final String[] COMPACT_DISPLAY_VALUES = {"short", "long"};

    private static final String[] CURRENCY_DISPLAY_VALUES = {"code", "symbol", "narrowSymbol", "name"};

    private static final String[] CURRENCY_SIGN_VALUES = {"standard", "accounting"};

    private static final String[] NOTATION_VALUES = {"standard", "scientific", "engineering", "compact"};

    private static final String[] NUMBER_STYLE_VALUES = {"decimal", "currency", "percent", "unit"};

    private static final String[] ROUNDING_MODE_VALUES = {"ceil", "floor", "expand", "trunc", "halfCeil", "halfFloor",
            "halfExpand", "halfTrunc", "halfEven"};

    private static final String[] ROUNDING_PRIORITY_VALUES = {"auto", "morePrecision", "lessPrecision"};

    private static final String[] SIGN_DISPLAY_VALUES = {"auto", "never", "always", "exceptZero", "negative"};

    private static final String[] TRAILING_ZERO_DISPLAY_VALUES = {"auto", "stripIfInteger"};

    private static final String[] UNIT_DISPLAY_VALUES = {"short", "narrow", "long"};

    private static final Set<Integer> VALID_ROUNDING_INCREMENTS = Set.of(1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500,
            1000, 2000, 2500, 5000);

    private IntlNumberFormatBuiltins() {
    }

    private static void appendRangePartsWithSource(JSContext context, JSArray target, JSArray sourceParts,
            String sourceName) {
        int length = (int) sourceParts.getLength();
        for (int index = 0; index < length; index++) {
            JSValue partValue = sourceParts.get(index);
            if (!(partValue instanceof JSObject partObject)) {
                continue;
            }
            JSValue typeValue = partObject.get(PropertyKey.fromString("type"));
            JSValue valueValue = partObject.get(PropertyKey.VALUE);
            if (!(typeValue instanceof JSString typeString) || !(valueValue instanceof JSString valueString)) {
                continue;
            }
            target.push(JSIntlObject.createPartObject(context, typeString.value(), valueString.value(), sourceName));
        }
    }

    private static String commonLeadingNonNumericPrefix(String start, String end) {
        int limit = Math.min(start.length(), end.length());
        int i = 0;
        while (i < limit) {
            char a = start.charAt(i);
            char b = end.charAt(i);
            if (a != b) {
                break;
            }
            if (Character.isDigit(a) || a == '.' || a == ',' || a == '-') {
                break;
            }
            i++;
        }
        if (i <= 0) {
            return "";
        }
        return start.substring(0, i);
    }

    static JSValue createNumberFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            IntlFormatOptions.ConstructorLocale constructor = IntlFormatOptions.ConstructorLocale.resolve(context,
                    prototype, args, "NumberFormat");
            if (constructor == null) {
                return JSUndefined.INSTANCE;
            }
            JSValue optionsArgument = JSValue.argumentOrUndefined(args, 1);
            JSObject optionsObject;
            if (optionsArgument instanceof JSUndefined || optionsArgument == null) {
                optionsObject = IntlFormatOptions.emptyOptions(context);
            } else {
                optionsObject = JSTypeConversions.toObject(context, optionsArgument);
                if (optionsObject == null) {
                    return context.throwTypeError("Cannot convert "
                            + JSTypeConversions.toString(context, optionsArgument).value() + " to object");
                }
            }

            // Option read order must match ECMA-402.
            String localeMatcher = IntlFormatOptions.readStringOption(context, optionsObject, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!IntlFormatOptions.isValidLocaleMatcher(localeMatcher)) {
                return context.throwRangeError("Value " + localeMatcher
                        + " out of range for Intl.NumberFormat options property localeMatcher");
            }

            String numberingSystemOption = IntlFormatOptions.readStringOption(context, optionsObject,
                    "numberingSystem");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!IntlFormatOptions.validateUnicodeType(context, "numberingSystem", numberingSystemOption)) {
                return context.getPendingException();
            }
            if (numberingSystemOption != null) {
                numberingSystemOption = numberingSystemOption.toLowerCase(Locale.ROOT);
            }

            String style = IntlFormatOptions.normalizedStringOption(context, optionsObject, "style", "decimal",
                    NUMBER_STYLE_VALUES);
            String currency = IntlFormatOptions.readStringOption(context, optionsObject, "currency");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (currency != null) {
                if (!isWellFormedCurrencyCode(currency)) {
                    return context.throwRangeError("Invalid currency code: " + currency);
                }
                currency = currency.toUpperCase(Locale.ROOT);
            }

            String currencyDisplay = IntlFormatOptions.normalizedStringOption(context, optionsObject, "currencyDisplay",
                    "symbol", CURRENCY_DISPLAY_VALUES);
            String currencySign = IntlFormatOptions.normalizedStringOption(context, optionsObject, "currencySign",
                    "standard", CURRENCY_SIGN_VALUES);
            String unit = IntlFormatOptions.readStringOption(context, optionsObject, "unit");
            String unitDisplay = IntlFormatOptions.normalizedStringOption(context, optionsObject, "unitDisplay",
                    "short", UNIT_DISPLAY_VALUES);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            if ("currency".equals(style) && currency == null) {
                return context.throwTypeError("Currency code is required with currency style");
            }
            if ("unit".equals(style) && unit == null) {
                return context.throwTypeError("Unit is required with unit style");
            }
            if (unit != null) {
                if (!isWellFormedUnitIdentifier(unit)) {
                    return context.throwRangeError("Invalid unit argument: " + unit);
                }
            }

            DigitOptions digits = DigitOptions.read(context, optionsObject);
            if (digits == null) {
                return JSUndefined.INSTANCE;
            }
            String notation = digits.notation();
            int minimumIntegerDigits = digits.minimumIntegerDigits();
            DigitRange fractionDigits = digits.fractionDigits();

            DigitRange significantDigits = DigitRange.readSignificant(context, optionsObject);
            if (significantDigits == null) {
                return JSUndefined.INSTANCE;
            }

            RoundingOptions rounding = readRoundingOptions(context, optionsObject, true);
            if (rounding == null) {
                return JSUndefined.INSTANCE;
            }

            String compactDisplay = IntlFormatOptions.normalizedStringOption(context, optionsObject, "compactDisplay",
                    "short", COMPACT_DISPLAY_VALUES);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSValue useGroupingValue = optionsObject.get(PropertyKey.fromString("useGrouping"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String useGroupingMode = parseUseGroupingOption(context, useGroupingValue, notation);
            String signDisplay = IntlFormatOptions.normalizedStringOption(context, optionsObject, "signDisplay", "auto",
                    SIGN_DISPLAY_VALUES);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            int currencyDigits = 2;
            if ("currency".equals(style) && currency != null) {
                currencyDigits = getCurrencyDigits(currency);
            }
            int minimumFractionDefault;
            int maximumFractionDefault;
            if ("currency".equals(style) && "standard".equals(notation)) {
                minimumFractionDefault = currencyDigits;
                maximumFractionDefault = currencyDigits;
            } else if ("percent".equals(style)) {
                minimumFractionDefault = 0;
                maximumFractionDefault = 0;
            } else if ("currency".equals(style) && "compact".equals(notation)) {
                minimumFractionDefault = 0;
                maximumFractionDefault = 0;
            } else {
                minimumFractionDefault = 0;
                maximumFractionDefault = 3;
            }

            boolean useSignificantDigits = significantDigits.specified();
            if (useSignificantDigits) {
                significantDigits = significantDigits.resolveSignificant();
            }
            fractionDigits = useSignificantDigits && "auto".equals(rounding.roundingPriority())
                    ? new DigitRange(minimumFractionDefault, maximumFractionDefault)
                    : fractionDigits.resolveFraction(minimumFractionDefault, maximumFractionDefault);

            if (rounding.roundingIncrement() != 1) {
                if (useSignificantDigits || "morePrecision".equals(rounding.roundingPriority())
                        || "lessPrecision".equals(rounding.roundingPriority())) {
                    return context.throwTypeError("Invalid roundingIncrement for current rounding settings");
                }
                if (!fractionDigits.minimum().equals(fractionDigits.maximum())) {
                    return context.throwRangeError(
                            "maximumFractionDigits and minimumFractionDigits must be equal when roundingIncrement is used");
                }
            }

            IntlFormatOptions.NumberingLocale numbering = IntlFormatOptions.NumberingLocale
                    .resolve(constructor.locale(), numberingSystemOption);
            JSIntlNumberFormat numberFormat = new JSIntlNumberFormat(context, numbering.locale(), style, currency,
                    useGroupingMode, minimumIntegerDigits, fractionDigits.minimum(), fractionDigits.maximum(),
                    useSignificantDigits, useSignificantDigits ? significantDigits.minimum() : 0,
                    useSignificantDigits ? significantDigits.maximum() : 0, unit, unitDisplay, signDisplay,
                    rounding.roundingMode(), numbering.numberingSystem(), notation,
                    "compact".equals(notation) ? compactDisplay : null, currencyDisplay, currencySign,
                    rounding.roundingIncrement(), rounding.roundingPriority(), rounding.trailingZeroDisplay());
            return constructor.finish(numberFormat);
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    static JSValue createPluralRules(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            IntlFormatOptions.ConstructorLocale constructor = IntlFormatOptions.ConstructorLocale.resolve(context,
                    prototype, args, "PluralRules");
            if (constructor == null) {
                return JSUndefined.INSTANCE;
            }
            JSValue options = IntlFormatOptions.coerceOptionsObject(context, JSValue.argumentOrUndefined(args, 1));
            if (context.hasPendingException() || !(options instanceof JSObject optionsObject)) {
                return options;
            }

            String localeMatcher = IntlFormatOptions.normalizedStringOption(context, optionsObject, "localeMatcher",
                    "best fit", "lookup", "best fit");
            String type = IntlFormatOptions.normalizedStringOption(context, optionsObject, "type", "cardinal",
                    "cardinal", "ordinal");
            DigitOptions digits = DigitOptions.read(context, optionsObject);
            if (digits == null) {
                return JSUndefined.INSTANCE;
            }
            String notation = digits.notation();
            int minimumIntegerDigits = digits.minimumIntegerDigits();
            DigitRange fractionDigits = digits.fractionDigits();

            boolean hasFractionDigits = fractionDigits.specified();
            fractionDigits = fractionDigits.resolveFraction(0, 3);

            DigitRange significantDigits = DigitRange.readSignificant(context, optionsObject);
            if (significantDigits == null) {
                return JSUndefined.INSTANCE;
            }
            if (significantDigits.specified()) {
                significantDigits = significantDigits.resolveSignificant();
            } else if ("compact".equals(notation) && !hasFractionDigits) {
                significantDigits = new DigitRange(1, 2);
            }

            if (readRoundingOptions(context, optionsObject, false) == null) {
                return JSUndefined.INSTANCE;
            }
            JSIntlPluralRules pluralRules = new JSIntlPluralRules(context, constructor.locale(), type, notation,
                    minimumIntegerDigits, fractionDigits.minimum(), fractionDigits.maximum(),
                    significantDigits.minimum(), significantDigits.maximum());
            return constructor.finish(pluralRules);
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    private static void defineDigitRange(JSObject options, boolean significant, int minimum, int maximum) {
        JSIntlObject.createDataProperty(options, significant ? "minimumSignificantDigits" : "minimumFractionDigits",
                JSNumber.of(minimum));
        JSIntlObject.createDataProperty(options, significant ? "maximumSignificantDigits" : "maximumFractionDigits",
                JSNumber.of(maximum));
    }

    private static int findFirstDigitIndex(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (Character.isDigit(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int findLastDigitIndex(String text) {
        for (int index = text.length() - 1; index >= 0; index--) {
            if (Character.isDigit(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static String formatIntlNumberValue(JSContext context, JSIntlNumberFormat numberFormat, JSValue value) {
        if (value instanceof JSBigInt bigInt) {
            return numberFormat.format(bigInt.value());
        }
        if (value instanceof JSString jsString) {
            return numberFormat.format(jsString.value());
        }
        double numeric = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return "";
        }
        return numberFormat.format(numeric);
    }

    private static JSValue formatNumberRange(JSContext context, JSValue thisArg, JSValue[] args, boolean toParts) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            String method = toParts ? "formatRangeToParts" : "formatRange";
            return context.throwTypeError("Intl.NumberFormat.prototype." + method + " called on incompatible receiver");
        }
        JSValue startValue = JSValue.argumentOrUndefined(args, 0);
        JSValue endValue = JSValue.argumentOrUndefined(args, 1);
        if (startValue instanceof JSUndefined || endValue instanceof JSUndefined) {
            return context.throwTypeError("start and end must not be undefined");
        }
        if (isNaNRangeNumberValue(context, startValue) || isNaNRangeNumberValue(context, endValue)) {
            return context.throwRangeError("start or end is NaN");
        }
        String startFormatted = formatIntlNumberValue(context, numberFormat, startValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String endFormatted = formatIntlNumberValue(context, numberFormat, endValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (toParts) {
            return numberRangeParts(context, numberFormat, startValue, endValue, startFormatted.equals(endFormatted));
        }
        if (startFormatted.equals(endFormatted)) {
            return new JSString("~" + startFormatted);
        }

        String localeTag = numberFormat.getLocale().toLanguageTag();
        String separator = localeTag.startsWith("pt") ? " - " : " \u2013 ";
        if ("currency".equals(numberFormat.getStyle())) {
            int startDigitStart = findFirstDigitIndex(startFormatted);
            int startDigitEnd = findLastDigitIndex(startFormatted);
            int endDigitStart = findFirstDigitIndex(endFormatted);
            int endDigitEnd = findLastDigitIndex(endFormatted);
            if (startDigitStart >= 0 && startDigitEnd >= startDigitStart && endDigitStart >= 0
                    && endDigitEnd >= endDigitStart) {
                String startPrefix = startFormatted.substring(0, startDigitStart);
                String startCore = startFormatted.substring(startDigitStart, startDigitEnd + 1);
                String startSuffix = startFormatted.substring(startDigitEnd + 1);
                String endPrefix = endFormatted.substring(0, endDigitStart);
                String endCore = endFormatted.substring(endDigitStart, endDigitEnd + 1);
                String endSuffix = endFormatted.substring(endDigitEnd + 1);

                if (!startSuffix.isEmpty() && startSuffix.equals(endSuffix)) {
                    if (startCore.startsWith("+") && endCore.startsWith("+")) {
                        endCore = endCore.substring(1);
                    }
                    return new JSString(startPrefix + startCore + separator + endCore + startSuffix);
                }

                if ("always".equals(numberFormat.getSignDisplay()) && !startPrefix.isEmpty()
                        && startPrefix.equals(endPrefix) && startPrefix.startsWith("+")) {
                    return new JSString(startPrefix + startCore + "\u2013" + endCore + startSuffix);
                }
            }
        }

        if ("currency".equals(numberFormat.getStyle())) {
            return new JSString(startFormatted + separator + endFormatted);
        }
        if (localeTag.startsWith("pt")) {
            return new JSString(startFormatted + " - " + endFormatted);
        }
        return new JSString(startFormatted + "\u2013" + endFormatted);
    }

    private static int getCurrencyDigits(String currencyCode) {
        try {
            Currency currency = Currency.getInstance(currencyCode);
            int digits = currency.getDefaultFractionDigits();
            if (digits < 0) {
                return 2;
            }
            return digits;
        } catch (IllegalArgumentException e) {
            return 2;
        }
    }

    private static boolean isNaNRangeNumberValue(JSContext context, JSValue value) {
        if (value instanceof JSBigInt) {
            return false;
        }
        if (value instanceof JSString jsString) {
            String text = jsString.value().trim();
            if (text.isEmpty()) {
                return false;
            }
            try {
                new BigDecimal(text);
                return false;
            } catch (RuntimeException ignored) {
                try {
                    double parsed = Double.parseDouble(text);
                    return Double.isNaN(parsed);
                } catch (RuntimeException parseError) {
                    return true;
                }
            }
        }
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        return !context.hasPendingException() && Double.isNaN(numericValue);
    }

    private static boolean isWellFormedCurrencyCode(String currencyCode) {
        if (currencyCode.length() != 3) {
            return false;
        }
        for (int index = 0; index < currencyCode.length(); index++) {
            char character = currencyCode.charAt(index);
            if (!((character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWellFormedUnitIdentifier(String unitIdentifier) {
        if (!unitIdentifier.equals(unitIdentifier.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (JSIntlObject.SANCTIONED_SIMPLE_UNITS.contains(unitIdentifier)) {
            return true;
        }
        String[] parts = unitIdentifier.split("-per-", -1);
        if (parts.length != 2) {
            return false;
        }
        return JSIntlObject.SANCTIONED_SIMPLE_UNITS.contains(parts[0])
                && JSIntlObject.SANCTIONED_SIMPLE_UNITS.contains(parts[1]);
    }

    static JSValue numberFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.format called on incompatible receiver");
        }
        JSValue value = JSValue.argumentOrUndefined(args, 0);
        String formatted = formatIntlNumberValue(context, numberFormat, value);
        return context.hasPendingException() ? JSUndefined.INSTANCE : new JSString(formatted);
    }

    static JSValue numberFormatFormatGetter(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.format called on incompatible receiver");
        }
        JSFunction cachedBoundFormatFunction = numberFormat.getBoundFormatFunction();
        if (cachedBoundFormatFunction != null) {
            return cachedBoundFormatFunction;
        }
        JSNativeFunction boundFormatFunction = new JSNativeFunction(context, "", 1,
                (childContext, thisValue, formatArgs) -> numberFormatFormat(childContext, numberFormat, formatArgs));
        context.transferPrototype(boundFormatFunction, JSFunction.NAME);
        numberFormat.setBoundFormatFunction(boundFormatFunction);
        return boundFormatFunction;
    }

    static JSValue numberFormatFormatRange(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatNumberRange(context, thisArg, args, false);
    }

    static JSValue numberFormatFormatRangeToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatNumberRange(context, thisArg, args, true);
    }

    static JSValue numberFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.formatToParts called on incompatible receiver");
        }
        if (args.length == 0) {
            return numberFormat.formatToParts(context, Double.NaN);
        }
        JSValue arg = args[0];
        if (arg instanceof JSBigInt jsBigInt) {
            return numberFormat.formatToParts(context, jsBigInt.value().toString());
        }
        if (arg instanceof JSString str) {
            return numberFormat.formatToParts(context, str.value());
        }
        double numValue = JSTypeConversions.toNumber(context, arg).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return numberFormat.formatToParts(context, numValue);
    }

    static JSValue numberFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context
                    .throwTypeError("Intl.NumberFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        JSIntlObject.createDataProperty(resolvedOptions, "locale",
                new JSString(numberFormat.getLocale().toLanguageTag()));
        JSIntlObject.createDataProperty(resolvedOptions, "numberingSystem",
                new JSString(numberFormat.getNumberingSystem()));
        JSIntlObject.createDataProperty(resolvedOptions, "style", new JSString(numberFormat.getStyle()));
        if ("currency".equals(numberFormat.getStyle()) && numberFormat.getCurrency() != null) {
            JSIntlObject.createDataProperty(resolvedOptions, "currency", new JSString(numberFormat.getCurrency()));
            JSIntlObject.createDataProperty(resolvedOptions, "currencyDisplay",
                    new JSString(numberFormat.getCurrencyDisplay()));
            JSIntlObject.createDataProperty(resolvedOptions, "currencySign",
                    new JSString(numberFormat.getCurrencySign()));
        }
        if ("unit".equals(numberFormat.getStyle()) && numberFormat.getUnit() != null) {
            JSIntlObject.createDataProperty(resolvedOptions, "unit", new JSString(numberFormat.getUnit()));
            JSIntlObject.createDataProperty(resolvedOptions, "unitDisplay",
                    new JSString(numberFormat.getUnitDisplay()));
        }
        JSIntlObject.createDataProperty(resolvedOptions, "minimumIntegerDigits",
                JSNumber.of(numberFormat.getMinimumIntegerDigits()));
        if (numberFormat.getUseSignificantDigits()) {
            defineDigitRange(resolvedOptions, true, numberFormat.getMinimumSignificantDigits(),
                    numberFormat.getMaximumSignificantDigits());
        } else {
            defineDigitRange(resolvedOptions, false, numberFormat.getMinimumFractionDigits(),
                    numberFormat.getMaximumFractionDigits());
        }
        if ("false".equals(numberFormat.getUseGroupingMode())) {
            JSIntlObject.createDataProperty(resolvedOptions, "useGrouping", JSBoolean.FALSE);
        } else {
            JSIntlObject.createDataProperty(resolvedOptions, "useGrouping",
                    new JSString(numberFormat.getUseGroupingMode()));
        }
        JSIntlObject.createDataProperty(resolvedOptions, "notation", new JSString(numberFormat.getNotation()));
        if ("compact".equals(numberFormat.getNotation()) && numberFormat.getCompactDisplay() != null) {
            JSIntlObject.createDataProperty(resolvedOptions, "compactDisplay",
                    new JSString(numberFormat.getCompactDisplay()));
        }
        JSIntlObject.createDataProperty(resolvedOptions, "signDisplay", new JSString(numberFormat.getSignDisplay()));
        JSIntlObject.createDataProperty(resolvedOptions, "roundingIncrement",
                JSNumber.of(numberFormat.getRoundingIncrement()));
        JSIntlObject.createDataProperty(resolvedOptions, "roundingMode", new JSString(numberFormat.getRoundingMode()));
        JSIntlObject.createDataProperty(resolvedOptions, "roundingPriority",
                new JSString(numberFormat.getRoundingPriority()));
        JSIntlObject.createDataProperty(resolvedOptions, "trailingZeroDisplay",
                new JSString(numberFormat.getTrailingZeroDisplay()));
        return resolvedOptions;
    }

    private static JSValue numberRangeParts(JSContext context, JSIntlNumberFormat numberFormat, JSValue startValue,
            JSValue endValue, boolean equalAfterFormatting) {
        JSValue startPartsValue = numberFormatFormatToParts(context, numberFormat, new JSValue[]{startValue});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSValue endPartsValue = numberFormatFormatToParts(context, numberFormat, new JSValue[]{endValue});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(startPartsValue instanceof JSArray startParts) || !(endPartsValue instanceof JSArray endParts)) {
            return context.throwTypeError("formatToParts did not return an array");
        }

        JSArray result = context.createJSArray();
        if (equalAfterFormatting) {
            result.push(JSIntlObject.createPartObject(context, "approximatelySign", "~", "shared"));
            appendRangePartsWithSource(context, result, startParts, "shared");
            return result;
        }

        appendRangePartsWithSource(context, result, startParts, "startRange");
        String localeTag = numberFormat.getLocale().toLanguageTag();
        String separator = localeTag.startsWith("pt") ? " - " : " \u2013 ";
        result.push(JSIntlObject.createPartObject(context, "literal", separator, "shared"));
        appendRangePartsWithSource(context, result, endParts, "endRange");
        return result;
    }

    private static String parseUseGroupingOption(JSContext context, JSValue useGroupingValue, String notation) {
        String defaultValue = "compact".equals(notation) ? "min2" : "auto";
        if (useGroupingValue == null || useGroupingValue instanceof JSUndefined) {
            return defaultValue;
        }
        if (useGroupingValue instanceof JSBoolean jsBoolean) {
            if (jsBoolean.value()) {
                return "always";
            }
            return "false";
        }
        if (useGroupingValue instanceof JSNull) {
            return "false";
        }
        if (useGroupingValue instanceof JSString jsString) {
            String value = jsString.value();
            if ("auto".equals(value) || "always".equals(value) || "min2".equals(value)) {
                return value;
            }
            if ("true".equals(value) || "false".equals(value)) {
                return defaultValue;
            }
            if (value.isEmpty()) {
                return "false";
            }
            context.throwRangeError("Invalid option value: " + value);
            return defaultValue;
        }
        if (useGroupingValue instanceof JSNumber jsNumber) {
            double number = jsNumber.value();
            if (number == 0) {
                return "false";
            }
            context.throwRangeError("Invalid option value: " + useGroupingValue);
            return defaultValue;
        }
        boolean booleanValue = JSTypeConversions.toBoolean(useGroupingValue).value();
        if (!booleanValue) {
            return "false";
        }
        context.throwRangeError(
                "Invalid option value: " + JSTypeConversions.toString(context, useGroupingValue).value());
        return defaultValue;
    }

    // ---- New Locale prototype methods ----

    static JSValue pluralRulesResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        JSIntlObject.createDataProperty(resolvedOptions, "locale",
                new JSString(pluralRules.getLocale().toLanguageTag()));
        JSIntlObject.createDataProperty(resolvedOptions, "type", new JSString(pluralRules.getType()));
        JSIntlObject.createDataProperty(resolvedOptions, "notation", new JSString(pluralRules.getNotation()));
        JSIntlObject.createDataProperty(resolvedOptions, "minimumIntegerDigits",
                JSNumber.of(pluralRules.getMinimumIntegerDigits()));
        if (pluralRules.getMinimumSignificantDigits() != null) {
            defineDigitRange(resolvedOptions, true, pluralRules.getMinimumSignificantDigits(),
                    pluralRules.getMaximumSignificantDigits());
        } else {
            defineDigitRange(resolvedOptions, false, pluralRules.getMinimumFractionDigits(),
                    pluralRules.getMaximumFractionDigits());
        }
        JSArray categories = context.createJSArray();
        for (String category : pluralRules.getPluralCategories()) {
            categories.push(new JSString(category));
        }
        JSIntlObject.createDataProperty(resolvedOptions, "pluralCategories", categories);
        return resolvedOptions;
    }

    static JSValue pluralRulesSelect(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.select called on incompatible receiver");
        }
        double value = args.length > 0 ? JSTypeConversions.toNumber(context, args[0]).value() : Double.NaN;
        return new JSString(pluralRules.select(value));
    }

    static JSValue pluralRulesSelectRange(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.selectRange called on incompatible receiver");
        }
        JSValue startValue = JSValue.argumentOrUndefined(args, 0);
        JSValue endValue = JSValue.argumentOrUndefined(args, 1);
        if (startValue instanceof JSUndefined || endValue instanceof JSUndefined) {
            return context.throwTypeError("Intl.PluralRules.prototype.selectRange requires start and end");
        }
        double start = JSTypeConversions.toNumber(context, startValue).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        double end = JSTypeConversions.toNumber(context, endValue).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (Double.isNaN(start) || Double.isNaN(end)) {
            return context.throwRangeError("NaN is not allowed in Intl.PluralRules.prototype.selectRange");
        }
        try {
            return new JSString(pluralRules.selectRange(start, end));
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    private static int readMinimumIntegerDigits(JSContext context, JSObject options) {
        Integer digits = IntlFormatOptions.getOptionIntegerOrUndefined(context, options, "minimumIntegerDigits", 1, 21);
        return digits == null ? 1 : digits;
    }

    private static RoundingOptions readRoundingOptions(JSContext context, JSObject optionsObject,
            boolean validateIncrement) {
        Integer increment = IntlFormatOptions.getOptionIntegerOrUndefined(context, optionsObject, "roundingIncrement",
                1, 5000);
        if (context.hasPendingException()) {
            return null;
        }
        int roundingIncrement = increment == null ? 1 : increment;
        if (validateIncrement && !VALID_ROUNDING_INCREMENTS.contains(roundingIncrement)) {
            context.throwRangeError("Invalid roundingIncrement value: " + roundingIncrement);
            return null;
        }
        String mode = IntlFormatOptions.normalizedStringOption(context, optionsObject, "roundingMode", "halfExpand",
                ROUNDING_MODE_VALUES);
        String priority = IntlFormatOptions.normalizedStringOption(context, optionsObject, "roundingPriority", "auto",
                ROUNDING_PRIORITY_VALUES);
        String trailingZero = IntlFormatOptions.normalizedStringOption(context, optionsObject, "trailingZeroDisplay",
                "auto", TRAILING_ZERO_DISPLAY_VALUES);
        return context.hasPendingException()
                ? null
                : new RoundingOptions(roundingIncrement, mode, priority, trailingZero);
    }

    private record DigitOptions(String notation, int minimumIntegerDigits, DigitRange fractionDigits) {
        static DigitOptions read(JSContext context, JSObject optionsObject) {
            String notation = IntlFormatOptions.normalizedStringOption(context, optionsObject, "notation", "standard",
                    NOTATION_VALUES);
            if (context.hasPendingException()) {
                return null;
            }
            int minimum = readMinimumIntegerDigits(context, optionsObject);
            if (context.hasPendingException()) {
                return null;
            }
            DigitRange fraction = DigitRange.readFraction(context, optionsObject);
            return fraction == null ? null : new DigitOptions(notation, minimum, fraction);
        }
    }

    private record DigitRange(Integer minimum, Integer maximum) {
        static DigitRange readFraction(JSContext context, JSObject options) {
            return read(context, options, "minimumFractionDigits", "maximumFractionDigits", 0, 100);
        }

        static DigitRange readSignificant(JSContext context, JSObject options) {
            return read(context, options, "minimumSignificantDigits", "maximumSignificantDigits", 1, 21);
        }

        static DigitRange read(JSContext context, JSObject options, String minKey, String maxKey, int low, int high) {
            Integer min = IntlFormatOptions.getOptionIntegerOrUndefined(context, options, minKey, low, high);
            if (context.hasPendingException()) {
                return null;
            }
            Integer max = IntlFormatOptions.getOptionIntegerOrUndefined(context, options, maxKey, low, high);
            return context.hasPendingException() ? null : new DigitRange(min, max);
        }

        boolean specified() {
            return minimum != null || maximum != null;
        }

        DigitRange resolveFraction(int defaultMin, int defaultMax) {
            int min = minimum != null ? minimum : maximum == null ? defaultMin : Math.min(defaultMin, maximum);
            int max = maximum != null ? maximum : Math.max(min, defaultMax);
            if (min > max) {
                throw new IllegalArgumentException("minimumFractionDigits value is out of range");
            }
            return new DigitRange(min, max);
        }

        DigitRange resolveSignificant() {
            int min = minimum == null ? 1 : minimum;
            int max = maximum == null ? 21 : maximum;
            if (min > max) {
                throw new IllegalArgumentException("minimumSignificantDigits value is out of range");
            }
            return new DigitRange(min, max);
        }
    }

    private record RoundingOptions(int roundingIncrement, String roundingMode, String roundingPriority,
            String trailingZeroDisplay) {
    }
}
