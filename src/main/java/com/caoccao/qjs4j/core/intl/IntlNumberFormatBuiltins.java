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

package com.caoccao.qjs4j.core.intl;

import com.caoccao.qjs4j.core.*;
import static com.caoccao.qjs4j.core.intl.IntlNumberOptions.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Intl.NumberFormat constructor and prototype methods.
 */
final class IntlNumberFormatBuiltins {
    private static final String[] COMPACT_DISPLAY_VALUES = {"short", "long"};

    private static final String[] CURRENCY_DISPLAY_VALUES = {"code", "symbol", "narrowSymbol", "name"};

    private static final String[] CURRENCY_SIGN_VALUES = {"standard", "accounting"};

    private static final String[] NUMBER_STYLE_VALUES = {"decimal", "currency", "percent", "unit"};

    private static final String[] SIGN_DISPLAY_VALUES = {"auto", "never", "always", "exceptZero", "negative"};

    private static final String[] UNIT_DISPLAY_VALUES = {"short", "narrow", "long"};

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
            JSValue typeValue = partObject.get(PropertyKey.TYPE);
            JSValue valueValue = partObject.get(PropertyKey.VALUE);
            if (!(typeValue instanceof JSString typeString) || !(valueValue instanceof JSString valueString)) {
                continue;
            }
            target.push(IntlBuiltins.createPartObject(context, typeString.value(), valueString.value(), sourceName));
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
            if (JSTypeChecking.isUndefinedOrMissing(optionsArgument)) {
                optionsObject = IntlFormatOptions.emptyOptions(context);
            } else {
                optionsObject = JSTypeConversions.toObject(context, optionsArgument);
                if (optionsObject == null) {
                    return context.throwTypeError("Cannot convert "
                            + JSTypeConversions.toString(context, optionsArgument).value() + " to object");
                }
            }

            // Option read order must match ECMA-402.
            JSValue matcherError = IntlFormatOptions.localeMatcherError(context, optionsObject, "Intl.NumberFormat");
            if (matcherError != null) {
                return matcherError;
            }

            String numberingSystemOption = IntlFormatOptions.readStringOption(context, optionsObject,
                    PropertyKey.NUMBERING_SYSTEM);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!IntlFormatOptions.validateUnicodeType(context, "numberingSystem", numberingSystemOption)) {
                return context.getPendingException();
            }
            if (numberingSystemOption != null) {
                numberingSystemOption = numberingSystemOption.toLowerCase(Locale.ROOT);
            }

            String style = IntlFormatOptions.normalizedStringOption(context, optionsObject, PropertyKey.STYLE,
                    "decimal", NUMBER_STYLE_VALUES);
            String currency = IntlFormatOptions.readStringOption(context, optionsObject, PropertyKey.CURRENCY);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (currency != null) {
                if (!isWellFormedCurrencyCode(currency)) {
                    return context.throwRangeError("Invalid currency code: " + currency);
                }
                currency = currency.toUpperCase(Locale.ROOT);
            }

            String currencyDisplay = IntlFormatOptions.normalizedStringOption(context, optionsObject,
                    PropertyKey.CURRENCY_DISPLAY, "symbol", CURRENCY_DISPLAY_VALUES);
            String currencySign = IntlFormatOptions.normalizedStringOption(context, optionsObject,
                    PropertyKey.CURRENCY_SIGN, "standard", CURRENCY_SIGN_VALUES);
            String unit = IntlFormatOptions.readStringOption(context, optionsObject, PropertyKey.UNIT);
            String unitDisplay = IntlFormatOptions.normalizedStringOption(context, optionsObject,
                    PropertyKey.UNIT_DISPLAY, "short", UNIT_DISPLAY_VALUES);
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

            String compactDisplay = IntlFormatOptions.normalizedStringOption(context, optionsObject,
                    PropertyKey.COMPACT_DISPLAY, "short", COMPACT_DISPLAY_VALUES);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSValue useGroupingValue = optionsObject.get(PropertyKey.USE_GROUPING);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String useGroupingMode = parseUseGroupingOption(context, useGroupingValue, notation);
            String signDisplay = IntlFormatOptions.normalizedStringOption(context, optionsObject,
                    PropertyKey.SIGN_DISPLAY, "auto", SIGN_DISPLAY_VALUES);
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
        if (IntlBuiltins.SANCTIONED_SIMPLE_UNITS.contains(unitIdentifier)) {
            return true;
        }
        String[] parts = unitIdentifier.split("-per-", -1);
        if (parts.length != 2) {
            return false;
        }
        return IntlBuiltins.SANCTIONED_SIMPLE_UNITS.contains(parts[0])
                && IntlBuiltins.SANCTIONED_SIMPLE_UNITS.contains(parts[1]);
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
        return numberFormat.getOrCreateBoundFunction(context, 1, IntlNumberFormatBuiltins::numberFormatFormat);
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
        resolvedOptions.createDataProperty(PropertyKey.LOCALE, new JSString(numberFormat.getLocale().toLanguageTag()));
        resolvedOptions.createDataProperty(PropertyKey.NUMBERING_SYSTEM,
                new JSString(numberFormat.getNumberingSystem()));
        resolvedOptions.createDataProperty(PropertyKey.STYLE, new JSString(numberFormat.getStyle()));
        if ("currency".equals(numberFormat.getStyle()) && numberFormat.getCurrency() != null) {
            resolvedOptions.createDataProperty(PropertyKey.CURRENCY, new JSString(numberFormat.getCurrency()));
            resolvedOptions.createDataProperty(PropertyKey.CURRENCY_DISPLAY,
                    new JSString(numberFormat.getCurrencyDisplay()));
            resolvedOptions.createDataProperty(PropertyKey.CURRENCY_SIGN, new JSString(numberFormat.getCurrencySign()));
        }
        if ("unit".equals(numberFormat.getStyle()) && numberFormat.getUnit() != null) {
            resolvedOptions.createDataProperty(PropertyKey.UNIT, new JSString(numberFormat.getUnit()));
            resolvedOptions.createDataProperty(PropertyKey.UNIT_DISPLAY, new JSString(numberFormat.getUnitDisplay()));
        }
        resolvedOptions.createDataProperty(PropertyKey.MINIMUM_INTEGER_DIGITS,
                JSNumber.of(numberFormat.getMinimumIntegerDigits()));
        if (numberFormat.getUseSignificantDigits()) {
            defineDigitRange(resolvedOptions, true, numberFormat.getMinimumSignificantDigits(),
                    numberFormat.getMaximumSignificantDigits());
        } else {
            defineDigitRange(resolvedOptions, false, numberFormat.getMinimumFractionDigits(),
                    numberFormat.getMaximumFractionDigits());
        }
        if ("false".equals(numberFormat.getUseGroupingMode())) {
            resolvedOptions.createDataProperty(PropertyKey.USE_GROUPING, JSBoolean.FALSE);
        } else {
            resolvedOptions.createDataProperty(PropertyKey.USE_GROUPING,
                    new JSString(numberFormat.getUseGroupingMode()));
        }
        resolvedOptions.createDataProperty(PropertyKey.NOTATION, new JSString(numberFormat.getNotation()));
        if ("compact".equals(numberFormat.getNotation()) && numberFormat.getCompactDisplay() != null) {
            resolvedOptions.createDataProperty(PropertyKey.COMPACT_DISPLAY,
                    new JSString(numberFormat.getCompactDisplay()));
        }
        resolvedOptions.createDataProperty(PropertyKey.SIGN_DISPLAY, new JSString(numberFormat.getSignDisplay()));
        resolvedOptions.createDataProperty(PropertyKey.ROUNDING_INCREMENT,
                JSNumber.of(numberFormat.getRoundingIncrement()));
        resolvedOptions.createDataProperty(PropertyKey.ROUNDING_MODE, new JSString(numberFormat.getRoundingMode()));
        resolvedOptions.createDataProperty(PropertyKey.ROUNDING_PRIORITY,
                new JSString(numberFormat.getRoundingPriority()));
        resolvedOptions.createDataProperty(PropertyKey.TRAILING_ZERO_DISPLAY,
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
            result.push(IntlBuiltins.createPartObject(context, "approximatelySign", "~", "shared"));
            appendRangePartsWithSource(context, result, startParts, "shared");
            return result;
        }

        appendRangePartsWithSource(context, result, startParts, "startRange");
        String localeTag = numberFormat.getLocale().toLanguageTag();
        String separator = localeTag.startsWith("pt") ? " - " : " \u2013 ";
        result.push(IntlBuiltins.createPartObject(context, "literal", separator, "shared"));
        appendRangePartsWithSource(context, result, endParts, "endRange");
        return result;
    }

    private static String parseUseGroupingOption(JSContext context, JSValue useGroupingValue, String notation) {
        String defaultValue = "compact".equals(notation) ? "min2" : "auto";
        if (JSTypeChecking.isUndefinedOrMissing(useGroupingValue)) {
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
}
