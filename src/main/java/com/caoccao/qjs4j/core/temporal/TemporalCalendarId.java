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

import java.util.Locale;

public enum TemporalCalendarId {
    BUDDHIST("buddhist", true),
    CHINESE("chinese", false),
    COPTIC("coptic", true),
    DANGI("dangi", false),
    ETHIOAA("ethioaa", true),
    ETHIOPIC("ethiopic", true),
    GREGORY("gregory", true),
    HEBREW("hebrew", true),
    INDIAN("indian", true),
    ISLAMIC_CIVIL("islamic-civil", true),
    ISLAMIC_TBLA("islamic-tbla", true),
    ISLAMIC_UMALQURA("islamic-umalqura", true),
    ISO8601("iso8601", false),
    JAPANESE("japanese", true),
    PERSIAN("persian", true),
    ROC("roc", true);

    private final boolean era;
    private final String identifier;

    TemporalCalendarId(String identifier, boolean era) {
        this.era = era;
        this.identifier = identifier;
    }

    public static String canonicalize(String calendarIdentifier) {
        String normalizedCalendarIdentifier = calendarIdentifier.toLowerCase(Locale.ROOT);
        if ("islamicc".equals(normalizedCalendarIdentifier)) {
            return "islamic-civil";
        }
        if ("ethiopic-amete-alem".equals(normalizedCalendarIdentifier)) {
            return "ethioaa";
        }
        if ("gregorian".equals(normalizedCalendarIdentifier)) {
            return "gregory";
        }
        return normalizedCalendarIdentifier;
    }

    public static TemporalCalendarId fromIdentifier(String calendarIdentifier) {
        if (calendarIdentifier == null) {
            return null;
        }
        String normalizedCalendarIdentifier = canonicalize(calendarIdentifier);
        return switch (normalizedCalendarIdentifier) {
            case "buddhist" -> BUDDHIST;
            case "chinese" -> CHINESE;
            case "coptic" -> COPTIC;
            case "dangi" -> DANGI;
            case "ethioaa" -> ETHIOAA;
            case "ethiopic" -> ETHIOPIC;
            case "gregory" -> GREGORY;
            case "hebrew" -> HEBREW;
            case "indian" -> INDIAN;
            case "islamic-civil" -> ISLAMIC_CIVIL;
            case "islamic-tbla" -> ISLAMIC_TBLA;
            case "islamic-umalqura" -> ISLAMIC_UMALQURA;
            case "iso8601" -> ISO8601;
            case "japanese" -> JAPANESE;
            case "persian" -> PERSIAN;
            case "roc" -> ROC;
            default -> null;
        };
    }

    public int getEraYearFromEra(TemporalEra era, int eraYear) {
        switch (this) {
            case GREGORY -> {
                if (era == TemporalEra.BCE) {
                    return 1 - eraYear;
                } else {
                    return eraYear;
                }
            }
            case JAPANESE -> {
                return era.getEraYearByJapaneseEraYear(eraYear);
            }
            case ROC -> {
                if (era == TemporalEra.BROC) {
                    return 1 - eraYear;
                } else {
                    return eraYear;
                }
            }
            case ETHIOPIC -> {
                if (era == TemporalEra.AA) {
                    return eraYear - 5500;
                } else {
                    return eraYear;
                }
            }
            case ISLAMIC_CIVIL, ISLAMIC_TBLA, ISLAMIC_UMALQURA -> {
                if (era == TemporalEra.BH) {
                    return 1 - eraYear;
                } else {
                    return eraYear;
                }
            }
            default -> {
                return eraYear;
            }
        }
    }

    public boolean hasEra() {
        return era;
    }

    public String identifier() {
        return identifier;
    }

    public TemporalEra toTemporalEra(String eraIdentifier) {
        if (eraIdentifier == null) {
            return null;
        }
        String normalizedEraIdentifier = eraIdentifier.toLowerCase(Locale.ROOT);
        return switch (this) {
            case GREGORY -> switch (normalizedEraIdentifier) {
                case "ce", "ad" -> TemporalEra.CE;
                case "bce", "bc" -> TemporalEra.BCE;
                default -> null;
            };
            case JAPANESE -> switch (normalizedEraIdentifier) {
                case "ce", "ad" -> TemporalEra.CE;
                case "bce", "bc" -> TemporalEra.BCE;
                case "meiji" -> TemporalEra.MEIJI;
                case "taisho" -> TemporalEra.TAISHO;
                case "showa" -> TemporalEra.SHOWA;
                case "heisei" -> TemporalEra.HEISEI;
                case "reiwa" -> TemporalEra.REIWA;
                default -> null;
            };
            case ROC -> switch (normalizedEraIdentifier) {
                case "roc", "minguo" -> TemporalEra.ROC;
                case "broc", "before-roc" -> TemporalEra.BROC;
                default -> null;
            };
            case BUDDHIST -> "be".equals(normalizedEraIdentifier) ? TemporalEra.BE : null;
            case COPTIC, HEBREW -> "am".equals(normalizedEraIdentifier) ? TemporalEra.AM : null;
            case ETHIOAA -> "aa".equals(normalizedEraIdentifier) ? TemporalEra.AA : null;
            case ETHIOPIC -> switch (normalizedEraIdentifier) {
                case "aa" -> TemporalEra.AA;
                case "am" -> TemporalEra.AM;
                default -> null;
            };
            case INDIAN -> switch (normalizedEraIdentifier) {
                case "shaka", "saka" -> TemporalEra.SHAKA;
                default -> null;
            };
            case ISLAMIC_CIVIL, ISLAMIC_TBLA, ISLAMIC_UMALQURA -> switch (normalizedEraIdentifier) {
                case "ah" -> TemporalEra.AH;
                case "bh" -> TemporalEra.BH;
                default -> null;
            };
            case PERSIAN -> "ap".equals(normalizedEraIdentifier) ? TemporalEra.AP : null;
            default -> null;
        };
    }
}
