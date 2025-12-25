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

/**
 * Represents a JavaScript Date object.
 * Wraps a timestamp (milliseconds since Unix epoch).
 */
public final class JSDate extends JSObject {
    private final long timeValue; // milliseconds since 1970-01-01T00:00:00.000Z

    /**
     * Create a Date with the current time.
     */
    public JSDate() {
        this(System.currentTimeMillis());
    }

    /**
     * Create a Date with a specific timestamp.
     * @param timeValue milliseconds since Unix epoch
     */
    public JSDate(long timeValue) {
        super();
        this.timeValue = timeValue;
    }

    /**
     * Get the time value (milliseconds since Unix epoch).
     */
    public long getTimeValue() {
        return timeValue;
    }

    /**
     * Get ZonedDateTime representation (UTC).
     */
    public ZonedDateTime getZonedDateTime() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeValue), ZoneId.of("UTC"));
    }

    /**
     * Get ZonedDateTime representation (local timezone).
     */
    public ZonedDateTime getLocalZonedDateTime() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeValue), ZoneId.systemDefault());
    }

    @Override
    public String toString() {
        return "JSDate[" + getZonedDateTime() + "]";
    }
}
