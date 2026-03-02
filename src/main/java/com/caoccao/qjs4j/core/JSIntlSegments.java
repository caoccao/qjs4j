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

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * %Segments.prototype% backing object for Intl.Segmenter.prototype.segment().
 */
public final class JSIntlSegments extends JSObject {
    private static final int CODE_POINT_VS_END = 0xFE0F;
    private static final int CODE_POINT_VS_START = 0xFE00;
    private static final int CODE_POINT_ZWJ = 0x200D;
    private static final int EMOJI_MODIFIER_END = 0x1F3FF;
    private static final int EMOJI_MODIFIER_START = 0x1F3FB;
    private static final int HANGUL_JAMO_EXTENDED_A_END = 0xA97C;
    private static final int HANGUL_JAMO_EXTENDED_A_START = 0xA960;
    private static final int HANGUL_JAMO_EXTENDED_B_END_L = 0xD7C6;
    private static final int HANGUL_JAMO_EXTENDED_B_END_T = 0xD7FB;
    private static final int HANGUL_JAMO_EXTENDED_B_START_L = 0xD7B0;
    private static final int HANGUL_JAMO_EXTENDED_B_START_T = 0xD7CB;
    private static final int HANGUL_JAMO_L_END = 0x115F;
    private static final int HANGUL_JAMO_L_START = 0x1100;
    private static final int HANGUL_JAMO_T_END = 0x11FF;
    private static final int HANGUL_JAMO_T_START = 0x11A8;
    private static final int HANGUL_JAMO_V_END = 0x11A7;
    private static final int HANGUL_JAMO_V_START = 0x1160;
    private static final int HANGUL_SYLLABLE_END = 0xD7A3;
    private static final int HANGUL_SYLLABLE_START = 0xAC00;
    private static final int SUPPLEMENTAL_VS_END = 0xE01EF;
    private static final int SUPPLEMENTAL_VS_START = 0xE0100;

    private final JSIntlSegmenter segmenter;
    private final String text;
    private List<SegmentRange> segmentRanges;

    public JSIntlSegments(JSIntlSegmenter segmenter, String text) {
        super();
        this.segmenter = segmenter;
        this.text = text;
    }

    private static HangulType getHangulType(int codePoint) {
        if ((codePoint >= HANGUL_JAMO_L_START && codePoint <= HANGUL_JAMO_L_END)
                || (codePoint >= HANGUL_JAMO_EXTENDED_A_START && codePoint <= HANGUL_JAMO_EXTENDED_A_END)) {
            return HangulType.L;
        }
        if ((codePoint >= HANGUL_JAMO_V_START && codePoint <= HANGUL_JAMO_V_END)
                || (codePoint >= HANGUL_JAMO_EXTENDED_B_START_L && codePoint <= HANGUL_JAMO_EXTENDED_B_END_L)) {
            return HangulType.V;
        }
        if ((codePoint >= HANGUL_JAMO_T_START && codePoint <= HANGUL_JAMO_T_END)
                || (codePoint >= HANGUL_JAMO_EXTENDED_B_START_T && codePoint <= HANGUL_JAMO_EXTENDED_B_END_T)) {
            return HangulType.T;
        }
        if (codePoint >= HANGUL_SYLLABLE_START && codePoint <= HANGUL_SYLLABLE_END) {
            int tIndex = (codePoint - HANGUL_SYLLABLE_START) % 28;
            return tIndex == 0 ? HangulType.LV : HangulType.LVT;
        }
        return HangulType.NONE;
    }

    private static boolean isCombiningMark(int codePoint) {
        int characterType = Character.getType(codePoint);
        return characterType == Character.NON_SPACING_MARK
                || characterType == Character.COMBINING_SPACING_MARK
                || characterType == Character.ENCLOSING_MARK;
    }

    private static boolean isEmojiModifier(int codePoint) {
        return codePoint >= EMOJI_MODIFIER_START && codePoint <= EMOJI_MODIFIER_END;
    }

    private static boolean isVariationSelector(int codePoint) {
        return (codePoint >= CODE_POINT_VS_START && codePoint <= CODE_POINT_VS_END)
                || (codePoint >= SUPPLEMENTAL_VS_START && codePoint <= SUPPLEMENTAL_VS_END);
    }

    private static boolean isWordLikeSegment(String segment) {
        for (int offset = 0; offset < segment.length(); ) {
            int codePoint = segment.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean shouldMergeGrapheme(String previousSegment, String currentSegment) {
        if (previousSegment.isEmpty() || currentSegment.isEmpty()) {
            return false;
        }

        int previousCodePoint = previousSegment.codePointBefore(previousSegment.length());
        int currentCodePoint = currentSegment.codePointAt(0);

        if (isCombiningMark(currentCodePoint)
                || isVariationSelector(currentCodePoint)
                || isEmojiModifier(currentCodePoint)
                || currentCodePoint == CODE_POINT_ZWJ
                || previousCodePoint == CODE_POINT_ZWJ) {
            return true;
        }

        HangulType previousType = getHangulType(previousCodePoint);
        HangulType currentType = getHangulType(currentCodePoint);
        if (previousType == HangulType.L
                && (currentType == HangulType.L || currentType == HangulType.V
                || currentType == HangulType.LV || currentType == HangulType.LVT)) {
            return true;
        }
        if ((previousType == HangulType.LV || previousType == HangulType.V)
                && (currentType == HangulType.V || currentType == HangulType.T)) {
            return true;
        }
        return (previousType == HangulType.LVT || previousType == HangulType.T) && currentType == HangulType.T;
    }

    public JSValue containing(JSContext context, JSValue indexValue) {
        double indexNumber = JSTypeConversions.toInteger(context, indexValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (indexNumber < 0 || indexNumber >= text.length()) {
            return JSUndefined.INSTANCE;
        }

        int index = (int) indexNumber;
        ensureSegmentRanges();
        for (SegmentRange segmentRange : segmentRanges) {
            if (index >= segmentRange.start() && index < segmentRange.end()) {
                return createSegmentDataObject(context, segmentRange);
            }
        }
        return JSUndefined.INSTANCE;
    }

    public JSIterator createIterator(JSContext context) {
        ensureSegmentRanges();
        final int[] iteratorIndex = {0};
        return new JSIterator(context, () -> {
            if (iteratorIndex[0] >= segmentRanges.size()) {
                return JSIterator.IteratorResult.done(context);
            }
            SegmentRange segmentRange = segmentRanges.get(iteratorIndex[0]++);
            JSObject value = createSegmentDataObject(context, segmentRange);
            return JSIterator.IteratorResult.of(context, value);
        });
    }

    private JSObject createSegmentDataObject(JSContext context, SegmentRange segmentRange) {
        JSObject result = context.createJSObject();
        result.defineProperty(PropertyKey.fromString("segment"), new JSString(segmentRange.segment()), PropertyDescriptor.DataState.All);
        result.defineProperty(PropertyKey.fromString("index"), JSNumber.of(segmentRange.start()), PropertyDescriptor.DataState.All);
        result.defineProperty(PropertyKey.fromString("input"), new JSString(text), PropertyDescriptor.DataState.All);
        if (segmenter.isWordGranularity()) {
            result.defineProperty(PropertyKey.fromString("isWordLike"), JSBoolean.valueOf(segmentRange.wordLike()), PropertyDescriptor.DataState.All);
        }
        return result;
    }

    private void ensureSegmentRanges() {
        if (segmentRanges != null) {
            return;
        }
        List<SegmentRange> ranges = new ArrayList<>();
        BreakIterator breakIterator = segmenter.createBreakIterator();
        breakIterator.setText(text);
        int start = breakIterator.first();
        for (int end = breakIterator.next(); end != BreakIterator.DONE; start = end, end = breakIterator.next()) {
            if (end <= start) {
                continue;
            }
            String segment = text.substring(start, end);
            boolean wordLike = segmenter.isWordGranularity() && isWordLikeSegment(segment);
            ranges.add(new SegmentRange(start, end, segment, wordLike));
        }

        String granularity = segmenter.getGranularity();
        if ("grapheme".equals(granularity) || "word".equals(granularity)) {
            List<SegmentRange> mergedRanges = new ArrayList<>();
            for (SegmentRange currentRange : ranges) {
                if (mergedRanges.isEmpty()) {
                    mergedRanges.add(currentRange);
                    continue;
                }
                SegmentRange previousRange = mergedRanges.get(mergedRanges.size() - 1);
                if (shouldMergeGrapheme(previousRange.segment(), currentRange.segment())) {
                    String mergedSegment = previousRange.segment() + currentRange.segment();
                    boolean mergedWordLike = segmenter.isWordGranularity() && isWordLikeSegment(mergedSegment);
                    mergedRanges.set(
                            mergedRanges.size() - 1,
                            new SegmentRange(previousRange.start(), currentRange.end(), mergedSegment, mergedWordLike));
                } else {
                    mergedRanges.add(currentRange);
                }
            }
            segmentRanges = mergedRanges;
            return;
        }
        segmentRanges = ranges;
    }

    private enum HangulType {
        NONE,
        L,
        V,
        T,
        LV,
        LVT
    }

    private record SegmentRange(int start, int end, String segment, boolean wordLike) {
    }
}
