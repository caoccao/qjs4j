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

package com.caoccao.qjs4j.core.temporal.duration;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.temporal.iso.IsoDateTime;

import java.math.BigInteger;
import java.time.LocalDateTime;

public record TemporalRelativeToOption(LocalDateTime startDateTime, boolean zoned, BigInteger epochNanoseconds,
        String timeZoneId, Integer offsetSeconds) {

    public BigInteger epochNanosecondsAt(JSContext context, LocalDateTime time, BigInteger known) {
        if (known != null) {
            return known;
        }
        return time.equals(startDateTime)
                ? epochNanoseconds
                : IsoDateTime.zonedLocalDateTimeToEpochNanoseconds(context, this, time);
    }
}
