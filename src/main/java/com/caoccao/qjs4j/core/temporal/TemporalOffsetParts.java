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

public record TemporalOffsetParts(String signText, int hours, int minutes, String secondsText, String fractionText) {

    public boolean hasValidHoursAndMinutes() {
        return hours <= 23 && minutes <= 59;
    }

    public boolean hasZeroSubMinutePart() {
        if (secondsText != null && !"00".equals(secondsText)) {
            return false;
        }
        if (fractionText != null) {
            for (int i = 0; i < fractionText.length(); i++) {
                if (fractionText.charAt(i) != '0') {
                    return false;
                }
            }
        }
        return true;
    }

    public int totalSeconds() {
        int seconds = secondsText == null ? 0 : Integer.parseInt(secondsText);
        int magnitude = hours * 3600 + minutes * 60 + seconds;
        return "+".equals(signText) ? magnitude : -magnitude;
    }
}
