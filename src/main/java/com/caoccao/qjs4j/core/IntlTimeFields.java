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

/** The time component widths of an Intl.DateTimeFormat. */
record IntlTimeFields(String dayPeriod, String hour, String minute, String second) {

    static IntlTimeFields read(JSContext context, JSObject options) {
        return new IntlTimeFields(IntlFormatOptions.readTextWidth(context, options, "dayPeriod"),
                IntlFormatOptions.readNumericWidth(context, options, "hour"),
                IntlFormatOptions.readNumericWidth(context, options, "minute"),
                IntlFormatOptions.readNumericWidth(context, options, "second"));
    }

    boolean specified() {
        return dayPeriod != null || hour != null || minute != null || second != null;
    }

    void defineOptions(JSObject options) {
        JSIntlObject.defineStringOption(options, "dayPeriod", dayPeriod);
        JSIntlObject.defineStringOption(options, "hour", hour);
        JSIntlObject.defineStringOption(options, "minute", minute);
        JSIntlObject.defineStringOption(options, "second", second);
    }

    static final IntlTimeFields EMPTY = new IntlTimeFields(null, null, null, null);

    IntlTimeFields withClock(String width) {
        return new IntlTimeFields(dayPeriod, width, width, width);
    }
}
