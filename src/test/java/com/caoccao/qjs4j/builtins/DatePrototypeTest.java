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
import com.caoccao.qjs4j.core.JSDate;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatePrototype methods.
 */
public class DatePrototypeTest extends BaseTest {

    @Test
    public void testGetDate() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getDate(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(1.0, ((JSNumber) result).value());

        // Edge case: called on non-Date
        result = DatePrototype.getDate(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetDay() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getDay(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        double day = ((JSNumber) result).value();
        assertTrue(day >= 0 && day <= 6, "Day should be between 0 and 6");

        // Edge case: called on non-Date
        result = DatePrototype.getDay(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetFullYear() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getFullYear(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(2024.0, ((JSNumber) result).value());

        // Edge case: called on non-Date
        result = DatePrototype.getFullYear(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetHours() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getHours(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        double hours = ((JSNumber) result).value();
        assertTrue(hours >= 0 && hours <= 23, "Hours should be between 0 and 23");

        // Edge case: called on non-Date
        result = DatePrototype.getHours(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetMilliseconds() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getMilliseconds(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(0.0, ((JSNumber) result).value());

        // Edge case: called on non-Date
        result = DatePrototype.getMilliseconds(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetMinutes() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getMinutes(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(0.0, ((JSNumber) result).value());

        // Edge case: called on non-Date
        result = DatePrototype.getMinutes(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetMonth() {
        // Test with 2024-01-01 00:00:00 UTC (January = month 0)
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getMonth(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(0.0, ((JSNumber) result).value()); // January is 0

        // Edge case: called on non-Date
        result = DatePrototype.getMonth(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetSeconds() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getSeconds(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(0.0, ((JSNumber) result).value());

        // Edge case: called on non-Date
        result = DatePrototype.getSeconds(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetTime() {
        // Test with a known timestamp: 2024-01-01 00:00:00 UTC (1704067200000L)
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getTime(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(1704067200000.0, ((JSNumber) result).value());

        // Edge case: called on non-Date
        result = DatePrototype.getTime(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetUTCDate() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getUTCDate(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(1.0, ((JSNumber) result).value());

        // Edge case: called on non-Date
        result = DatePrototype.getUTCDate(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetUTCFullYear() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getUTCFullYear(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(2024.0, ((JSNumber) result).value());

        // Edge case: called on non-Date
        result = DatePrototype.getUTCFullYear(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetUTCHours() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getUTCHours(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(0.0, ((JSNumber) result).value());

        // Edge case: called on non-Date
        result = DatePrototype.getUTCHours(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetUTCMonth() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.getUTCMonth(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(0.0, ((JSNumber) result).value()); // January is 0

        // Edge case: called on non-Date
        result = DatePrototype.getUTCMonth(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testToISOString() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.toISOString(ctx, date, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        String isoString = ((JSString) result).getValue();
        assertTrue(isoString.startsWith("2024-01-01T00:00:00"));

        // Edge case: called on non-Date
        result = DatePrototype.toISOString(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testToJSON() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.toJSON(ctx, date, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        String jsonString = ((JSString) result).getValue();
        assertTrue(jsonString.startsWith("2024-01-01T00:00:00"));

        // Edge case: called on non-Date
        result = DatePrototype.toJSON(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testToStringMethod() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.toStringMethod(ctx, date, new JSValue[]{});
        assertInstanceOf(JSString.class, result);
        // Should return a string representation
        String str = ((JSString) result).getValue();
        assertNotNull(str);
        assertTrue(str.length() > 0);

        // Edge case: called on non-Date
        result = DatePrototype.toStringMethod(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testValueOf() {
        // Test with 2024-01-01 00:00:00 UTC
        JSDate date = new JSDate(1704067200000L);

        JSValue result = DatePrototype.valueOf(ctx, date, new JSValue[]{});
        assertInstanceOf(JSNumber.class, result);
        assertEquals(1704067200000.0, ((JSNumber) result).value());

        // Edge case: called on non-Date
        result = DatePrototype.valueOf(ctx, new JSString("not a date"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }
}