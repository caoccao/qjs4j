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

package com.caoccao.qjs4j.core.intl;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.iterator.JSIterator;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lazy segmentation with shared boundary caching and logarithmic containing lookups. */
public final class JSIntlSegments extends JSObject {
    // Java's extended grapheme matcher implements UAX #29, including GB11's pictographic ZWJ rule.
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private BreakIterator breakIterator;
    private int cursor;
    private int graphemeEnd;
    private Matcher graphemes;
    private final JSIntlSegmenter segmenter;
    private final List<SegmentRange> segmentRanges = new ArrayList<>();
    private final String text;

    public JSIntlSegments(JSContext context, JSIntlSegmenter segmenter, String text) {
        super(context);
        this.segmenter = segmenter;
        this.text = text;
    }

    public JSValue containing(JSContext context, JSValue indexValue) {
        double indexNumber = JSTypeConversions.toInteger(context, indexValue);
        if (context.hasPendingException() || indexNumber < 0 || indexNumber >= text.length()) {
            return JSUndefined.INSTANCE;
        }
        int index = (int) indexNumber;
        while (cursor <= index) {
            scanNext();
        }
        int low = 0;
        int high = segmentRanges.size() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (segmentRanges.get(middle).end() <= index) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return createSegmentDataObject(context, segmentRanges.get(low));
    }

    public JSIterator createIterator(JSContext context) {
        final int[] index = {0};
        return new JSIterator(context, () -> {
            if (index[0] == segmentRanges.size() && !scanNext()) {
                return JSIterator.IteratorResult.done(context);
            }
            return JSIterator.IteratorResult.of(context,
                    createSegmentDataObject(context, segmentRanges.get(index[0]++)));
        });
    }

    private JSObject createSegmentDataObject(JSContext context, SegmentRange range) {
        JSObject result = context.createJSObject();
        result.defineProperty(PropertyKey.fromString("segment"),
                new JSString(text.substring(range.start(), range.end())), PropertyDescriptor.DataState.All);
        result.defineProperty(PropertyKey.INDEX, JSNumber.of(range.start()), PropertyDescriptor.DataState.All);
        result.defineProperty(PropertyKey.INPUT, new JSString(text), PropertyDescriptor.DataState.All);
        if (segmenter.isWordGranularity()) {
            result.defineProperty(PropertyKey.fromString("isWordLike"), JSBoolean.valueOf(range.wordLike()),
                    PropertyDescriptor.DataState.All);
        }
        return result;
    }

    private boolean scanNext() {
        if (cursor == text.length()) {
            return false;
        }
        context.checkExecutionInterrupt();
        if (graphemes == null) {
            graphemes = GRAPHEME.matcher(text);
            if (!"grapheme".equals(segmenter.getGranularity())) {
                breakIterator = segmenter.createBreakIterator();
                breakIterator.setText(text);
                breakIterator.first();
            }
        }
        int end;
        if (breakIterator == null) {
            graphemes.find();
            end = graphemes.end();
        } else {
            end = breakIterator.next();
            if (segmenter.isWordGranularity()) {
                // A word boundary must not split a grapheme (notably an emoji ZWJ sequence).
                while (true) {
                    while (graphemeEnd < end && graphemes.find()) {
                        graphemeEnd = graphemes.end();
                    }
                    if (graphemeEnd <= end) {
                        break;
                    }
                    end = breakIterator.next();
                }
            }
        }
        boolean wordLike = false;
        if (segmenter.isWordGranularity()) {
            for (int offset = cursor; offset < end;) {
                int codePoint = text.codePointAt(offset);
                wordLike |= Character.isLetterOrDigit(codePoint);
                offset += Character.charCount(codePoint);
            }
        }
        segmentRanges.add(new SegmentRange(cursor, end, wordLike));
        cursor = end;
        return true;
    }

    private record SegmentRange(int start, int end, boolean wordLike) {
    }
}
