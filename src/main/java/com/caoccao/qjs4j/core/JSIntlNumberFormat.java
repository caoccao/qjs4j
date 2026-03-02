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
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Intl.NumberFormat instance object.
 */
public final class JSIntlNumberFormat extends JSObject {
    private static final Map<String, Integer> CONTIGUOUS_ZERO_CODE_POINTS = Map.ofEntries(
            Map.entry("adlm", 0x1E950),
            Map.entry("ahom", 0x11730),
            Map.entry("arab", 0x660),
            Map.entry("arabext", 0x6F0),
            Map.entry("bali", 0x1B50),
            Map.entry("beng", 0x9E6),
            Map.entry("bhks", 0x11C50),
            Map.entry("brah", 0x11066),
            Map.entry("cakm", 0x11136),
            Map.entry("cham", 0xAA50),
            Map.entry("deva", 0x966),
            Map.entry("diak", 0x11950),
            Map.entry("fullwide", 0xFF10),
            Map.entry("gong", 0x11DA0),
            Map.entry("gonm", 0x11D50),
            Map.entry("gujr", 0xAE6),
            Map.entry("guru", 0xA66),
            Map.entry("hmng", 0x16B50),
            Map.entry("hmnp", 0x1E140),
            Map.entry("java", 0xA9D0),
            Map.entry("kali", 0xA900),
            Map.entry("kawi", 0x11F50),
            Map.entry("khmr", 0x17E0),
            Map.entry("knda", 0xCE6),
            Map.entry("lana", 0x1A80),
            Map.entry("lanatham", 0x1A90),
            Map.entry("laoo", 0xED0),
            Map.entry("latn", 0x30),
            Map.entry("lepc", 0x1C40),
            Map.entry("limb", 0x1946),
            Map.entry("nagm", 0x1E4F0),
            Map.entry("mathbold", 0x1D7CE),
            Map.entry("mathdbl", 0x1D7D8),
            Map.entry("mathmono", 0x1D7F6),
            Map.entry("mathsanb", 0x1D7EC),
            Map.entry("mathsans", 0x1D7E2),
            Map.entry("mlym", 0xD66),
            Map.entry("modi", 0x11650),
            Map.entry("mong", 0x1810),
            Map.entry("mroo", 0x16A60),
            Map.entry("mtei", 0xABF0),
            Map.entry("mymr", 0x1040),
            Map.entry("mymrshan", 0x1090),
            Map.entry("mymrtlng", 0xA9F0),
            Map.entry("newa", 0x11450),
            Map.entry("nkoo", 0x7C0),
            Map.entry("olck", 0x1C50),
            Map.entry("orya", 0xB66),
            Map.entry("osma", 0x104A0),
            Map.entry("rohg", 0x10D30),
            Map.entry("saur", 0xA8D0),
            Map.entry("segment", 0x1FBF0),
            Map.entry("shrd", 0x111D0),
            Map.entry("sind", 0x112F0),
            Map.entry("sinh", 0xDE6),
            Map.entry("sora", 0x110F0),
            Map.entry("sund", 0x1BB0),
            Map.entry("takr", 0x116C0),
            Map.entry("talu", 0x19D0),
            Map.entry("tamldec", 0xBE6),
            Map.entry("tnsa", 0x16AC0),
            Map.entry("telu", 0xC66),
            Map.entry("thai", 0xE50),
            Map.entry("tibt", 0xF20),
            Map.entry("tirh", 0x114D0),
            Map.entry("vaii", 0xA620),
            Map.entry("wara", 0x118E0),
            Map.entry("wcho", 0x1E2F0)
    );
    private static final BigDecimal DECIMAL_ONE_HALF = new BigDecimal("0.5");
    private static final String[] HANIDEC_DIGITS = {"〇", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
    public static final String NAME = "Intl.NumberFormat";
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

    public JSIntlNumberFormat(Locale locale, String style, String currency) {
        this(locale, style, currency, "auto", 1, -1, -1, false, 0,
                null, null, "auto", "halfExpand", null);
    }

    public JSIntlNumberFormat(Locale locale, String style, String currency,
                              boolean useGrouping, int minimumIntegerDigits,
                              int minimumFractionDigits, int maximumFractionDigits,
                              boolean useSignificantDigits, int maximumSignificantDigits) {
        this(locale, style, currency, useGrouping ? "always" : "false", minimumIntegerDigits,
                minimumFractionDigits, maximumFractionDigits, useSignificantDigits, maximumSignificantDigits,
                null, null, "auto", "halfExpand", null);
    }

    public JSIntlNumberFormat(Locale locale, String style, String currency,
                              String useGroupingMode, int minimumIntegerDigits,
                              int minimumFractionDigits, int maximumFractionDigits,
                              boolean useSignificantDigits, int maximumSignificantDigits,
                              String unit, String unitDisplay, String signDisplay,
                              String roundingMode, String numberingSystem) {
        this(locale, style, currency,
                useGroupingMode, minimumIntegerDigits, minimumFractionDigits, maximumFractionDigits,
                useSignificantDigits, useSignificantDigits ? 1 : 0, maximumSignificantDigits,
                unit, unitDisplay, signDisplay, roundingMode, numberingSystem,
                "standard", null, "symbol", "standard", 1, "auto", "auto");
    }

    public JSIntlNumberFormat(Locale locale, String style, String currency,
                              String useGroupingMode, int minimumIntegerDigits,
                              int minimumFractionDigits, int maximumFractionDigits,
                              boolean useSignificantDigits, int minimumSignificantDigits, int maximumSignificantDigits,
                              String unit, String unitDisplay, String signDisplay,
                              String roundingMode, String numberingSystem,
                              String notation, String compactDisplay,
                              String currencyDisplay, String currencySign,
                              int roundingIncrement, String roundingPriority,
                              String trailingZeroDisplay) {
        super();
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

    private BigDecimal applyFractionRounding(BigDecimal value) {
        int fractionDigits = Math.max(0, maximumFractionDigits);
        BigDecimal increment = BigDecimal.ONE.scaleByPowerOfTen(-fractionDigits)
                .multiply(BigDecimal.valueOf(roundingIncrement));
        if (increment.compareTo(BigDecimal.ZERO) <= 0) {
            return value;
        }
        return roundToIncrement(value, increment, roundingMode);
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

    private String applySignDisplayAndDigits(String formatted, boolean isNegativeAfterRounding,
                                             boolean isRoundedZero, boolean shouldAddPlus) {
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

    private String applyNumberingSystemDigits(String text) {
        if (numberingSystem == null || "latn".equals(numberingSystem)) {
            return text;
        }
        Integer zeroCodePoint = CONTIGUOUS_ZERO_CODE_POINTS.get(numberingSystem);
        if (zeroCodePoint != null) {
            StringBuilder sb = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c >= '0' && c <= '9') {
                    sb.append(Character.toChars(zeroCodePoint + (c - '0')));
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        if ("hanidec".equals(numberingSystem)) {
            StringBuilder sb = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c >= '0' && c <= '9') {
                    sb.append(HANIDEC_DIGITS[c - '0']);
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return text;
    }

    private String applyUnitStyle(String numberPortion) {
        if (!"kilometer-per-hour".equals(unit)) {
            return numberPortion + " " + unit;
        }
        String languageTag = locale.toLanguageTag();
        String display = unitDisplay != null ? unitDisplay : "short";
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
        BigDecimal original = BigDecimal.valueOf(value);
        BigDecimal valueForRounding = original;
        if ("percent".equals(style)) {
            valueForRounding = original.multiply(BigDecimal.valueOf(100));
        }
        BigDecimal roundedForRoundingDomain = applyRounding(valueForRounding);
        BigDecimal rounded = roundedForRoundingDomain;
        if ("percent".equals(style)) {
            rounded = roundedForRoundingDomain.divide(BigDecimal.valueOf(100), MathContext.DECIMAL128);
        }
        boolean originalNegative = value < 0 || isOriginalNegativeZero(value);
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

    public String format(BigInteger value) {
        BigDecimal original = new BigDecimal(value);
        BigDecimal valueForRounding = original;
        if ("percent".equals(style)) {
            valueForRounding = original.multiply(BigDecimal.valueOf(100));
        }
        BigDecimal roundedForRoundingDomain = applyRounding(valueForRounding);
        BigDecimal rounded = roundedForRoundingDomain;
        if ("percent".equals(style)) {
            rounded = roundedForRoundingDomain.divide(BigDecimal.valueOf(100), MathContext.DECIMAL128);
        }
        boolean originalNegative = value.signum() < 0;
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

    public String format(String stringValue) {
        if (stringValue == null) {
            return format(Double.NaN);
        }
        try {
            BigDecimal original = new BigDecimal(stringValue.trim());
            BigDecimal valueForRounding = original;
            if ("percent".equals(style)) {
                valueForRounding = original.multiply(BigDecimal.valueOf(100));
            }
            BigDecimal roundedForRoundingDomain = applyRounding(valueForRounding);
            BigDecimal rounded = roundedForRoundingDomain;
            if ("percent".equals(style)) {
                rounded = roundedForRoundingDomain.divide(BigDecimal.valueOf(100), MathContext.DECIMAL128);
            }
            boolean originalNegative = stringValue.startsWith("-") || original.signum() < 0;
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
        } catch (RuntimeException e) {
            try {
                return format(Double.parseDouble(stringValue));
            } catch (RuntimeException ignored) {
                return format(Double.NaN);
            }
        }
    }

    public JSArray formatToParts(JSContext context, double value) {
        return formatToPartsFromFormatted(context, format(value));
    }

    public JSArray formatToParts(JSContext context, String stringValue) {
        return formatToPartsFromFormatted(context, format(stringValue));
    }

    private JSArray formatToPartsFromFormatted(JSContext context, String formatted) {
        JSArray result = context.createJSArray();
        int partIndex = 0;
        boolean hasSign = formatted.startsWith("-") || formatted.startsWith("+");
        if (hasSign) {
            JSObject signPart = context.createJSObject();
            signPart.set("type", new JSString(formatted.startsWith("-") ? "minusSign" : "plusSign"));
            signPart.set("value", new JSString(formatted.substring(0, 1)));
            result.set(context, partIndex++, signPart);
            formatted = formatted.substring(1);
        }
        int decimalIndex = formatted.indexOf('.');
        if (decimalIndex < 0) {
            decimalIndex = formatted.indexOf(',');
        }
        if (decimalIndex >= 0) {
            JSObject intPart = context.createJSObject();
            intPart.set("type", new JSString("integer"));
            intPart.set("value", new JSString(formatted.substring(0, decimalIndex)));
            result.set(context, partIndex++, intPart);

            JSObject decPart = context.createJSObject();
            decPart.set("type", new JSString("decimal"));
            decPart.set("value", new JSString(formatted.substring(decimalIndex, decimalIndex + 1)));
            result.set(context, partIndex++, decPart);

            if (decimalIndex + 1 < formatted.length()) {
                JSObject fracPart = context.createJSObject();
                fracPart.set("type", new JSString("fraction"));
                fracPart.set("value", new JSString(formatted.substring(decimalIndex + 1)));
                result.set(context, partIndex++, fracPart);
            }
        } else {
            JSObject intPart = context.createJSObject();
            intPart.set("type", new JSString("integer"));
            intPart.set("value", new JSString(formatted));
            result.set(context, partIndex++, intPart);
        }
        return result;
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

    private String formatRoundedNumber(BigDecimal rounded, boolean displayNegative,
                                       boolean roundedZero, boolean originalNegative) {
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
        if (formatter instanceof DecimalFormat decimalFormat && useSignificantDigits
                && !"compact".equals(notation) && !useFractionMode) {
            int integerDigitsForSignificant = computeIntegerDigitsForSignificant(absoluteRounded);
            int minimumFractionDigits = Math.max(0, minimumSignificantDigits - integerDigitsForSignificant);
            int maximumFractionDigits = Math.max(0, maximumSignificantDigits - integerDigitsForSignificant);
            decimalFormat.setMinimumFractionDigits(Math.min(minimumFractionDigits, 100));
            decimalFormat.setMaximumFractionDigits(Math.min(Math.max(maximumFractionDigits, minimumFractionDigits), 100));
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

    private String formatCompactNumber(BigDecimal absoluteValue, boolean displayNegative,
                                       boolean groupingEnabled, int minimumGroupingDigits) {
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

    private String formatCompactPlain(BigDecimal absoluteValue, boolean groupingEnabled,
                                      int minimumGroupingDigits, boolean displayNegative) {
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

    private String formatLocalizedNumber(BigDecimal value, int minimumFractionDigits,
                                         int maximumFractionDigits, boolean grouping) {
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

    private CompactSpec resolveCompactSpec(String localeTag, BigDecimal absoluteValue) {
        if (localeTag.startsWith("en-IN")) {
            if (absoluteValue.compareTo(new BigDecimal("10000000")) >= 0) {
                if ("long".equals(compactDisplay)) {
                    return new CompactSpec(new BigDecimal("10000000"), " crore");
                }
                return new CompactSpec(new BigDecimal("10000000"), "Cr");
            }
            if (absoluteValue.compareTo(new BigDecimal("100000")) >= 0) {
                if ("long".equals(compactDisplay)) {
                    return new CompactSpec(new BigDecimal("100000"), " lakh");
                }
                return new CompactSpec(new BigDecimal("100000"), "L");
            }
            if (absoluteValue.compareTo(new BigDecimal("1000")) >= 0) {
                if ("long".equals(compactDisplay)) {
                    return new CompactSpec(new BigDecimal("1000"), " thousand");
                }
                return new CompactSpec(new BigDecimal("1000"), "K");
            }
            return null;
        }
        if (localeTag.startsWith("en")) {
            if (absoluteValue.compareTo(new BigDecimal("1000000")) >= 0) {
                if ("long".equals(compactDisplay)) {
                    return new CompactSpec(new BigDecimal("1000000"), " million");
                }
                return new CompactSpec(new BigDecimal("1000000"), "M");
            }
            if (absoluteValue.compareTo(new BigDecimal("1000")) >= 0) {
                if ("long".equals(compactDisplay)) {
                    return new CompactSpec(new BigDecimal("1000"), " thousand");
                }
                return new CompactSpec(new BigDecimal("1000"), "K");
            }
            return null;
        }
        if (localeTag.startsWith("de")) {
            if (absoluteValue.compareTo(new BigDecimal("1000000")) >= 0) {
                if ("long".equals(compactDisplay)) {
                    return new CompactSpec(new BigDecimal("1000000"), " Millionen");
                }
                return new CompactSpec(new BigDecimal("1000000"), "\u00A0Mio.");
            }
            if ("long".equals(compactDisplay) && absoluteValue.compareTo(new BigDecimal("1000")) >= 0) {
                return new CompactSpec(new BigDecimal("1000"), " Tausend");
            }
            return null;
        }
        if (localeTag.startsWith("ja")) {
            if (absoluteValue.compareTo(new BigDecimal("100000000")) >= 0) {
                return new CompactSpec(new BigDecimal("100000000"), "億");
            }
            if (absoluteValue.compareTo(new BigDecimal("10000")) >= 0) {
                return new CompactSpec(new BigDecimal("10000"), "万");
            }
            return null;
        }
        if (localeTag.startsWith("ko")) {
            if (absoluteValue.compareTo(new BigDecimal("100000000")) >= 0) {
                return new CompactSpec(new BigDecimal("100000000"), "억");
            }
            if (absoluteValue.compareTo(new BigDecimal("10000")) >= 0) {
                return new CompactSpec(new BigDecimal("10000"), "만");
            }
            if (absoluteValue.compareTo(new BigDecimal("1000")) >= 0) {
                return new CompactSpec(new BigDecimal("1000"), "천");
            }
            return null;
        }
        if (localeTag.startsWith("zh-TW")) {
            if (absoluteValue.compareTo(new BigDecimal("100000000")) >= 0) {
                return new CompactSpec(new BigDecimal("100000000"), "億");
            }
            if (absoluteValue.compareTo(new BigDecimal("10000")) >= 0) {
                return new CompactSpec(new BigDecimal("10000"), "萬");
            }
            return null;
        }
        return null;
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

    public String getCurrency() {
        return currency;
    }

    public String getCurrencyDisplay() {
        return currencyDisplay;
    }

    public String getCurrencySign() {
        return currencySign;
    }

    public String getCompactDisplay() {
        return compactDisplay;
    }

    public JSFunction getBoundFormatFunction() {
        return boundFormatFunction;
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

    public String getUnit() {
        return unit;
    }

    public String getUnitDisplay() {
        return unitDisplay;
    }

    public String getTrailingZeroDisplay() {
        return trailingZeroDisplay;
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
            case "ceil":
                return value.setScale(0, RoundingMode.CEILING);
            case "floor":
                return value.setScale(0, RoundingMode.FLOOR);
            case "expand":
                return value.setScale(0, RoundingMode.UP);
            case "trunc":
                return value.setScale(0, RoundingMode.DOWN);
            case "halfExpand":
                return value.setScale(0, RoundingMode.HALF_UP);
            case "halfTrunc":
                return value.setScale(0, RoundingMode.HALF_DOWN);
            case "halfEven":
                return value.setScale(0, RoundingMode.HALF_EVEN);
            case "halfCeil":
            case "halfFloor":
                return roundHalfCeilOrFloor(value, mode);
            default:
                return value.setScale(0, RoundingMode.HALF_UP);
        }
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

    private record CompactSpec(BigDecimal divisor, String suffix) {
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
            case "never":
                return false;
            case "exceptZero":
                return !roundedZero;
            case "negative":
                return !roundedZero;
            default:
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
        if (minimumFractionDigits > 0 && minimumSignificantDigits > 0) {
            if ("lessPrecision".equals(roundingPriority)) {
                return true;
            }
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

}
