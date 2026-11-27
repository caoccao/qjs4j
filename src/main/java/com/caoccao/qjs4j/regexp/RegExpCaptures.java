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

package com.caoccao.qjs4j.regexp;

import java.util.*;
import static com.caoccao.qjs4j.regexp.RegExpCompiler.*;
import static com.caoccao.qjs4j.regexp.RegExpEscapes.*;

/** Capture numbering and name resolution for a single regular expression. */
final class RegExpCaptures {
    /**
     * Maximum number of capture groups in one pattern, counting group 0: V8's limit, 32,767 explicit groups. Capture
     * indices are encoded in two bytes.
     * <p>
     * The limit was QuickJS's 255, set by its one-byte encoding, so a pattern with 255 groups that V8 compiles was a
     * {@code SyntaxError}. A capture count no longer sets an unbounded cost: each saved backtrack state grows by two
     * ints per capture, and the backtrack store is capped at 64 MB, past which a match is a {@code RangeError}.
     */
    private static final int CAPTURE_COUNT_MAX = 32_768;
    private int count = 1; // Capture zero is the entire match.
    private final Map<String, Integer> indices;
    private List<String> names;
    private final int totalCount;

    private RegExpCaptures(int totalCount, Map<String, Integer> indices) {
        this.totalCount = totalCount;
        this.indices = indices;
    }

    int add(String name) {
        if (count >= CAPTURE_COUNT_MAX) {
            throw new RegExpSyntaxException("Too many captures");
        }
        int index = count++;
        if (name != null) {
            if (names == null) {
                names = new ArrayList<>();
            }
            while (names.size() < index) {
                names.add(null);
            }
            names.add(name);
        }
        return index;
    }

    int count() {
        return count;
    }

    boolean hasNames() {
        return !indices.isEmpty();
    }

    Integer indexOf(String name) {
        return indices.get(name);
    }

    String[] names() {
        if (!hasNames()) {
            return null;
        }
        return names == null ? new String[count] : names.toArray(new String[count]);
    }

    int totalCount() {
        return totalCount;
    }

    private static boolean isJsIdentifierPart(int codePoint) {
        return codePoint == '$' || codePoint == '_' || codePoint == 0x200C || codePoint == 0x200D
                || Character.isUnicodeIdentifierPart(codePoint);
    }

    private static boolean isJsIdentifierStart(int codePoint) {
        return codePoint == '$' || codePoint == '_' || Character.isUnicodeIdentifierStart(codePoint);
    }

    private static GroupNameParseResult parseGroupName(int[] codePoints, int startPos) {
        StringBuilder name = new StringBuilder();
        int pos = startPos;

        while (pos < codePoints.length) {
            int codePoint = codePoints[pos];
            if (codePoint == '>') {
                if (name.isEmpty()) {
                    return null;
                }
                return new GroupNameParseResult(name.toString(), pos + 1);
            }

            if (codePoint == '\\') {
                EscapedCodePoint escapedCodePoint = tryParseUnicodeEscapeAt(codePoints, pos, true);
                if (escapedCodePoint == null) {
                    return null;
                }
                codePoint = escapedCodePoint.codePoint();
                pos = escapedCodePoint.nextPos();
                if (Character.isHighSurrogate((char) codePoint) && pos < codePoints.length && codePoints[pos] == '\\') {
                    EscapedCodePoint trailingEscapedCodePoint = tryParseUnicodeEscapeAt(codePoints, pos, true);
                    if (trailingEscapedCodePoint != null
                            && Character.isLowSurrogate((char) trailingEscapedCodePoint.codePoint())) {
                        codePoint = Character.toCodePoint((char) codePoint,
                                (char) trailingEscapedCodePoint.codePoint());
                        pos = trailingEscapedCodePoint.nextPos();
                    }
                }
            } else {
                // In non-unicode mode, codePoints may be UTF-16 code units.
                // Combine surrogate pairs for group names (which use Unicode ID_Start/ID_Continue).
                if (Character.isHighSurrogate((char) codePoint) && pos + 1 < codePoints.length
                        && Character.isLowSurrogate((char) codePoints[pos + 1])) {
                    codePoint = Character.toCodePoint((char) codePoint, (char) codePoints[pos + 1]);
                    pos += 2;
                } else {
                    pos++;
                }
            }

            if (name.isEmpty()) {
                if (!isJsIdentifierStart(codePoint)) {
                    return null;
                }
            } else if (!isJsIdentifierPart(codePoint)) {
                return null;
            }
            name.appendCodePoint(codePoint);
        }

        return null;
    }

    static String readName(CompileContext context) {
        GroupNameParseResult result = requireName(context.codePoints, context.pos);
        context.pos = result.nextPos();
        return result.name();
    }

    private static GroupNameParseResult requireName(int[] source, int start) {
        GroupNameParseResult result = parseGroupName(source, start);
        if (result == null) {
            throw new RegExpSyntaxException("Invalid capture group name");
        }
        return result;
    }

    static RegExpCaptures scan(int[] source, boolean mayHaveNames) {
        Map<String, Integer> indices = mayHaveNames ? new HashMap<>() : Map.of();
        Deque<Alternatives> groups = mayHaveNames ? new ArrayDeque<>() : null;
        if (groups != null) {
            groups.push(new Alternatives());
        }
        int next = 1;
        boolean inClass = false;
        for (int i = 0; i < source.length; i++) {
            int ch = source[i];
            if (ch == '\\') {
                i++;
            } else if (inClass) {
                inClass = ch != ']';
            } else if (ch == '[') {
                inClass = true;
            } else if (ch == '|' && groups != null) {
                groups.peek().nextBranch();
            } else if (ch == ')' && groups != null && groups.size() > 1) {
                Alternatives finished = groups.pop();
                groups.peek().merge(finished);
            } else if (ch == '(') {
                if (i + 1 >= source.length || source[i + 1] != '?') {
                    next++;
                } else if (i + 2 < source.length && source[i + 2] == '<'
                        && (i + 3 == source.length || source[i + 3] != '=' && source[i + 3] != '!')) {
                    GroupNameParseResult named = requireName(source, i + 3);
                    if (!groups.peek().current.add(named.name())) {
                        throw new RegExpSyntaxException("Duplicate capture group name");
                    }
                    indices.putIfAbsent(named.name(), next++);
                    i = named.nextPos() - 1;
                }
                if (groups != null) {
                    groups.push(new Alternatives());
                }
            }
        }
        return new RegExpCaptures(next, indices);
    }

    /** Names in sibling alternatives may be repeated. Closing a group exposes all its names to the parent branch. */
    private static final class Alternatives {
        final Set<String> completed = new HashSet<>();
        final Set<String> current = new HashSet<>();

        void merge(Alternatives nested) {
            current.addAll(nested.current);
            current.addAll(nested.completed);
        }

        void nextBranch() {
            completed.addAll(current);
            current.clear();
        }
    }

    private record GroupNameParseResult(String name, int nextPos) {
    }
}
