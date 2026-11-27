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

package com.caoccao.qjs4j.builtins.date;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.date.JSDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code Date.parse} and {@code new Date(string)} accept what V8 accepts, and read it the way V8 does.
 * <p>
 * Only the date time string format, {@code toString()}/{@code toUTCString()} output and {@code YYYY-MM-DD HH:MM} used
 * to parse; {@code Jan 1 2024}, {@code 1/2/2024} and every other common legacy form gave {@code NaN}. The parser is now
 * a port of V8's; every string is parsed by both engines in the same time zone and the results compared.
 */
public class DateParseTest extends BaseJavetTest {

    @Test
    public void testDatesPrintedByTheEngine() {
        assertStringWithJavet("""
                var d = new Date(2024, 0, 15, 10, 20, 30, 456);
                JSON.stringify([d.toString(), d.toUTCString(), d.toISOString(), d.toDateString(), d.toGMTString()]
                  .map(s => Date.parse(s)))""", """
                var d = new Date(8.64e15);
                JSON.stringify([d.toString(), d.toUTCString(), d.toISOString()].map(s => Date.parse(s)))""", """
                var d = new Date(-8.64e15);
                JSON.stringify([d.toUTCString(), d.toISOString()].map(s => Date.parse(s)))""");
    }

    @Test
    public void testDateTimeStringFormat() {
        assertStringWithJavet("""
                JSON.stringify([
                  '2024',
                  '2024-01',
                  '2024-01-01',
                  '2024-01-01T10:00',
                  '2024-01-01T10:00:00.123456Z',
                  '2024-01-01T24:00:00Z',
                  '2024-01-01T24:00:01Z',
                  '2024-01-01T10:00+0530',
                  '2024-01-01T10:00+05:30',
                  '2024-01-01T10:00+24:00',
                  '+002024-01-01',
                  '-000000-01-01T00:00:00Z',
                  '-271821-04-20T00:00:00.000Z',
                  '-271821-04-19T23:59:59.999Z',
                  '+275760-09-13T00:00:00.000Z',
                  '+275760-09-13T00:00:00.001Z',
                  '2024-01-01t00:00z',
                  '2000-01-01T00:00:00.Z',
                  '2000-01-01T00:00:00.000000000001Z',
                  '0000-01-01',
                  '2000-01-01T00:00:00.000-00:00',
                  '2024-02-30',
                  '2024-13-01',
                  '2000-01-01T',
                  '2000-01-01T10',
                  '2000-01-01T00:00:00Z junk',
                  'T10:00',
                  '2024-01-01T10:00:00',
                  '2024-01-01T10:00:00.5',
                  '2024-00-01',
                  '2024-01-00',
                  '+2024-01-01',
                  '002024-01-01',
                  '2024-01-01T10:00:00+01'
                ].map(s => Date.parse(s)))""", """
                JSON.stringify([
                  '2024',
                  '2024-01',
                  '2024-01-01',
                  '2024-01-01T10:00',
                  '2024-01-01T10:00:00.123456Z',
                  '2024-01-01T24:00:00Z',
                  '2024-01-01T24:00:01Z',
                  '2024-01-01T10:00+0530',
                  '2024-01-01T10:00+05:30',
                  '2024-01-01T10:00+24:00',
                  '+002024-01-01',
                  '-000000-01-01T00:00:00Z',
                  '-271821-04-20T00:00:00.000Z',
                  '-271821-04-19T23:59:59.999Z',
                  '+275760-09-13T00:00:00.000Z',
                  '+275760-09-13T00:00:00.001Z',
                  '2024-01-01t00:00z',
                  '2000-01-01T00:00:00.Z',
                  '2000-01-01T00:00:00.000000000001Z',
                  '0000-01-01',
                  '2000-01-01T00:00:00.000-00:00',
                  '2024-02-30',
                  '2024-13-01',
                  '2000-01-01T',
                  '2000-01-01T10',
                  '2000-01-01T00:00:00Z junk',
                  'T10:00',
                  '2024-01-01T10:00:00',
                  '2024-01-01T10:00:00.5',
                  '2024-00-01',
                  '2024-01-00',
                  '+2024-01-01',
                  '002024-01-01',
                  '2024-01-01T10:00:00+01'
                ].map(s => new Date(s).getTime()))""");
    }

    @Test
    public void testInvalidAndBoundaryInputs() {
        // Each string reaches a different rejection or boundary of the port; V8 gives NaN or the same time for all.
        assertStringWithJavet("""
                JSON.stringify([
                  'Jan 1 2000 1:2:3:4:5:',
                  'Jan 1 2000 10:.5',
                  'GMT Jan 1 2000 10:00',
                  '-Jan 1 2000',
                  'Jan 1 2000 )',
                  'Jan 1 2000 -',
                  'Jan 1 300000',
                  'Sep 24 275760 00:00',
                  '-271821-04-10 00:00',
                  '-271821-04-30 00:00',
                  '+275760-09-30 00:00',
                  '1/2/3/4',
                  'Jan 1 2000 10:20:30:400.5',
                  'Jan 1 2000 25:00',
                  'Jan 1 2000 10:60',
                  'Jan 1 2000 10:00:60',
                  'Jan 1 2000 24:01',
                  'Jan 1 2000 24:00',
                  'Jan 1 2000 13:00 PM',
                  'Jan 1 2000 12:00 AM',
                  'Jan 1 2000 12:00 PM',
                  'Jan 1 2000 10:00::',
                  'Jan 1 2000 10::',
                  'Jan 1 2000 10:00::00',
                  '10::30 Jan 1 2000',
                  'Jan 1 2000 10:20 30',
                  'Jan 1 2000 10:20:30 40',
                  'Jan 1 2000 1 2',
                  'Jan 1 2000 1:2',
                  'Jan 1 2000 10:20:30.',
                  'Jan 1 2000 10.20'
                ].map(s => Date.parse(s)))""", """
                JSON.stringify([
                  'Jan 1 2000 1:2:3:4:5:',
                  'Jan 1 2000 10:.5',
                  'GMT Jan 1 2000 10:00',
                  '-Jan 1 2000',
                  'Jan 1 2000 )',
                  'Jan 1 2000 -',
                  'Jan 1 300000',
                  'Sep 24 275760 00:00',
                  '-271821-04-10 00:00',
                  '-271821-04-30 00:00',
                  '+275760-09-30 00:00',
                  '1/2/3/4',
                  'Jan 1 2000 10:20:30:400.5',
                  'Jan 1 2000 25:00',
                  'Jan 1 2000 10:60',
                  'Jan 1 2000 10:00:60',
                  'Jan 1 2000 24:01',
                  'Jan 1 2000 24:00',
                  'Jan 1 2000 13:00 PM',
                  'Jan 1 2000 12:00 AM',
                  'Jan 1 2000 12:00 PM',
                  'Jan 1 2000 10:00::',
                  'Jan 1 2000 10::',
                  'Jan 1 2000 10:00::00',
                  '10::30 Jan 1 2000',
                  'Jan 1 2000 10:20 30',
                  'Jan 1 2000 10:20:30 40',
                  'Jan 1 2000 1 2',
                  'Jan 1 2000 1:2',
                  'Jan 1 2000 10:20:30.',
                  'Jan 1 2000 10.20'
                ].map(s => new Date(s).getTime()))""");
        assertStringWithJavet("""
                JSON.stringify([
                  '2000-1-1',
                  '2000-13-01',
                  '2000-01-1',
                  '2000-01-32',
                  '2000-01-01T1:00',
                  '2000-01-01T25:00',
                  '2000-01-01T10:1',
                  '2000-01-01T10:60',
                  '2000-01-01T24:01',
                  '2000-01-01T10:00:1',
                  '2000-01-01T10:00:60',
                  '2000-01-01T24:00:01',
                  '2000-01-01T10:00:00.Z',
                  '2000-01-01T24:00:00.001',
                  '2000-01-01T24:00:00.000Z',
                  '2000-01-01T10:00+2500',
                  '2000-01-01T10:00+0160',
                  '2000-01-01T10:00+1:00',
                  '2000-01-01T10:00+25:00',
                  '2000-01-01T10:00+01:6',
                  '2000-01-01T10:00+01:60',
                  '2000-01-01T10:00+01x',
                  '2000-01-01T10:00:00.12Z',
                  '2000-01-01T10:00:00.5',
                  '2000-01-01T',
                  'T10:00',
                  '2000-01-01T10',
                  '2000-01-01T10:00 ',
                  ' 2000-01-01',
                  '-000000-01-01',
                  '+002000-01-01'
                ].map(s => Date.parse(s)))""", """
                JSON.stringify([
                  '2000-1-1',
                  '2000-13-01',
                  '2000-01-1',
                  '2000-01-32',
                  '2000-01-01T1:00',
                  '2000-01-01T25:00',
                  '2000-01-01T10:1',
                  '2000-01-01T10:60',
                  '2000-01-01T24:01',
                  '2000-01-01T10:00:1',
                  '2000-01-01T10:00:60',
                  '2000-01-01T24:00:01',
                  '2000-01-01T10:00:00.Z',
                  '2000-01-01T24:00:00.001',
                  '2000-01-01T24:00:00.000Z',
                  '2000-01-01T10:00+2500',
                  '2000-01-01T10:00+0160',
                  '2000-01-01T10:00+1:00',
                  '2000-01-01T10:00+25:00',
                  '2000-01-01T10:00+01:6',
                  '2000-01-01T10:00+01:60',
                  '2000-01-01T10:00+01x',
                  '2000-01-01T10:00:00.12Z',
                  '2000-01-01T10:00:00.5',
                  '2000-01-01T',
                  'T10:00',
                  '2000-01-01T10',
                  '2000-01-01T10:00 ',
                  ' 2000-01-01',
                  '-000000-01-01',
                  '+002000-01-01'
                ].map(s => new Date(s).getTime()))""");
        assertStringWithJavet("""
                JSON.stringify([
                  '12/31/1999 10:00',
                  '2000 Jan 5',
                  'Jan 1 49',
                  'Jan 1 50',
                  'Jan 1 2000 10:00 GMT+05:30',
                  'Jan 1 2000 10:00 GMT+5',
                  'Jan 1 2000 10:00 GMT+0530',
                  'Jan 1 2000 10:00 GMT+05330',
                  'Jan 1 2000 10:00 UTC+',
                  'Jan 1 2000 10:00 UT',
                  'Jan 1 2000 10:00 z',
                  'Jan 1 2000 10:00 EST',
                  'Jan 1 2000 10:00 PDT',
                  'Jan 1 2000 10:00:00.1234567891234',
                  'Jan 1, 2000',
                  'Jan 1 2000 (comment',
                  'Jan 1 2000 (a (b) c) 10:00',
                  'Mondayx Jan 1 2000',
                  'Jan 1 2000 UTCX',
                  'Jan 1 2000 10:00 PMX',
                  'Januaryyy 1 2000',
                  'Jan 1 2000 10:00:00.5',
                  'Jan 1 2000 10:00 x',
                  'x Jan 1 2000',
                  'Jan 1 2000 10:00:00 GMT+0100 (CET)',
                  'Jan 1 2000 GMT-8',
                  'Jan 1 2000 GMT-0800',
                  'UTC+1 Jan 1 2000'
                ].map(s => Date.parse(s)))""", """
                JSON.stringify([
                  '12/31/1999 10:00',
                  '2000 Jan 5',
                  'Jan 1 49',
                  'Jan 1 50',
                  'Jan 1 2000 10:00 GMT+05:30',
                  'Jan 1 2000 10:00 GMT+5',
                  'Jan 1 2000 10:00 GMT+0530',
                  'Jan 1 2000 10:00 GMT+05330',
                  'Jan 1 2000 10:00 UTC+',
                  'Jan 1 2000 10:00 UT',
                  'Jan 1 2000 10:00 z',
                  'Jan 1 2000 10:00 EST',
                  'Jan 1 2000 10:00 PDT',
                  'Jan 1 2000 10:00:00.1234567891234',
                  'Jan 1, 2000',
                  'Jan 1 2000 (comment',
                  'Jan 1 2000 (a (b) c) 10:00',
                  'Mondayx Jan 1 2000',
                  'Jan 1 2000 UTCX',
                  'Jan 1 2000 10:00 PMX',
                  'Januaryyy 1 2000',
                  'Jan 1 2000 10:00:00.5',
                  'Jan 1 2000 10:00 x',
                  'x Jan 1 2000',
                  'Jan 1 2000 10:00:00 GMT+0100 (CET)',
                  'Jan 1 2000 GMT-8',
                  'Jan 1 2000 GMT-0800',
                  'UTC+1 Jan 1 2000'
                ].map(s => new Date(s).getTime()))""");
    }

    @Test
    public void testLegacyFormats() {
        assertStringWithJavet("""
                JSON.stringify([
                  'Jan 1 2024',
                  '1/2/2024',
                  '2024/01/02',
                  'January 2, 2024',
                  '2 January 2024',
                  'Jan 2, 2024 1:02:03 PM',
                  '2024-1-1',
                  'Mon Jan 01 2024',
                  'Tue, 1 Jan 2000 00:00:00 GMT',
                  'Thu, 01 Jan 1970 00:00:00 GMT+0000',
                  '12/31/1999 23:59:59 UTC',
                  'Jan 1 2000 GMT',
                  '2000 Jan 1',
                  '99-01-01',
                  'Jan 2',
                  '1/2',
                  '1',
                  '1 1',
                  '2000 1 1',
                  '1 2000 Jan',
                  'Jan-01-2000',
                  '01-Jan-2000',
                  '2000-Jan-01',
                  '1.2.2000',
                  '1-2-2000',
                  'Sat, 01-Jan-2000 08:00:00 GMT',
                  '12:00 Jan 1 2000',
                  'Jan 1, 2000, 10:00',
                  'Jan 1 0 ',
                  'Jan 1 49',
                  'Jan 1 50',
                  '1 Jan 99',
                  'Jan 1 1234567890',
                  'Decembre 1 2000',
                  'Janxyz 1 2000',
                  'J\\u00e4nner 1 2000',
                  'foo Jan 1 2000',
                  'foo1 Jan 1 2000',
                  'Jan 1 2000 foo',
                  'Jan',
                  '))',
                  'Jan 1 2000 )',
                  'Jan 1 2000 (',
                  'Jan 1 2000 (unclosed',
                  'Jan 1 2000 10:00 (comment (nested)) Z',
                  '32/1/2000',
                  'Jan 1 2000\\u00a0',
                  '\\u0009Jan\\u000a1 2000',
                  'Fri Sep 12 275760 00:00:00 GMT+0000',
                  'Sat, 13 Sep 275760 00:00:00 GMT',
                  'Tue Apr 20 -271821 00:00:00 GMT+0000',
                  '20 Apr -271821',
                  'Jan 1 2000\\u0000junk'
                ].map(s => Date.parse(s)))""", """
                JSON.stringify([
                  'Jan 1 2024',
                  '1/2/2024',
                  '2024/01/02',
                  'January 2, 2024',
                  '2 January 2024',
                  'Jan 2, 2024 1:02:03 PM',
                  '2024-1-1',
                  'Mon Jan 01 2024',
                  'Tue, 1 Jan 2000 00:00:00 GMT',
                  'Thu, 01 Jan 1970 00:00:00 GMT+0000',
                  '12/31/1999 23:59:59 UTC',
                  'Jan 1 2000 GMT',
                  '2000 Jan 1',
                  '99-01-01',
                  'Jan 2',
                  '1/2',
                  '1',
                  '1 1',
                  '2000 1 1',
                  '1 2000 Jan',
                  'Jan-01-2000',
                  '01-Jan-2000',
                  '2000-Jan-01',
                  '1.2.2000',
                  '1-2-2000',
                  'Sat, 01-Jan-2000 08:00:00 GMT',
                  '12:00 Jan 1 2000',
                  'Jan 1, 2000, 10:00',
                  'Jan 1 0 ',
                  'Jan 1 49',
                  'Jan 1 50',
                  '1 Jan 99',
                  'Jan 1 1234567890',
                  'Decembre 1 2000',
                  'Janxyz 1 2000',
                  'J\\u00e4nner 1 2000',
                  'foo Jan 1 2000',
                  'foo1 Jan 1 2000',
                  'Jan 1 2000 foo',
                  'Jan',
                  '))',
                  'Jan 1 2000 )',
                  'Jan 1 2000 (',
                  'Jan 1 2000 (unclosed',
                  'Jan 1 2000 10:00 (comment (nested)) Z',
                  '32/1/2000',
                  'Jan 1 2000\\u00a0',
                  '\\u0009Jan\\u000a1 2000',
                  'Fri Sep 12 275760 00:00:00 GMT+0000',
                  'Sat, 13 Sep 275760 00:00:00 GMT',
                  'Tue Apr 20 -271821 00:00:00 GMT+0000',
                  '20 Apr -271821',
                  'Jan 1 2000\\u0000junk'
                ].map(s => new Date(s).getTime()))""");
    }

    @Test
    public void testLegacyTimesAndZones() {
        assertStringWithJavet("""
                JSON.stringify([
                  'Jan 1 2000 12:00 AM',
                  'Jan 1 2000 12:00 PM',
                  'Jan 1 2000 13:00 PM',
                  'Jan 1 2000 10:00 GMT+5',
                  'Jan 1 2000 10:00 GMT+05:30',
                  'Jan 1 2000 10:00 GMT-0800',
                  'Jan 1 2000 10:00 PST',
                  'Jan 1 2000 10:00 EDT',
                  'Jan 1 2000 10:00 cst',
                  'Jan 1 2000 10:00 MDT',
                  'Jan 1 2000 10:00 MST',
                  'Jan 1 2000 10:00 PDT',
                  'Jan 1 2000 10:00 EST',
                  'Jan 1 2000 10:00 CDT',
                  'Jan 1 2000 10:00:00.5',
                  'Jan 1 2000 10:00:00.123456',
                  'Jan 1 2000 10::',
                  'Jan 1 2000 10:',
                  'Jan 1 2000 Z+1',
                  'Jan 1 2000 10:00:00 +0100',
                  'Jan 1 2000 10:00:00 -01:00',
                  'Jan 1 2000 a.m.',
                  'Jan 1 2000 10 PM',
                  'Jan 1 2000 10:00 am pm',
                  'Jan 1 2000 Z 10:00',
                  'Jan 1 2000 GMT+123',
                  'Jan 1 2000 GMT+12345',
                  'Jan 1 2000 UT',
                  'Jan 1 2000 UTC+1:30',
                  'Jan 1 2000 10:00 +1',
                  'Jan 1 2000 10:00 -',
                  'Jan 1 2000 10:00 Z -0100',
                  'Jan 1 2000 10:60',
                  'Jan 1 2000 10:59:60',
                  'Jan 1 2000 24:00',
                  'Jan 1 2000 24:01',
                  'Jan 01 2000 00:00:00 GMT+1400',
                  'Jan 01 2000 00:00:00 GMT+9999',
                  'Jan 1 2000 10:00:00:00',
                  'Jan 1 2000 10:00:00:00:00',
                  'Jan 1 2000 10.30',
                  'Jan 1 2000 10:30.5',
                  'Jan 1 2000 10:30:15.',
                  'Jan 1 2000 10:30:15.x',
                  'Jan 1 2000 10:30:15+01',
                  'Jan 1 2000 10:30:15x',
                  'Jan 1 2000 10:30 PM GMT+2',
                  'Jan 1 2000 GMT +2',
                  'Jan 1 2000 10:00 GMT+5:',
                  'Jan 1 2000 1::30',
                  'Jan 1 2000 10:20::',
                  'PM Jan 1 2000 10:00'
                ].map(s => Date.parse(s)))""", """
                JSON.stringify([
                  'Jan 1 2000 12:00 AM',
                  'Jan 1 2000 12:00 PM',
                  'Jan 1 2000 13:00 PM',
                  'Jan 1 2000 10:00 GMT+5',
                  'Jan 1 2000 10:00 GMT+05:30',
                  'Jan 1 2000 10:00 GMT-0800',
                  'Jan 1 2000 10:00 PST',
                  'Jan 1 2000 10:00 EDT',
                  'Jan 1 2000 10:00 cst',
                  'Jan 1 2000 10:00 MDT',
                  'Jan 1 2000 10:00 MST',
                  'Jan 1 2000 10:00 PDT',
                  'Jan 1 2000 10:00 EST',
                  'Jan 1 2000 10:00 CDT',
                  'Jan 1 2000 10:00:00.5',
                  'Jan 1 2000 10:00:00.123456',
                  'Jan 1 2000 10::',
                  'Jan 1 2000 10:',
                  'Jan 1 2000 Z+1',
                  'Jan 1 2000 10:00:00 +0100',
                  'Jan 1 2000 10:00:00 -01:00',
                  'Jan 1 2000 a.m.',
                  'Jan 1 2000 10 PM',
                  'Jan 1 2000 10:00 am pm',
                  'Jan 1 2000 Z 10:00',
                  'Jan 1 2000 GMT+123',
                  'Jan 1 2000 GMT+12345',
                  'Jan 1 2000 UT',
                  'Jan 1 2000 UTC+1:30',
                  'Jan 1 2000 10:00 +1',
                  'Jan 1 2000 10:00 -',
                  'Jan 1 2000 10:00 Z -0100',
                  'Jan 1 2000 10:60',
                  'Jan 1 2000 10:59:60',
                  'Jan 1 2000 24:00',
                  'Jan 1 2000 24:01',
                  'Jan 01 2000 00:00:00 GMT+1400',
                  'Jan 01 2000 00:00:00 GMT+9999',
                  'Jan 1 2000 10:00:00:00',
                  'Jan 1 2000 10:00:00:00:00',
                  'Jan 1 2000 10.30',
                  'Jan 1 2000 10:30.5',
                  'Jan 1 2000 10:30:15.',
                  'Jan 1 2000 10:30:15.x',
                  'Jan 1 2000 10:30:15+01',
                  'Jan 1 2000 10:30:15x',
                  'Jan 1 2000 10:30 PM GMT+2',
                  'Jan 1 2000 GMT +2',
                  'Jan 1 2000 10:00 GMT+5:',
                  'Jan 1 2000 1::30',
                  'Jan 1 2000 10:20::',
                  'PM Jan 1 2000 10:00'
                ].map(s => new Date(s).getTime()))""");
    }

    @Test
    public void testMoreLegacyTimesAndZones() {
        // A line terminator is white space. V8 caches a failed parse in a way that can change the next result, so this
        // string is parsed on its own.
        assertStringWithJavet("String(Date.parse('Jan\\n1 2000 10:00') === Date.parse('Jan 1 2000 10:00'))");
        assertStringWithJavet("""
                JSON.stringify([
                  '-271821-04-05 00:00',
                  'Jan 1 2000 10:20:30:400',
                  'Jan 1 2000 23:60',
                  'Jan 1 2000 24:30',
                  'Jan 1 2000 24:00:00:001',
                  'Jan 1 2000 10:00 GMT+05:',
                  '2000-01-01T10:00UT',
                  'Jan 1 2000 10:00 GMT+05 30',
                  'UTC Jan 1 2000 10:00',
                  'Jan 1 2000 10:00 UTC+0:30'
                ].map(s => Date.parse(s)))""", """
                JSON.stringify([
                  '-271821-04-05 00:00',
                  'Jan 1 2000 10:20:30:400',
                  'Jan 1 2000 23:60',
                  'Jan 1 2000 24:30',
                  'Jan 1 2000 24:00:00:001',
                  'Jan 1 2000 10:00 GMT+05:',
                  '2000-01-01T10:00UT',
                  'Jan 1 2000 10:00 GMT+05 30',
                  'UTC Jan 1 2000 10:00',
                  'Jan 1 2000 10:00 UTC+0:30'
                ].map(s => new Date(s).getTime()))""");
    }

    @Test
    public void testWhiteSpaceAndSeparators() {
        // Padding sends an ISO date to the legacy rules, which read it as local time; a comma is not an ISO
        // decimal separator.
        assertStringWithJavet("""
                JSON.stringify([
                  ' 2024-01-01',
                  '2024-01-01 ',
                  '2024-01-01T00:00:00,123Z',
                  '2024-01-01 10:00',
                  '2024-01-01 10:00:00.5',
                  '2024-01-01 10:00Z',
                  '2024-01-01 10:00 +0100',
                  '',
                  ' ',
                  '\\u2028'
                ].map(s => Date.parse(s)))""", """
                JSON.stringify([
                  ' 2024-01-01',
                  '2024-01-01 ',
                  '2024-01-01T00:00:00,123Z',
                  '2024-01-01 10:00',
                  '2024-01-01 10:00:00.5',
                  '2024-01-01 10:00Z',
                  '2024-01-01 10:00 +0100',
                  '',
                  ' ',
                  '\\u2028'
                ].map(s => new Date(s).getTime()))""");
        assertThat(JSDate.parseDateString(null)).isNaN();
    }

}
