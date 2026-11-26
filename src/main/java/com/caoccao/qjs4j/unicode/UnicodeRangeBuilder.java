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

package com.caoccao.qjs4j.unicode;

import java.util.Arrays;

/** Accumulates inclusive pairs of Unicode code points without boxing the endpoints. */
public final class UnicodeRangeBuilder {
    private int[] endpoints = new int[8];
    private int size;

    public void add(int start, int end) {
        reserve(2);
        endpoints[size++] = start;
        endpoints[size++] = end;
    }

    public void addAll(int[] ranges) {
        reserve(ranges.length);
        System.arraycopy(ranges, 0, endpoints, size, ranges.length);
        size += ranges.length;
    }

    /** Append a range in ascending order, coalescing it with its predecessor where possible. */
    public void addSorted(int start, int end) {
        if (size > 0 && start <= endpoints[size - 1] + 1) {
            endpoints[size - 1] = Math.max(endpoints[size - 1], end);
        } else {
            add(start, end);
        }
    }

    private void reserve(int count) {
        if (count > endpoints.length - size) {
            endpoints = Arrays.copyOf(endpoints, Math.max(size + count, endpoints.length * 2));
        }
    }

    public int[] toArray() {
        return Arrays.copyOf(endpoints, size);
    }

    /** Sort and coalesce range pairs supplied in arbitrary order. */
    public static int[] normalize(int[] ranges) {
        if (ranges.length <= 2) {
            return ranges;
        }
        long[] sorted = new long[ranges.length / 2];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = ((long) ranges[2 * i] << 32) | (ranges[2 * i + 1] & 0xFFFF_FFFFL);
        }
        Arrays.sort(sorted);
        UnicodeRangeBuilder result = new UnicodeRangeBuilder();
        for (long pair : sorted) {
            result.addSorted((int) (pair >>> 32), (int) pair);
        }
        return result.toArray();
    }
}
