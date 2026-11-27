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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.*;

/**
 * Intl.NumberFormat instance object.
 */
public final class JSIntlNumberFormat extends JSObject {
    private static final BigDecimal DECIMAL_ONE_HALF = new BigDecimal("0.5");
    private static final CompactScale[] ENGLISH_SCALES = {new CompactScale(1_000_000, "M", " million"),
            new CompactScale(1_000, "K", " thousand")};
    private static final CompactScale[] GERMAN_SCALES = {new CompactScale(1_000_000, "\u00A0Mio.", " Millionen"),
            new CompactScale(1_000, null, " Tausend")};
    private static final CompactScale[] INDIAN_ENGLISH_SCALES = {new CompactScale(10_000_000, "Cr", " crore"),
            new CompactScale(100_000, "L", " lakh"), new CompactScale(1_000, "K", " thousand")};
    private static final CompactScale[] JAPANESE_SCALES = {new CompactScale(100_000_000, "億"),
            new CompactScale(10_000, "万")};
    private static final CompactScale[] KOREAN_SCALES = {new CompactScale(100_000_000, "억"),
            new CompactScale(10_000, "만"), new CompactScale(1_000, "천")};
    public static final String NAME = "Intl.NumberFormat";
    private static final CompactScale[] TAIWANESE_CHINESE_SCALES = {new CompactScale(100_000_000, "億"),
            new CompactScale(10_000, "萬")};
    private JSFunction boundFormatFunction;
    private final String compactDisplay;
    private final String currency;
    private final String currencyDisplay;
    private final String currencySign;
    private final Locale locale;
    private final int maximumFractionDigits;
    private final int maximumSignificantDigits;
    private final int minimumFractionDigits;
    private final int minimumIntegerDigits;
    private final int minimumSignificantDigits;
    private final String notation;
    private final String numberingSystem;
    private final int roundingIncrement;
    private final String roundingMode;
    private final String roundingPriority;
    private final String signDisplay;

    private final String style;

    private final String trailingZeroDisplay;

    private final String unit;

    private final String unitDisplay;

    private final String useGroupingMode;

    private final boolean useSignificantDigits;

    public JSIntlNumberFormat(JSContext context, Locale locale, String style, String currency) {
        this(context, locale, style, currency, "auto", 1, -1, -1, false, 0, null, null, "auto", "halfExpand", null);
    }

    public JSIntlNumberFormat(JSContext context, Locale locale, String style, String currency, boolean useGrouping,
            int minimumIntegerDigits, int minimumFractionDigits, int maximumFractionDigits,
            boolean useSignificantDigits, int maximumSignificantDigits) {
        this(context, locale, style, currency, useGrouping ? "always" : "false", minimumIntegerDigits,
                minimumFractionDigits, maximumFractionDigits, useSignificantDigits, maximumSignificantDigits, null,
                null, "auto", "halfExpand", null);
    }

    public JSIntlNumberFormat(JSContext context, Locale locale, String style, String currency, String useGroupingMode,
            int minimumIntegerDigits, int minimumFractionDigits, int maximumFractionDigits,
            boolean useSignificantDigits, int minimumSignificantDigits, int maximumSignificantDigits, String unit,
            String unitDisplay, String signDisplay, String roundingMode, String numberingSystem, String notation,
            String compactDisplay, String currencyDisplay, String currencySign, int roundingIncrement,
            String roundingPriority, String trailingZeroDisplay) {
        super(context);
        this.locale = locale;
        this.style = style;
        this.currency = currency;
        this.useGroupingMode = useGroupingMode != null ? useGroupingMode : "auto";
        this.minimumIntegerDigits = minimumIntegerDigits;
        this.minimumFractionDigits = minimumFractionDigits;
        this.maximumFractionDigits = maximumFractionDigits;
        this.useSignificantDigits = useSignificantDigits;
        this.minimumSignificantDigits = minimumSignificantDigits;
        this.maximumSignificantDigits = maximumSignificantDigits;
        this.unit = unit;
        this.unitDisplay = unitDisplay;
        this.signDisplay = signDisplay != null ? signDisplay : "auto";
        this.roundingMode = roundingMode != null ? roundingMode : "halfExpand";
        this.numberingSystem = numberingSystem;
        this.notation = notation != null ? notation : "standard";
        this.compactDisplay = compactDisplay;
        this.currencyDisplay = currencyDisplay != null ? currencyDisplay : "symbol";
        this.currencySign = currencySign != null ? currencySign : "standard";
        this.roundingIncrement = roundingIncrement;
        this.roundingPriority = roundingPriority != null ? roundingPriority : "auto";
        this.trailingZeroDisplay = trailingZeroDisplay != null ? trailingZeroDisplay : "auto";
    }

    public JSIntlNumberFormat(JSContext context, Locale locale, String style, String currency, String useGroupingMode,
            int minimumIntegerDigits, int minimumFractionDigits, int maximumFractionDigits,
            boolean useSignificantDigits, int maximumSignificantDigits, String unit, String unitDisplay,
            String signDisplay, String roundingMode, String numberingSystem) {
        this(context, locale, style, currency, useGroupingMode, minimumIntegerDigits, minimumFractionDigits,
                maximumFractionDigits, useSignificantDigits, useSignificantDigits ? 1 : 0, maximumSignificantDigits,
                unit, unitDisplay, signDisplay, roundingMode, numberingSystem, "standard", null, "symbol", "standard",
                1, "auto", "auto");
    }

    private void appendAffixedNumberParts(List<PartToken> tokens, String body, DecimalFormatSymbols symbols,
            boolean compact) {
        int[] numericRange = findNumericCoreRange(body, symbols);
        if (numericRange[0] < 0) {
            if (compact) {
                tokens.add(new PartToken("literal", body));
            } else {
                appendStyledAffix(tokens, body, symbols);
            }
            return;
        }
        String prefix = body.substring(0, numericRange[0]);
        String numberPortion = body.substring(numericRange[0], numericRange[1] + 1);
        String suffix = body.substring(numericRange[1] + 1);
        if (compact) {
            appendLiteral(tokens, prefix);
        } else {
            appendStyledAffix(tokens, prefix, symbols);
        }
        appendNumberParts(tokens, numberPortion, symbols);
        if (compact) {
            appendAffixWithType(tokens, suffix, "compact");
        } else {
            appendStyledAffix(tokens, suffix, symbols);
        }
    }

    private void appendAffixWithType(List<PartToken> tokens, String affix, String type) {
        if (affix.isEmpty()) {
            return;
        }
        int index = 0;
        while (index < affix.length()) {
            int start = index;
            boolean whitespace = isWhitespaceLike(affix.charAt(index));
            index++;
            while (index < affix.length() && isWhitespaceLike(affix.charAt(index)) == whitespace) {
                index++;
            }
            String segment = affix.substring(start, index);
            if (whitespace) {
                tokens.add(new PartToken("literal", segment));
            } else {
                tokens.add(new PartToken(type, segment));
            }
        }
    }

    private void appendCurrencyAffix(List<PartToken> tokens, String affix, DecimalFormatSymbols symbols) {
        if (affix.isEmpty()) {
            return;
        }
        List<String> currencyCandidates = new ArrayList<>();
        if (currency != null && !currency.isEmpty()) {
            currencyCandidates.add(currency);
        }
        String currencySymbol = symbols.getCurrencySymbol();
        if (currencySymbol != null && !currencySymbol.isEmpty()) {
            currencyCandidates.add(currencySymbol);
        }
        String internationalCurrencySymbol = symbols.getInternationalCurrencySymbol();
        if (internationalCurrencySymbol != null && !internationalCurrencySymbol.isEmpty()) {
            currencyCandidates.add(internationalCurrencySymbol);
        }
        currencyCandidates.sort((left, right) -> Integer.compare(right.length(), left.length()));

        int index = 0;
        while (index < affix.length()) {
            char character = affix.charAt(index);
            if (isWhitespaceLike(character)) {
                int end = index + 1;
                while (end < affix.length() && isWhitespaceLike(affix.charAt(end))) {
                    end++;
                }
                tokens.add(new PartToken("literal", affix.substring(index, end)));
                index = end;
                continue;
            }

            String matchedCandidate = null;
            for (String candidate : currencyCandidates) {
                if (!candidate.isEmpty() && affix.startsWith(candidate, index)) {
                    matchedCandidate = candidate;
                    break;
                }
            }
            if (matchedCandidate != null) {
                tokens.add(new PartToken("currency", matchedCandidate));
                index += matchedCandidate.length();
                continue;
            }

            if (character == '(' || character == ')' || character == '[' || character == ']') {
                tokens.add(new PartToken("literal", String.valueOf(character)));
                index++;
                continue;
            }

            if (isCurrencyLikeCharacter(character)) {
                int end = index + 1;
                while (end < affix.length()) {
                    char nextCharacter = affix.charAt(end);
                    if (isWhitespaceLike(nextCharacter) || nextCharacter == '(' || nextCharacter == ')'
                            || nextCharacter == '[' || nextCharacter == ']') {
                        break;
                    }
                    if (!isCurrencyLikeCharacter(nextCharacter)) {
                        break;
                    }
                    end++;
                }
                tokens.add(new PartToken("currency", affix.substring(index, end)));
                index = end;
                continue;
            }

            int end = index + 1;
            while (end < affix.length()) {
                char nextCharacter = affix.charAt(end);
                if (isWhitespaceLike(nextCharacter) || isCurrencyLikeCharacter(nextCharacter)) {
                    break;
                }
                end++;
            }
            tokens.add(new PartToken("literal", affix.substring(index, end)));
            index = end;
        }
    }

    private void appendLiteral(List<PartToken> tokens, String text) {
        if (!text.isEmpty()) {
            tokens.add(new PartToken("literal", text));
        }
    }

    private void appendNumberParts(List<PartToken> tokens, String numberPortion, DecimalFormatSymbols symbols) {
        if (numberPortion.isEmpty()) {
            return;
        }
        char decimalSeparator = symbols.getDecimalSeparator();
        char groupingSeparator = symbols.getGroupingSeparator();
        int decimalIndex = numberPortion.indexOf(decimalSeparator);
        String integerPart;
        String fractionPart;
        if (decimalIndex >= 0) {
            integerPart = numberPortion.substring(0, decimalIndex);
            fractionPart = numberPortion.substring(decimalIndex + 1);
        } else {
            integerPart = numberPortion;
            fractionPart = null;
        }

        int cursor = 0;
        boolean emittedInteger = false;
        for (int index = 0; index < integerPart.length(); index++) {
            if (integerPart.charAt(index) == groupingSeparator) {
                if (index > cursor) {
                    tokens.add(new PartToken("integer", integerPart.substring(cursor, index)));
                    emittedInteger = true;
                }
                tokens.add(new PartToken("group", String.valueOf(groupingSeparator)));
                cursor = index + 1;
            }
        }
        if (cursor < integerPart.length()) {
            tokens.add(new PartToken("integer", integerPart.substring(cursor)));
            emittedInteger = true;
        }
        if (!emittedInteger && !integerPart.isEmpty()) {
            tokens.add(new PartToken("integer", integerPart));
        }

        if (fractionPart != null) {
            tokens.add(new PartToken("decimal", String.valueOf(decimalSeparator)));
            if (!fractionPart.isEmpty()) {
                tokens.add(new PartToken("fraction", fractionPart));
            }
        }
    }

    private void appendScientificParts(List<PartToken> tokens, String body, DecimalFormatSymbols symbols) {
        String exponentSeparator = symbols.getExponentSeparator();
        int exponentIndex = body.indexOf(exponentSeparator);
        if (exponentIndex < 0) {
            appendNumberParts(tokens, body, symbols);
            return;
        }

        String mantissa = body.substring(0, exponentIndex);
        String exponent = body.substring(exponentIndex + exponentSeparator.length());
        appendNumberParts(tokens, mantissa, symbols);
        tokens.add(new PartToken("exponentSeparator", exponentSeparator));
        if (!exponent.isEmpty()) {
            char first = exponent.charAt(0);
            if (first == '-' || first == symbols.getMinusSign()) {
                tokens.add(new PartToken("exponentMinusSign", String.valueOf(first)));
                exponent = exponent.substring(1);
            } else if (first == '+') {
                exponent = exponent.substring(1);
            }
        }
        if (!exponent.isEmpty()) {
            tokens.add(new PartToken("exponentInteger", exponent));
        }
    }

    private void appendStyledAffix(List<PartToken> tokens, String affix, DecimalFormatSymbols symbols) {
        switch (style) {
            case "currency" -> appendCurrencyAffix(tokens, affix, symbols);
            case "percent" -> appendAffixWithType(tokens, affix, "percentSign");
            case "unit" -> appendUnitAffix(tokens, affix, symbols);
        }
    }

    private void appendUnitAffix(List<PartToken> tokens, String affix, DecimalFormatSymbols symbols) {
        if (affix.isEmpty()) {
            return;
        }
        int segmentStart = 0;
        for (int index = 0; index < affix.length(); index++) {
            char character = affix.charAt(index);
            if (character == '-' || character == symbols.getMinusSign() || character == '+') {
                appendUnitText(tokens, affix.substring(segmentStart, index));
                if (character == '+') {
                    tokens.add(new PartToken("plusSign", String.valueOf(character)));
                } else {
                    tokens.add(new PartToken("minusSign", String.valueOf(character)));
                }
                segmentStart = index + 1;
            }
        }
        appendUnitText(tokens, affix.substring(segmentStart));
    }

    private void appendUnitText(List<PartToken> tokens, String text) {
        if (text.isEmpty()) {
            return;
        }
        int leadingWhitespaceEnd = 0;
        while (leadingWhitespaceEnd < text.length() && isWhitespaceLike(text.charAt(leadingWhitespaceEnd))) {
            leadingWhitespaceEnd++;
        }
        int trailingWhitespaceStart = text.length();
        while (trailingWhitespaceStart > leadingWhitespaceEnd
                && isWhitespaceLike(text.charAt(trailingWhitespaceStart - 1))) {
            trailingWhitespaceStart--;
        }
        if (leadingWhitespaceEnd > 0) {
            tokens.add(new PartToken("literal", text.substring(0, leadingWhitespaceEnd)));
        }
        if (trailingWhitespaceStart > leadingWhitespaceEnd) {
            tokens.add(new PartToken("unit", text.substring(leadingWhitespaceEnd, trailingWhitespaceStart)));
        }
        if (trailingWhitespaceStart < text.length()) {
            tokens.add(new PartToken("literal", text.substring(trailingWhitespaceStart)));
        }
    }

    private BigDecimal applyFractionRounding(BigDecimal value) {
        int fractionDigits = Math.max(0, maximumFractionDigits);
        BigDecimal increment = BigDecimal.ONE.scaleByPowerOfTen(-fractionDigits)
                .multiply(BigDecimal.valueOf(roundingIncrement));
        if (increment.compareTo(BigDecimal.ZERO) <= 0) {
            return value;
        }
        return roundToIncrement(value, increment, roundingMode);
    }

    private String applyNumberingSystemDigits(String text) {
        return IntlNumberingSystem.localize(text, numberingSystem, false);
    }

    private BigDecimal applyRounding(BigDecimal value) {
        if ("scientific".equals(notation) || "engineering".equals(notation) || "compact".equals(notation)) {
            return value;
        }
        if (useSignificantDigits) {
            if ("morePrecision".equals(roundingPriority) || "lessPrecision".equals(roundingPriority)) {
                if (shouldUseFractionRoundingForPriority()) {
                    return applyFractionRounding(value);
                }
                return applySignificantRounding(value);
            }
            return applySignificantRounding(value);
        }
        return applyFractionRounding(value);
    }

    private String applySignDisplayAndDigits(String formatted, boolean isNegativeAfterRounding, boolean isRoundedZero,
            boolean shouldAddPlus) {
        String result = formatted;
        if ("never".equals(signDisplay)) {
            result = removeLeadingSign(result);
        } else if ("negative".equals(signDisplay) && isRoundedZero && isNegativeAfterRounding) {
            result = removeLeadingSign(result);
        } else if ("exceptZero".equals(signDisplay) && isRoundedZero) {
            result = removeLeadingSign(result);
        }
        if (shouldAddPlus) {
            result = "+" + removeLeadingSign(result);
        }
        return applyNumberingSystemDigits(result);
    }

    private BigDecimal applySignificantRounding(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return value;
        }
        int significantDigits = Math.max(1, maximumSignificantDigits);
        BigDecimal absoluteValue = value.abs();
        int exponent = absoluteValue.precision() - absoluteValue.scale() - 1;
        int incrementExponent = exponent - significantDigits + 1;
        BigDecimal increment = BigDecimal.ONE.scaleByPowerOfTen(incrementExponent);
        BigDecimal rounded = roundToIncrement(value, increment, roundingMode);
        if ("morePrecision".equals(roundingPriority) || "lessPrecision".equals(roundingPriority)) {
            BigDecimal fractionRounded = applyFractionRounding(value);
            int roundedScale = Math.max(0, rounded.stripTrailingZeros().scale());
            int fractionScale = Math.max(0, fractionRounded.stripTrailingZeros().scale());
            if ("morePrecision".equals(roundingPriority)) {
                if (fractionScale > roundedScale) {
                    return fractionRounded;
                }
            } else {
                if (fractionScale < roundedScale) {
                    return fractionRounded;
                }
            }
        }
        return rounded;
    }

    private String applyUnitStyle(String numberPortion) {
        if ("percent".equals(unit)) {
            return numberPortion + "%";
        }
        String display = unitDisplay != null ? unitDisplay : "short";
        String englishUnit = JSIntlDurationFormat.formatEnglishTimeUnit(numberPortion, unit, display, locale);
        if (englishUnit != null) {
            return englishUnit;
        }
        if (!"kilometer-per-hour".equals(unit)) {
            return numberPortion + " " + unit;
        }
        String languageTag = locale.toLanguageTag();
        if (languageTag.startsWith("de")) {
            if ("long".equals(display)) {
                return numberPortion + " Kilometer pro Stunde";
            }
            return numberPortion + " km/h";
        }
        if (languageTag.startsWith("ja")) {
            if ("long".equals(display)) {
                return "時速 " + numberPortion + " キロメートル";
            }
            if ("narrow".equals(display)) {
                return numberPortion + "km/h";
            }
            return numberPortion + " km/h";
        }
        if (languageTag.startsWith("ko")) {
            if ("long".equals(display)) {
                return "시속 " + numberPortion + "킬로미터";
            }
            return numberPortion + "km/h";
        }
        if (languageTag.startsWith("zh-TW")) {
            if ("long".equals(display)) {
                return "每小時 " + numberPortion + " 公里";
            }
            if ("narrow".equals(display)) {
                return numberPortion + "公里/小時";
            }
            return numberPortion + " 公里/小時";
        }
        if ("long".equals(display)) {
            return numberPortion + " kilometers per hour";
        }
        if ("narrow".equals(display)) {
            return numberPortion + "km/h";
        }
        return numberPortion + " km/h";
    }

    private int computeCompactPlainMaximumFractionDigits(BigDecimal absoluteValue) {
        if (absoluteValue.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        double numeric = absoluteValue.doubleValue();
        if (numeric <= 0) {
            return 0;
        }
        int exponent = (int) Math.floor(Math.log10(numeric));
        int maxFractionDigits = 1 - exponent;
        if (maxFractionDigits < 0) {
            return 0;
        }
        if (maxFractionDigits > 20) {
            return 20;
        }
        return maxFractionDigits;
    }

    private int computeIntegerDigits(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ONE) < 0) {
            return 1;
        }
        return normalized.precision() - normalized.scale();
    }

    private int computeIntegerDigitsForSignificant(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            return 1;
        }
        return normalized.precision() - normalized.scale();
    }

    private Locale createCurrencySignLocale() {
        if ("currency".equals(style) && "accounting".equals(currencySign)) {
            try {
                return new Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("cf", "account").build();
            } catch (RuntimeException ignored) {
                return locale;
            }
        }
        return locale;
    }

    private NumberFormat createFormatterForNotation(boolean enableGrouping) {
        Locale formatLocale = createCurrencySignLocale();
        NumberFormat format;
        if ("compact".equals(notation)) {
            NumberFormat.Style compactStyle = "long".equals(compactDisplay)
                    ? NumberFormat.Style.LONG
                    : NumberFormat.Style.SHORT;
            format = NumberFormat.getCompactNumberInstance(formatLocale, compactStyle);
        } else if ("scientific".equals(notation) || "engineering".equals(notation)) {
            DecimalFormat decimalFormat = (DecimalFormat) NumberFormat.getNumberInstance(formatLocale);
            if ("engineering".equals(notation)) {
                decimalFormat.applyPattern("##0.###E0");
            } else {
                decimalFormat.applyPattern("0.###E0");
            }
            format = decimalFormat;
        } else if ("currency".equals(style)) {
            format = NumberFormat.getCurrencyInstance(formatLocale);
        } else if ("percent".equals(style)) {
            format = NumberFormat.getPercentInstance(formatLocale);
        } else {
            format = NumberFormat.getNumberInstance(formatLocale);
        }

        if (format instanceof DecimalFormat decimalFormat) {
            if ("currency".equals(style) && currency != null && !currency.isBlank()) {
                try {
                    decimalFormat.setCurrency(Currency.getInstance(currency.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            decimalFormat.setGroupingUsed(enableGrouping);
            decimalFormat.setMinimumIntegerDigits(minimumIntegerDigits);
            if (!"compact".equals(notation) && !useSignificantDigits) {
                decimalFormat.setMinimumFractionDigits(Math.max(0, minimumFractionDigits));
                decimalFormat.setMaximumFractionDigits(Math.max(0, maximumFractionDigits));
            }
        } else {
            format.setGroupingUsed(enableGrouping);
            format.setMinimumIntegerDigits(minimumIntegerDigits);
            if (!useSignificantDigits) {
                format.setMinimumFractionDigits(Math.max(0, minimumFractionDigits));
                format.setMaximumFractionDigits(Math.max(0, maximumFractionDigits));
            }
        }

        if (format instanceof DecimalFormat decimalFormat) {
            decimalFormat.setRoundingMode(mapToJavaRoundingMode(roundingMode));
        }
        return format;
    }

    private String ensureIndianGrouping(String text, boolean enableGrouping, int minimumGroupingDigits) {
        if (!locale.toLanguageTag().startsWith("en-IN")) {
            return text;
        }
        if (!enableGrouping) {
            return text.replace(",", "");
        }
        int integerStart = -1;
        int integerEndExclusive = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                if (integerStart < 0) {
                    integerStart = i;
                }
                integerEndExclusive = i + 1;
            } else if (integerStart >= 0 && c == ',') {
                integerEndExclusive = i + 1;
            } else if (integerStart >= 0) {
                break;
            }
        }
        if (integerStart < 0) {
            return text;
        }
        String integerText = text.substring(integerStart, integerEndExclusive).replace(",", "");
        if (integerText.length() < minimumGroupingDigits) {
            return text.substring(0, integerStart) + integerText + text.substring(integerEndExclusive);
        }
        String groupedInteger = groupIndian(integerText);
        return text.substring(0, integerStart) + groupedInteger + text.substring(integerEndExclusive);
    }

    private int[] findNumericCoreRange(String text, DecimalFormatSymbols symbols) {
        int start = -1;
        int end = -1;
        boolean seenDigit = false;
        char decimalSeparator = symbols.getDecimalSeparator();
        char groupingSeparator = symbols.getGroupingSeparator();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            boolean numericCharacter = Character.isDigit(character) || character == decimalSeparator
                    || character == groupingSeparator;
            if (numericCharacter) {
                if (start < 0) {
                    start = index;
                }
                if (Character.isDigit(character)) {
                    seenDigit = true;
                }
                end = index;
            } else if (start >= 0 && seenDigit) {
                break;
            } else if (start >= 0) {
                start = -1;
                end = -1;
            }
        }
        return new int[]{start, end};
    }

    public String format(BigInteger value) {
        return formatFinite(new BigDecimal(value), value.signum() < 0);
    }

    public String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            boolean addPlus = shouldAddPlusForSpecialValue(value);
            NumberFormat format = createFormatterForNotation(isGroupingActiveForMagnitude(0));
            String special = format.format(value);
            if ("never".equals(signDisplay)) {
                special = removeLeadingSign(special);
            }
            if (addPlus) {
                special = "+" + removeLeadingSign(special);
            }
            return applyNumberingSystemDigits(special);
        }
        return formatFinite(BigDecimal.valueOf(value), value < 0 || isOriginalNegativeZero(value));
    }

    public String format(String stringValue) {
        if (stringValue == null) {
            return format(Double.NaN);
        }
        try {
            BigDecimal original = new BigDecimal(stringValue.trim());
            return formatFinite(original, stringValue.startsWith("-") || original.signum() < 0);
        } catch (RuntimeException e) {
            try {
                return format(Double.parseDouble(stringValue));
            } catch (RuntimeException ignored) {
                return format(Double.NaN);
            }
        }
    }

    private String formatCompactNumber(BigDecimal absoluteValue, boolean displayNegative, boolean groupingEnabled,
            int minimumGroupingDigits) {
        String localeTag = locale.toLanguageTag();
        CompactSpec compactSpec = resolveCompactSpec(localeTag, absoluteValue);
        if (compactSpec == null) {
            return formatCompactPlain(absoluteValue, groupingEnabled, minimumGroupingDigits, displayNegative);
        }

        BigDecimal scaled = absoluteValue.divide(compactSpec.divisor(), MathContext.DECIMAL128);
        int maximumFractionDigits;
        if (scaled.compareTo(BigDecimal.TEN) < 0) {
            maximumFractionDigits = 1;
        } else {
            maximumFractionDigits = 0;
        }
        String scaledText = formatLocalizedNumber(scaled, 0, maximumFractionDigits, false);
        String compactText = scaledText + compactSpec.suffix();
        if (displayNegative) {
            return "-" + compactText;
        }
        return compactText;
    }

    private String formatCompactPlain(BigDecimal absoluteValue, boolean groupingEnabled, int minimumGroupingDigits,
            boolean displayNegative) {
        int maximumFractionDigits = computeCompactPlainMaximumFractionDigits(absoluteValue);
        String plainText = formatLocalizedNumber(absoluteValue, 0, maximumFractionDigits, groupingEnabled);
        if ("en-IN".equals(locale.toLanguageTag()) || locale.toLanguageTag().startsWith("en-IN")) {
            plainText = ensureIndianGrouping(plainText, groupingEnabled, minimumGroupingDigits);
        }
        if (displayNegative) {
            return "-" + plainText;
        }
        return plainText;
    }

    private String formatFinite(BigDecimal original, boolean originalNegative) {
        BigDecimal valueForRounding = original;
        if ("percent".equals(style)) {
            valueForRounding = original.multiply(BigDecimal.valueOf(100));
        }
        BigDecimal roundedForRoundingDomain = applyRounding(valueForRounding);
        BigDecimal rounded = roundedForRoundingDomain;
        if ("percent".equals(style)) {
            rounded = roundedForRoundingDomain.divide(BigDecimal.valueOf(100), MathContext.DECIMAL128);
        }
        boolean roundedZero = rounded.compareTo(BigDecimal.ZERO) == 0;
        boolean roundedNegative = rounded.signum() < 0 || (roundedZero && originalNegative);
        boolean displayNegative = shouldDisplayNegativeSign(roundedNegative, roundedZero);
        boolean displayPlus = shouldDisplayPositivePlus(displayNegative, roundedZero);

        String formattedNumber = formatRoundedNumber(rounded, displayNegative, roundedZero, originalNegative);
        String result = applySignDisplayAndDigits(formattedNumber, roundedNegative, roundedZero, displayPlus);
        if ("unit".equals(style) && unit != null) {
            return applyUnitStyle(result);
        }
        return result;
    }

    private String formatLocalizedNumber(BigDecimal value, int minimumFractionDigits, int maximumFractionDigits,
            boolean grouping) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        if (numberFormat instanceof DecimalFormat decimalFormat) {
            decimalFormat.setGroupingUsed(grouping);
            decimalFormat.setMinimumFractionDigits(Math.max(0, minimumFractionDigits));
            decimalFormat.setMaximumFractionDigits(Math.max(0, maximumFractionDigits));
            decimalFormat.setRoundingMode(mapToJavaRoundingMode(roundingMode));
            return decimalFormat.format(value);
        }
        numberFormat.setGroupingUsed(grouping);
        numberFormat.setMinimumFractionDigits(Math.max(0, minimumFractionDigits));
        numberFormat.setMaximumFractionDigits(Math.max(0, maximumFractionDigits));
        return numberFormat.format(value);
    }

    private String formatRoundedNumber(BigDecimal rounded, boolean displayNegative, boolean roundedZero,
            boolean originalNegative) {
        BigDecimal absoluteRounded = rounded.abs();
        int minimumGroupingDigits = "min2".equals(useGroupingMode) ? 5 : 1;
        int integerDigits = computeIntegerDigits(absoluteRounded);
        boolean groupingEnabled = isGroupingActiveForMagnitude(integerDigits);
        if ("min2".equals(useGroupingMode) && absoluteRounded.compareTo(new BigDecimal("1000")) < 0) {
            groupingEnabled = false;
        }
        if ("compact".equals(notation) && "decimal".equals(style)) {
            return formatCompactNumber(absoluteRounded, displayNegative, groupingEnabled, minimumGroupingDigits);
        }

        NumberFormat formatter = createFormatterForNotation(groupingEnabled);
        boolean useFractionMode = useSignificantDigits
                && ("morePrecision".equals(roundingPriority) || "lessPrecision".equals(roundingPriority))
                && shouldUseFractionRoundingForPriority();
        if (formatter instanceof DecimalFormat decimalFormat && useSignificantDigits && !"compact".equals(notation)
                && !useFractionMode) {
            int integerDigitsForSignificant = computeIntegerDigitsForSignificant(absoluteRounded);
            int minimumFractionDigits = Math.max(0, minimumSignificantDigits - integerDigitsForSignificant);
            int maximumFractionDigits = Math.max(0, maximumSignificantDigits - integerDigitsForSignificant);
            decimalFormat.setMinimumFractionDigits(Math.min(minimumFractionDigits, 100));
            decimalFormat
                    .setMaximumFractionDigits(Math.min(Math.max(maximumFractionDigits, minimumFractionDigits), 100));
        } else if (formatter instanceof DecimalFormat decimalFormat && useFractionMode) {
            decimalFormat.setMinimumFractionDigits(Math.max(0, minimumFractionDigits));
            decimalFormat.setMaximumFractionDigits(Math.max(0, maximumFractionDigits));
        }
        Object valueToFormat;
        if (displayNegative) {
            if (roundedZero && originalNegative) {
                valueToFormat = -0.0d;
            } else {
                valueToFormat = absoluteRounded.negate();
            }
        } else {
            valueToFormat = absoluteRounded;
        }

        String formatted = formatter.format(valueToFormat);
        if ("en-IN".equals(locale.toLanguageTag()) || locale.toLanguageTag().startsWith("en-IN")) {
            formatted = ensureIndianGrouping(formatted, groupingEnabled, minimumGroupingDigits);
        }
        return formatted;
    }

    public JSArray formatToParts(JSContext context, double value) {
        return formatToPartsFromFormatted(context, format(value));
    }

    public JSArray formatToParts(JSContext context, String stringValue) {
        return formatToPartsFromFormatted(context, format(stringValue));
    }

    JSArray formatToPartsFromFormatted(JSContext context, String formatted) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(createCurrencySignLocale());
        List<PartToken> tokens = new ArrayList<>();
        String body = formatted;

        if (!body.isEmpty()) {
            char first = body.charAt(0);
            if (first == '+') {
                tokens.add(new PartToken("plusSign", String.valueOf(first)));
                body = body.substring(1);
            } else if (first == '-' || first == symbols.getMinusSign()) {
                tokens.add(new PartToken("minusSign", String.valueOf(first)));
                body = body.substring(1);
            }
        }

        String nanText = symbols.getNaN();
        String infinityText = symbols.getInfinity();
        if (body.equals(nanText) || body.equals("NaN")) {
            tokens.add(new PartToken("nan", body));
        } else if (body.equals(infinityText) || body.equals("∞") || body.equals("Infinity")) {
            tokens.add(new PartToken("infinity", body));
        } else if ("scientific".equals(notation) || "engineering".equals(notation)) {
            appendScientificParts(tokens, body, symbols);
        } else if ("unit".equals(style) || "percent".equals(style) || "currency".equals(style)) {
            appendAffixedNumberParts(tokens, body, symbols, false);
        } else if ("compact".equals(notation)) {
            appendAffixedNumberParts(tokens, body, symbols, true);
        } else {
            appendNumberParts(tokens, body, symbols);
        }

        JSArray result = context.createJSArray();
        for (int index = 0; index < tokens.size(); index++) {
            PartToken token = tokens.get(index);
            JSObject part = context.createJSObject();
            part.set("type", new JSString(token.type()));
            part.set("value", new JSString(token.value()));
            result.set(index, part);
        }
        return result;
    }

    public JSFunction getBoundFormatFunction() {
        return boundFormatFunction;
    }

    public String getCompactDisplay() {
        return compactDisplay;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCurrencyDisplay() {
        return currencyDisplay;
    }

    public String getCurrencySign() {
        return currencySign;
    }

    public Locale getLocale() {
        return locale;
    }

    public int getMaximumFractionDigits() {
        return maximumFractionDigits;
    }

    public int getMaximumSignificantDigits() {
        return maximumSignificantDigits;
    }

    public int getMinimumFractionDigits() {
        return minimumFractionDigits;
    }

    public int getMinimumIntegerDigits() {
        return minimumIntegerDigits;
    }

    public int getMinimumSignificantDigits() {
        return minimumSignificantDigits;
    }

    public String getNotation() {
        return notation;
    }

    public String getNumberingSystem() {
        return numberingSystem;
    }

    public int getRoundingIncrement() {
        return roundingIncrement;
    }

    public String getRoundingMode() {
        return roundingMode;
    }

    public String getRoundingPriority() {
        return roundingPriority;
    }

    public String getSignDisplay() {
        return signDisplay;
    }

    public String getStyle() {
        return style;
    }

    public String getTrailingZeroDisplay() {
        return trailingZeroDisplay;
    }

    public String getUnit() {
        return unit;
    }

    public String getUnitDisplay() {
        return unitDisplay;
    }

    public boolean getUseGrouping() {
        return !"false".equals(useGroupingMode);
    }

    public String getUseGroupingMode() {
        return useGroupingMode;
    }

    public boolean getUseSignificantDigits() {
        return useSignificantDigits;
    }

    private String groupIndian(String digits) {
        if (digits.length() <= 3) {
            return digits;
        }
        String lastThree = digits.substring(digits.length() - 3);
        String leading = digits.substring(0, digits.length() - 3);
        List<String> groups = new ArrayList<>();
        while (leading.length() > 2) {
            groups.add(0, leading.substring(leading.length() - 2));
            leading = leading.substring(0, leading.length() - 2);
        }
        if (!leading.isEmpty()) {
            groups.add(0, leading);
        }
        groups.add(lastThree);
        return String.join(",", groups);
    }

    private boolean isCurrencyLikeCharacter(char character) {
        int type = Character.getType(character);
        if (Character.isLetterOrDigit(character)) {
            return true;
        }
        return type == Character.CURRENCY_SYMBOL || type == Character.OTHER_SYMBOL || type == Character.MATH_SYMBOL;
    }

    private boolean isGroupingActiveForMagnitude(int magnitudeDigits) {
        if ("false".equals(useGroupingMode)) {
            return false;
        }
        if ("min2".equals(useGroupingMode)) {
            return magnitudeDigits >= 5;
        }
        if ("always".equals(useGroupingMode)) {
            return true;
        }
        if ("compact".equals(notation)) {
            return "min2".equals(useGroupingMode) ? magnitudeDigits >= 5 : !"false".equals(useGroupingMode);
        }
        return true;
    }

    private boolean isOriginalNegativeZero(double value) {
        return Double.doubleToRawLongBits(value) == Long.MIN_VALUE;
    }

    private boolean isWhitespaceLike(char character) {
        return Character.isWhitespace(character) || Character.getType(character) == Character.SPACE_SEPARATOR;
    }

    private RoundingMode mapToJavaRoundingMode(String mode) {
        return switch (mode) {
            case "ceil" -> RoundingMode.CEILING;
            case "floor" -> RoundingMode.FLOOR;
            case "expand" -> RoundingMode.UP;
            case "trunc" -> RoundingMode.DOWN;
            case "halfCeil" -> RoundingMode.HALF_UP;
            case "halfFloor" -> RoundingMode.HALF_DOWN;
            case "halfTrunc" -> RoundingMode.HALF_DOWN;
            case "halfEven" -> RoundingMode.HALF_EVEN;
            default -> RoundingMode.HALF_UP;
        };
    }

    private String removeLeadingSign(String text) {
        if (text.startsWith("-")) {
            return text.substring(1);
        }
        if (text.startsWith("+")) {
            return text.substring(1);
        }
        if (text.startsWith("(") && text.endsWith(")")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private CompactSpec resolveCompactSpec(String localeTag, BigDecimal absoluteValue) {
        CompactScale[] scales;
        if (localeTag.startsWith("en-IN")) {
            scales = INDIAN_ENGLISH_SCALES;
        } else if (localeTag.startsWith("en")) {
            scales = ENGLISH_SCALES;
        } else if (localeTag.startsWith("de")) {
            scales = GERMAN_SCALES;
        } else if (localeTag.startsWith("ja")) {
            scales = JAPANESE_SCALES;
        } else if (localeTag.startsWith("ko")) {
            scales = KOREAN_SCALES;
        } else if (localeTag.startsWith("zh-TW")) {
            scales = TAIWANESE_CHINESE_SCALES;
        } else {
            return null;
        }
        for (CompactScale scale : scales) {
            if (absoluteValue.compareTo(scale.divisor()) >= 0) {
                return "long".equals(compactDisplay) ? scale.longForm() : scale.shortForm();
            }
        }
        return null;
    }

    private BigDecimal roundHalfCeilOrFloor(BigDecimal value, String mode) {
        BigDecimal sign = BigDecimal.valueOf(value.signum());
        BigDecimal absolute = value.abs();
        BigDecimal floor = absolute.setScale(0, RoundingMode.DOWN);
        BigDecimal fraction = absolute.subtract(floor);
        int cmp = fraction.compareTo(DECIMAL_ONE_HALF);
        BigDecimal roundedMagnitude;
        if (cmp < 0) {
            roundedMagnitude = floor;
        } else if (cmp > 0) {
            roundedMagnitude = floor.add(BigDecimal.ONE);
        } else {
            if ("halfCeil".equals(mode)) {
                if (value.signum() >= 0) {
                    roundedMagnitude = floor.add(BigDecimal.ONE);
                } else {
                    roundedMagnitude = floor;
                }
            } else {
                if (value.signum() >= 0) {
                    roundedMagnitude = floor;
                } else {
                    roundedMagnitude = floor.add(BigDecimal.ONE);
                }
            }
        }
        if (sign.signum() < 0) {
            return roundedMagnitude.negate();
        }
        return roundedMagnitude;
    }

    private BigDecimal roundToIncrement(BigDecimal value, BigDecimal increment, String mode) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return value;
        }
        BigDecimal quotient = value.divide(increment, MathContext.DECIMAL128);
        BigDecimal roundedQuotient = roundToInteger(quotient, mode);
        return roundedQuotient.multiply(increment).stripTrailingZeros();
    }

    private BigDecimal roundToInteger(BigDecimal value, String mode) {
        switch (mode) {
            case "ceil" :
                return value.setScale(0, RoundingMode.CEILING);
            case "floor" :
                return value.setScale(0, RoundingMode.FLOOR);
            case "expand" :
                return value.setScale(0, RoundingMode.UP);
            case "trunc" :
                return value.setScale(0, RoundingMode.DOWN);
            case "halfExpand" :
                return value.setScale(0, RoundingMode.HALF_UP);
            case "halfTrunc" :
                return value.setScale(0, RoundingMode.HALF_DOWN);
            case "halfEven" :
                return value.setScale(0, RoundingMode.HALF_EVEN);
            case "halfCeil" :
            case "halfFloor" :
                return roundHalfCeilOrFloor(value, mode);
            default :
                return value.setScale(0, RoundingMode.HALF_UP);
        }
    }

    public void setBoundFormatFunction(JSFunction boundFormatFunction) {
        this.boundFormatFunction = boundFormatFunction;
    }

    private boolean shouldAddPlusForSpecialValue(double value) {
        if ("always".equals(signDisplay)) {
            return value >= 0 || Double.isNaN(value);
        }
        if ("exceptZero".equals(signDisplay)) {
            return value > 0;
        }
        return false;
    }

    private boolean shouldDisplayNegativeSign(boolean negative, boolean roundedZero) {
        if (!negative) {
            return false;
        }
        switch (signDisplay) {
            case "never" :
                return false;
            case "exceptZero" :
                return !roundedZero;
            case "negative" :
                return !roundedZero;
            default :
                return true;
        }
    }

    private boolean shouldDisplayPositivePlus(boolean displayNegative, boolean roundedZero) {
        if (displayNegative) {
            return false;
        }
        if ("always".equals(signDisplay)) {
            return true;
        }
        if ("exceptZero".equals(signDisplay)) {
            return !roundedZero;
        }
        return false;
    }

    private boolean shouldUseFractionRoundingForPriority() {
        if (!useSignificantDigits) {
            return true;
        }
        if ("auto".equals(roundingPriority)) {
            return false;
        }
        if (maximumFractionDigits >= 0 && maximumSignificantDigits > 0) {
            if ("lessPrecision".equals(roundingPriority)) {
                return maximumFractionDigits < maximumSignificantDigits;
            }
            return maximumFractionDigits >= maximumSignificantDigits;
        }
        return false;
    }

    private record CompactScale(BigDecimal divisor, CompactSpec shortForm, CompactSpec longForm) {
        private CompactScale(long divisor, String suffix) {
            this(BigDecimal.valueOf(divisor), suffix, suffix);
        }

        private CompactScale(long divisor, String shortSuffix, String longSuffix) {
            this(BigDecimal.valueOf(divisor), shortSuffix, longSuffix);
        }

        private CompactScale(BigDecimal divisor, String shortSuffix, String longSuffix) {
            this(divisor, shortSuffix == null ? null : new CompactSpec(divisor, shortSuffix),
                    new CompactSpec(divisor, longSuffix));
        }
    }

    private record CompactSpec(BigDecimal divisor, String suffix) {
    }

    private record PartToken(String type, String value) {
    }
}
