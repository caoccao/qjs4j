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

import java.time.format.FormatStyle;

/** The date component widths of an Intl.DateTimeFormat. */
record IntlDateFields(String weekday, String era, String year, String month, String day) {

    static IntlDateFields read(JSContext context, JSObject options) {
        return new IntlDateFields(IntlFormatOptions.readTextWidth(context, options, "weekday"),
                IntlFormatOptions.readTextWidth(context, options, "era"),
                IntlFormatOptions.readNumericWidth(context, options, "year"),
                IntlFormatOptions.readValidatedOption(context, options, "month", "numeric", "2-digit", "long", "short",
                        "narrow"),
                IntlFormatOptions.readNumericWidth(context, options, "day"));
    }

    boolean specified() {
        return weekday != null || year != null || month != null || day != null;
    }

    void defineOptions(JSObject options) {
        JSIntlObject.defineStringOption(options, "weekday", weekday);
        JSIntlObject.defineStringOption(options, "era", era);
        JSIntlObject.defineStringOption(options, "year", year);
        JSIntlObject.defineStringOption(options, "month", month);
        JSIntlObject.defineStringOption(options, "day", day);
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
