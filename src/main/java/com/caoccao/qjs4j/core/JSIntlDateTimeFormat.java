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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Intl.DateTimeFormat instance object.
 */
public final class JSIntlDateTimeFormat extends JSObject {
    private final FormatStyle dateStyle;
    private final Locale locale;
    private final FormatStyle timeStyle;

    public JSIntlDateTimeFormat(Locale locale, FormatStyle dateStyle, FormatStyle timeStyle) {
        super();
        this.locale = locale;
        this.dateStyle = dateStyle;
        this.timeStyle = timeStyle;
    }

    public String format(double epochMillis) {
        if (!Double.isFinite(epochMillis)) {
            return "Invalid Date";
        }
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli((long) epochMillis),
                ZoneId.systemDefault());
        DateTimeFormatter formatter;
        if (dateStyle != null && timeStyle != null) {
            formatter = DateTimeFormatter.ofLocalizedDateTime(dateStyle, timeStyle);
        } else if (dateStyle != null) {
            formatter = DateTimeFormatter.ofLocalizedDate(dateStyle);
        } else if (timeStyle != null) {
            formatter = DateTimeFormatter.ofLocalizedTime(timeStyle);
        } else {
            formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
        }
        return formatter.withLocale(locale).withZone(ZoneId.systemDefault()).format(dateTime);
    }

    public FormatStyle getDateStyle() {
        return dateStyle;
    }

    public Locale getLocale() {
        return locale;
    }

    public FormatStyle getTimeStyle() {
        return timeStyle;
    }
}
