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

import java.time.LocalDateTime;

/**
 * Represents an ISO 8601 date-time combining IsoDate and IsoTime.
 */
public record IsoDateTime(IsoDate date, IsoTime time) implements Comparable<IsoDateTime> {

    public static IsoDateTime createFromLocalDateTime(LocalDateTime localDateTime) {
        int nanosecondOfSecond = localDateTime.getNano();
        int millisecond = nanosecondOfSecond / 1_000_000;
        int microsecond = (nanosecondOfSecond / 1_000) % 1_000;
        int nanosecond = nanosecondOfSecond % 1_000;
        IsoDate isoDate = new IsoDate(
                localDateTime.getYear(),
                localDateTime.getMonthValue(),
                localDateTime.getDayOfMonth());
        IsoTime isoTime = new IsoTime(
                localDateTime.getHour(),
                localDateTime.getMinute(),
                localDateTime.getSecond(),
                millisecond,
                microsecond,
                nanosecond);
        return new IsoDateTime(isoDate, isoTime);
    }

    @Override
    public int compareTo(IsoDateTime otherIsoDateTime) {
        int dateCompare = date.compareTo(otherIsoDateTime.date);
        if (dateCompare != 0) {
            return dateCompare;
        }
        return time.compareTo(otherIsoDateTime.time);
    }

    public LocalDateTime toLocalDateTime() {
        return LocalDateTime.of(
                date.year(),
                date.month(),
                date.day(),
                time.hour(),
                time.minute(),
                time.second(),
                time.millisecond() * 1_000_000
                        + time.microsecond() * 1_000
                        + time.nanosecond());
    }

    @Override
    public String toString() {
        return date.toString() + "T" + time.toString();
    }
}
