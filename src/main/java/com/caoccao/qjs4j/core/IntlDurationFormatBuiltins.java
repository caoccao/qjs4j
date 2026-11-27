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

import java.util.*;
import static com.caoccao.qjs4j.core.JSIntlObject.createPartObject;

/** Intl.DurationFormat construction and prototype methods. */
final class IntlDurationFormatBuiltins {
    private IntlDurationFormatBuiltins() {
    }

    /**
     * Intl.DurationFormat constructor.
     */
    static JSValue createDurationFormat(JSContext context, JSObject prototype, JSValue[] args) {
        IntlFormatOptions.ConstructorLocale constructor = IntlFormatOptions.ConstructorLocale.resolve(context,
                prototype, args, "DurationFormat");
        if (constructor == null) {
            return JSUndefined.INSTANCE;
        }

        // Get options object (optional, defaults to undefined)
        JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
        JSObject options = null;
        if (optionsArg instanceof JSObject opt) {
            options = opt;
        } else if (!(optionsArg instanceof JSUndefined)) {
            return context.throwTypeError("Options is not an object");
        }

        // Read localeMatcher (not in resolvedOptions, but read from options)
        JSValue matcherError = IntlFormatOptions.localeMatcherError(context, options);
        if (matcherError != null) {
            return matcherError;
        }

        // Read numberingSystem from options
        String numberingSystemOption = IntlFormatOptions.readStringOption(context, options, "numberingSystem");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!IntlFormatOptions.validateUnicodeType(context, "numberingSystem", numberingSystemOption)) {
            return context.getPendingException();
        }

        IntlFormatOptions.NumberingLocale numbering = IntlFormatOptions.NumberingLocale.resolve(constructor.locale(),
                numberingSystemOption);

        // Read style (default "short")
        String style = IntlFormatOptions.readStringOption(context, options, "style");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (style == null) {
            style = "short";
        } else if (!"long".equals(style) && !"short".equals(style) && !"narrow".equals(style)
                && !"digital".equals(style)) {
            return context
                    .throwRangeError("Value " + style + " out of range for Intl.DurationFormat options property style");
        }

        // Process unit options
        String prevStyle = null;
        String[] unitStyles = new String[JSIntlDurationFormat.UNIT_NAMES.length];
        String[] unitDisplays = new String[JSIntlDurationFormat.UNIT_NAMES.length];

        for (int i = 0; i < JSIntlDurationFormat.UNIT_NAMES.length; i++) {
            String unitName = JSIntlDurationFormat.UNIT_NAMES[i];

            // Read unit style from options
            String unitStyle = IntlFormatOptions.readStringOption(context, options, unitName);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // GetDurationUnitOptions: the style is validated before the display is read.
            String[] validStyles = JSIntlDurationFormat.getValidStylesForUnit(i);
            if (unitStyle != null && !Arrays.asList(validStyles).contains(unitStyle)) {
                return context.throwRangeError(
                        "Value " + unitStyle + " out of range for Intl.DurationFormat options property " + unitName);
            }
            boolean subsecondUnit = i >= 7;
            boolean prevNumeric = JSIntlDurationFormat.isNumericStyle(prevStyle);
            String displayDefault = "always";
            if (unitStyle == null) {
                if ("digital".equals(style)) {
                    unitStyle = JSIntlDurationFormat.getDigitalDefault(i);
                    if (i < 4 || subsecondUnit) {
                        displayDefault = "auto";
                    }
                } else if (prevNumeric) {
                    unitStyle = "numeric";
                    if (!"minutes".equals(unitName) && !"seconds".equals(unitName)) {
                        displayDefault = "auto";
                    }
                } else {
                    unitStyle = style;
                    displayDefault = "auto";
                }
            }
            // A numeric sub-second unit is the spec's "fractional" style: it is folded into the unit before it.
            boolean fractional = subsecondUnit && "numeric".equals(unitStyle);
            if (fractional) {
                displayDefault = "auto";
            }
            String display = IntlFormatOptions.readStringOption(context, options, unitName + "Display");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (display == null) {
                display = displayDefault;
            } else if (!"auto".equals(display) && !"always".equals(display)) {
                return context.throwRangeError("Value " + display
                        + " out of range for Intl.DurationFormat options property " + unitName + "Display");
            }
            // ValidateDurationUnitStyle
            if (fractional && "always".equals(display)) {
                return context
                        .throwRangeError("Cannot use display \"always\" for " + unitName + " with \"numeric\" style");
            }
            if (prevNumeric && !JSIntlDurationFormat.isNumericStyle(unitStyle)) {
                return context.throwRangeError("Cannot use style \"" + unitStyle + "\" for " + unitName
                        + " when previous unit uses \"" + prevStyle + "\" style");
            }
            if (prevNumeric && ("minutes".equals(unitName) || "seconds".equals(unitName))) {
                unitStyle = "2-digit";
            }

            unitStyles[i] = unitStyle;
            unitDisplays[i] = display;
            if (i >= 4) {
                prevStyle = unitStyle;
            }
        }

        Integer fractionalDigits = getFractionalDigits(context, options);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        JSIntlDurationFormat durationFormat = new JSIntlDurationFormat(context, numbering.locale(),
                numbering.numberingSystem(), style, unitStyles, unitDisplays, fractionalDigits);
        return constructor.finish(durationFormat);
    }

    /**
     * Intl.DurationFormat.prototype.format(duration)
     */
    static JSValue durationFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatDuration(context, thisArg, args, false);
    }

    /**
     * Intl.DurationFormat.prototype.formatToParts(duration)
     */
    static JSValue durationFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatDuration(context, thisArg, args, true);
    }

    /**
     * Intl.DurationFormat.prototype.resolvedOptions()
     */
    static JSValue durationFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDurationFormat df)) {
            return context
                    .throwTypeError("Intl.DurationFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject result = context.createJSObject();
        result.set("locale", new JSString(df.getLocale().toLanguageTag()));
        result.set("numberingSystem", new JSString(df.getNumberingSystem()));
        result.set("style", new JSString(df.getStyle()));

        // Add unit styles and displays in table order
        for (int i = 0; i < JSIntlDurationFormat.UNIT_NAMES.length; i++) {
            String unitName = JSIntlDurationFormat.UNIT_NAMES[i];
            result.set(unitName, new JSString(df.getUnitStyle(i)));
            result.set(unitName + "Display", new JSString(df.getUnitDisplay(i)));
        }

        // Add fractionalDigits only if explicitly set
        if (df.getFractionalDigits() != null) {
            result.set("fractionalDigits", new JSNumber(df.getFractionalDigits()));
        }

        return result;
    }

    private static JSValue formatDuration(JSContext context, JSValue thisArg, JSValue[] args, boolean partsRequested) {
        if (!(thisArg instanceof JSIntlDurationFormat durationFormat)) {
            String method = partsRequested ? "formatToParts" : "format";
            return context
                    .throwTypeError("Intl.DurationFormat.prototype." + method + " called on incompatible receiver");
        }
        if (args.length == 0) {
            return context.throwTypeError("Invalid duration value");
        }

        Map<String, Double> durationRecord = JSIntlDurationFormat.toDurationRecord(context, args[0]);
        if (durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        if (!partsRequested) {
            return new JSString(durationFormat.formatDuration(durationRecord));
        }
        List<JSIntlDurationFormat.FormatPart> parts = durationFormat.partitionDurationFormatPattern(durationRecord);
        JSArray result = context.createJSArray();
        for (int i = 0; i < parts.size(); i++) {
            JSIntlDurationFormat.FormatPart part = parts.get(i);
            JSObject partObj = createPartObject(context, part.type(), part.value());
            if (part.unit() != null) {
                partObj.set("unit", new JSString(part.unit()));
            }
            result.set(i, partObj);
        }
        return result;
    }

    private static Integer getFractionalDigits(JSContext context, JSObject options) {
        return options == null
                ? null
                : IntlFormatOptions.getOptionIntegerOrUndefined(context, options, "fractionalDigits", 0, 9,
                        "fractionalDigits value is out of range");
    }
}
