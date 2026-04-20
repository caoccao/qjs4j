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

package com.caoccao.qjs4j.core.temporal;

import com.caoccao.qjs4j.core.JSContext;

public enum TemporalEra {
    BCE("bce"),
    CE("ce"),
    MEIJI("meiji"),
    TAISHO("taisho"),
    SHOWA("showa"),
    HEISEI("heisei"),
    REIWA("reiwa"),
    ROC("roc"),
    BROC("broc"),
    BE("be"),
    AM("am"),
    AA("aa"),
    SHAKA("shaka"),
    AP("ap"),
    AH("ah"),
    BH("bh");

    private final String identifier;

    TemporalEra(String identifier) {
        this.identifier = identifier;
    }

    public static TemporalEra createByCalendarId(JSContext context, TemporalCalendarId calendarId, String era) {
        if (era == null) {
            context.throwRangeError("Temporal error: Invalid era.");
            return null;
        }
        TemporalEra temporalEra = calendarId.toTemporalEra(era);
        if (temporalEra == null) {
            context.throwRangeError("Temporal error: Invalid era.");
            return null;
        }
        return temporalEra;
    }

    public int getEraYearByJapaneseEraYear(int eraYear) {
        return switch (this) {
            case BCE -> 1 - eraYear;
            case MEIJI -> 1867 + eraYear;
            case TAISHO -> 1911 + eraYear;
            case SHOWA -> 1925 + eraYear;
            case HEISEI -> 1988 + eraYear;
            case REIWA -> 2018 + eraYear;
            default -> eraYear;
        };
    }

    public String identifier() {
        return identifier;
    }
}
