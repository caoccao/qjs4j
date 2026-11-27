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

import com.caoccao.qjs4j.core.PropertyKey;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSObject;

import java.time.format.FormatStyle;

/** The date component widths of an Intl.DateTimeFormat. */
record IntlDateFields(String weekday, String era, String year, String month, String day) {

    static IntlDateFields read(JSContext context, JSObject options) {
        return new IntlDateFields(IntlFormatOptions.readTextWidth(context, options, PropertyKey.WEEKDAY),
                IntlFormatOptions.readTextWidth(context, options, PropertyKey.ERA),
                IntlFormatOptions.readNumericWidth(context, options, PropertyKey.YEAR),
                IntlFormatOptions.readValidatedOption(context, options, PropertyKey.MONTH, "numeric", "2-digit", "long",
                        "short", "narrow"),
                IntlFormatOptions.readNumericWidth(context, options, PropertyKey.DAY));
    }

    boolean specified() {
        return weekday != null || year != null || month != null || day != null;
    }

    void defineOptions(JSObject options) {
        IntlBuiltins.defineStringOption(options, "weekday", weekday);
        IntlBuiltins.defineStringOption(options, "era", era);
        IntlBuiltins.defineStringOption(options, "year", year);
        IntlBuiltins.defineStringOption(options, "month", month);
        IntlBuiltins.defineStringOption(options, "day", day);
    }

    IntlDateFields withDate(String year, String month, String day) {
        return new IntlDateFields(weekday, era, year, month, day);
    }

    IntlDateFields forTemporal(boolean fullDate, boolean yearSupported, boolean monthSupported, boolean daySupported) {
        return new IntlDateFields(fullDate ? weekday : null, yearSupported ? era : null, yearSupported ? year : null,
                monthSupported ? month : null, daySupported ? day : null);
    }

    IntlDateFields withStyle(FormatStyle style) {
        return new IntlDateFields(weekday == null && style == FormatStyle.FULL ? "long" : weekday, era,
                year != null ? year : style == FormatStyle.SHORT ? "2-digit" : "numeric",
                month != null ? month : switch (style) {
                    case FULL, LONG -> "long";
                    case MEDIUM -> "short";
                    case SHORT -> "numeric";
                }, day != null ? day : "numeric");
    }
}
