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

/**
 * Represents an ISO 8601 date-time combining IsoDate and IsoTime.
 */
public record IsoDateTime(IsoDate date, IsoTime time) {

    public static int compareIsoDateTime(IsoDateTime firstDateTime, IsoDateTime secondDateTime) {
        int dateCompare = IsoDate.compareIsoDate(firstDateTime.date, secondDateTime.date);
        if (dateCompare != 0) {
            return dateCompare;
        }
        return IsoTime.compareIsoTime(firstDateTime.time, secondDateTime.time);
    }

    @Override
    public String toString() {
        return date.toString() + "T" + time.toString();
    }
}
