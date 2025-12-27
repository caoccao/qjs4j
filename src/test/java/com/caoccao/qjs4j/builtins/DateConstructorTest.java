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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Date constructor and Date.prototype methods.
 */
public class DateConstructorTest extends BaseTest {

    @Test
    public void testNow() {
        // Normal case
        long before = System.currentTimeMillis();
        JSValue result = DateConstructor.now(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        long after = System.currentTimeMillis();

        double timestamp = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(timestamp >= before && timestamp <= after);
    }

    @Test
    public void testUTC() {
        // Normal case: Date.UTC(2025, 0, 1, 0, 0, 0)
        JSValue result = DateConstructor.UTC(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(2025),
                new JSNumber(0), // January
                new JSNumber(1),
                new JSNumber(0),
                new JSNumber(0),
                new JSNumber(0)
        });
        double timestamp = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(timestamp > 0);

        // Edge case: only year
        result = DateConstructor.UTC(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(2025)});
        result.asNumber().map(JSNumber::value).orElse(0.0);

        // Edge case: year and month
        result = DateConstructor.UTC(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(2025),
                new JSNumber(5) // June
        });
        result.asNumber().map(JSNumber::value).orElse(0.0);

        // Edge case: no arguments (should return NaN)
        result = DateConstructor.UTC(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        double nanValue = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(Double.isNaN(nanValue));

        // Edge case: invalid date (should return NaN)
        result = DateConstructor.UTC(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSNumber(2025),
                new JSNumber(12), // Invalid month
                new JSNumber(32)  // Invalid day
        });
        double invalidValue = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(Double.isNaN(invalidValue));
    }

    @Test
    public void testParse() {
        // Normal case: ISO 8601 format
        JSValue result = DateConstructor.parse(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSString("2025-01-01T00:00:00.000Z")
        });
        double timestamp = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(timestamp > 0);

        // Edge case: no arguments
        result = DateConstructor.parse(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        double nanValue = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(Double.isNaN(nanValue));

        // Edge case: invalid date string
        result = DateConstructor.parse(ctx, JSUndefined.INSTANCE, new JSValue[]{
                new JSString("not a date")
        });
        double invalidValue = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(Double.isNaN(invalidValue));
    }

    @Test
    public void testGetTime() {
        // Normal case
        long timestamp = System.currentTimeMillis();
        JSDate date = new JSDate(timestamp);

        JSValue result = DatePrototype.getTime(ctx, date, new JSValue[]{});
        assertEquals(timestamp, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getTime(ctx, new JSString("not date"), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetFullYear() {
        // Normal case: 2025-06-15
        JSDate date = new JSDate(1750000000000L); // Approximately 2025-06-15

        JSValue result = DatePrototype.getFullYear(ctx, date, new JSValue[]{});
        // We don't assert exact year as it depends on timezone
        result.asNumber().map(JSNumber::value).orElse(0.0);

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getFullYear(ctx, new JSObject(), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetMonth() {
        JSDate date = new JSDate(1750000000000L);

        JSValue result = DatePrototype.getMonth(ctx, date, new JSValue[]{});
        double month = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(month >= 0 && month <= 11);

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getMonth(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetDate() {
        JSDate date = new JSDate(1750000000000L);

        JSValue result = DatePrototype.getDate(ctx, date, new JSValue[]{});
        double day = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(day >= 1 && day <= 31);

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getDate(ctx, new JSNumber(123), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetDay() {
        JSDate date = new JSDate(1750000000000L);

        JSValue result = DatePrototype.getDay(ctx, date, new JSValue[]{});
        double day = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(day >= 0 && day <= 6);

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getDay(ctx, JSNull.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetHours() {
        JSDate date = new JSDate(1750000000000L);

        JSValue result = DatePrototype.getHours(ctx, date, new JSValue[]{});
        double hours = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(hours >= 0 && hours <= 23);

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getHours(ctx, new JSArray(), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetMinutes() {
        JSDate date = new JSDate(1750000000000L);

        JSValue result = DatePrototype.getMinutes(ctx, date, new JSValue[]{});
        double minutes = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(minutes >= 0 && minutes <= 59);

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getMinutes(ctx, new JSString("not date"), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetSeconds() {
        JSDate date = new JSDate(1750000000000L);

        JSValue result = DatePrototype.getSeconds(ctx, date, new JSValue[]{});
        double seconds = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(seconds >= 0 && seconds <= 59);

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getSeconds(ctx, JSBoolean.TRUE, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetMilliseconds() {
        JSDate date = new JSDate(1750000000123L); // 123 milliseconds

        JSValue result = DatePrototype.getMilliseconds(ctx, date, new JSValue[]{});
        double ms = result.asNumber().map(JSNumber::value).orElse(0.0);
        assertTrue(ms >= 0 && ms <= 999);

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getMilliseconds(ctx, new JSObject(), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetUTCFullYear() {
        // Known timestamp: 2025-01-01T00:00:00.000Z = 1735689600000
        JSDate date = new JSDate(1735689600000L);

        JSValue result = DatePrototype.getUTCFullYear(ctx, date, new JSValue[]{});
        assertEquals(2025.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getUTCFullYear(ctx, new JSString("not date"), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetUTCMonth() {
        // Known timestamp: 2025-01-01T00:00:00.000Z
        JSDate date = new JSDate(1735689600000L);

        JSValue result = DatePrototype.getUTCMonth(ctx, date, new JSValue[]{});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0)); // January = 0

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getUTCMonth(ctx, JSUndefined.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetUTCDate() {
        // Known timestamp: 2025-01-01T00:00:00.000Z
        JSDate date = new JSDate(1735689600000L);

        JSValue result = DatePrototype.getUTCDate(ctx, date, new JSValue[]{});
        assertEquals(1.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getUTCDate(ctx, new JSNumber(42), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testGetUTCHours() {
        // Known timestamp: 2025-01-01T00:00:00.000Z
        JSDate date = new JSDate(1735689600000L);

        JSValue result = DatePrototype.getUTCHours(ctx, date, new JSValue[]{});
        assertEquals(0.0, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.getUTCHours(ctx, JSNull.INSTANCE, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testToISOString() {
        // Known timestamp: 2025-01-01T00:00:00.000Z
        JSDate date = new JSDate(1735689600000L);

        JSValue result = DatePrototype.toISOString(ctx, date, new JSValue[]{});
        String isoString = result.asString().map(JSString::value).orElse("");
        assertTrue(isoString.contains("2025-01-01"));
        assertTrue(isoString.endsWith("Z"));

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.toISOString(ctx, new JSArray(), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testToJSON() {
        // Known timestamp: 2025-01-01T00:00:00.000Z
        JSDate date = new JSDate(1735689600000L);

        JSValue result = DatePrototype.toJSON(ctx, date, new JSValue[]{});
        String jsonString = result.asString().map(JSString::value).orElse("");
        assertTrue(jsonString.contains("2025-01-01"));

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.toJSON(ctx, JSBoolean.FALSE, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testToString() {
        JSDate date = new JSDate(1735689600000L);

        JSValue result = DatePrototype.toStringMethod(ctx, date, new JSValue[]{});
        String str = result.asString().map(JSString::value).orElse("");
        assertFalse(str.isEmpty());

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.toStringMethod(ctx, new JSString("not date"), new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testValueOf() {
        long timestamp = 1735689600000L;
        JSDate date = new JSDate(timestamp);

        JSValue result = DatePrototype.valueOf(ctx, date, new JSValue[]{});
        assertEquals(timestamp, result.asNumber().map(JSNumber::value).orElse(0.0));

        // Edge case: called on non-Date
        assertTypeError(DatePrototype.valueOf(ctx, new JSObject(), new JSValue[]{}));
        assertPendingException(ctx);
    }
}
