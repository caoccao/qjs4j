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

import com.caoccao.qjs4j.unicode.UnicodePropertyResolver;
import com.caoccao.qjs4j.utils.DynamicBuffer;

import java.util.*;

/**
 * Compiles regex patterns to bytecode. Based on QuickJS libregexp.c compiler.
 * <p>
 * Supports captures, lookarounds, Unicode properties and quantified subexpressions.
 */
public final class RegExpCompiler {

    /**
     * Maximum number of capture groups in one pattern, counting group 0: V8's limit, 32,767 explicit groups. Capture
     * indices are encoded in two bytes.
     * <p>
     * The limit was QuickJS's 255, set by its one-byte encoding, so a pattern with 255 groups that V8 compiles was a
     * {@code SyntaxError}. A capture count no longer sets an unbounded cost: each saved backtrack state grows by two
     * ints per capture, and the backtrack store is capped at 64 MB, past which a match is a {@code RangeError}.
     */
    private static final int CAPTURE_COUNT_MAX = 32_768;
    private static final byte[] HEX_VALUES = hexValues();
    private static final long MAX_QUANTIFIER_BOUND = Integer.MAX_VALUE;
    static final int MAX_UNICODE_CODE_POINT = 0x10FFFF;
    private static final int MAX_UNROLLED_QUANTIFIER_REPETITIONS = 4096;
    private int captureCount;
    private List<String> groupNames;
    private Map<String, Integer> namedCaptureIndices;
    private int totalCaptureCount;

    private final UnicodePropertyResolver unicodePropertyResolver;

    public RegExpCompiler(UnicodePropertyResolver unicodePropertyResolver) {
        this.unicodePropertyResolver = unicodePropertyResolver;
    }

    /**
     * Registers follow the stack discipline of QuickJS's loop counters: a quantifier's registers are released once its
     * term is complete, so sibling quantifiers share them and only nesting depth consumes new ones. Backtracking
     * restores a sibling's registers along with the captures, so reuse cannot corrupt a loop that is resumed.
     */
    private int allocateRegister(CompileContext context) {
        if (context.nextRegister >= RegExpBytecode.ExecutionLimits.MAX_REGISTERS) {
            throw new RegExpSyntaxException("Regular expression requires too many registers");
        }
        int register = context.nextRegister++;
        context.registerPeak = Math.max(context.registerPeak, context.nextRegister);
        context.registerCount = Math.max(context.registerCount, context.nextRegister);
        return register;
    }

    private long appendDecimalDigitWithClamp(long currentValue, int digit) {
        if (currentValue >= MAX_QUANTIFIER_BOUND) {
            return MAX_QUANTIFIER_BOUND;
        }
        long nextValue = currentValue * 10L + digit;
        return Math.min(nextValue, MAX_QUANTIFIER_BOUND);
    }

    /**
     * Compile a regex pattern to bytecode.
     *
     * @param pattern
     *            The regex pattern string
     * @param flags
     *            The regex flags (g, i, m, s, u, y, d, v)
     * @return Compiled bytecode
     * @throws RegExpSyntaxException
     *             if the pattern is invalid
     */
    public RegExpBytecode compile(String pattern, String flags) {
        if (pattern == null) {
            throw new RegExpSyntaxException("Pattern cannot be null");
        }

        int flagBits = RegExpBytecode.parseFlags(flags);
        captureCount = 1; // Capture group 0 (the entire match)
        groupNames = new ArrayList<>();
        groupNames.add(null); // Group 0 has no name
        namedCaptureIndices = scanNamedCaptureGroups(pattern);
        if (!namedCaptureIndices.isEmpty()) {
            flagBits |= RegExpBytecode.FLAG_NAMED_GROUPS;
        }

        DynamicBuffer buffer = new DynamicBuffer(256);
        CompileContext context = new CompileContext(pattern, flagBits, buffer, unicodePropertyResolver);

        try {
            compilePattern(context);
            if (context.pos != context.codePoints.length) {
                throw new RegExpSyntaxException(
                        context.codePoints[context.pos] == ')' ? "Unmatched ')'" : "Unexpected character in pattern");
            }
            // End with MATCH opcode
            buffer.appendU8(RegExpOpcode.MATCH.getCode());

            String[] compiledGroupNames = null;
            if (!namedCaptureIndices.isEmpty()) {
                compiledGroupNames = new String[captureCount];
                for (int i = 0; i < captureCount && i < groupNames.size(); i++) {
                    compiledGroupNames[i] = groupNames.get(i);
                }
            }

            return new RegExpBytecode(buffer.toByteArray(), flagBits, captureCount,
                    // Registers this pattern actually allocated. The matcher saves them on every
                    // backtrack point where state changed, so reporting the real count instead of
                    // the maximum is the difference between 12 and 76 bytes of saved state per
                    // point for a typical pattern.
                    context.registerCount, compiledGroupNames);
        } catch (RegExpSyntaxException e) {
            // The reason as it was found, as V8 reports it.
            throw e;
        } catch (Exception e) {
            throw new RegExpSyntaxException("Failed to compile pattern: " + e.getMessage(), e);
        }
    }

    private void compileAlternative(CompileContext context, boolean isBackwardDirection) {
        // An alternative is a sequence of terms (atoms with optional quantifiers)
        int alternativeStart = context.buffer.size();
        while (context.pos < context.codePoints.length) {
            int ch = context.codePoints[context.pos];

            // Check for end of alternative
            if (ch == '|' || ch == ')') {
                return;
            }

            // Parse a term (atom + optional quantifier)
            int termStart = context.buffer.size();
            compileTerm(context, isBackwardDirection);
            if (isBackwardDirection) {
                reverseAlternativeTerm(context, alternativeStart, termStart);
            }
        }
    }

    private void compileAtom(CompileContext context, boolean isBackwardDirection) {
        if (context.pos >= context.codePoints.length) {
            return;
        }
        context.lastAtomCanRepeat = true;

        int ch = context.codePoints[context.pos];

        switch (ch) {
            case '^' -> {
                context.lastAtomCanRepeat = false;
                context.buffer.appendU8(context.isMultiline()
                        ? RegExpOpcode.LINE_START_M.getCode()
                        : RegExpOpcode.LINE_START.getCode());
                context.pos++;
            }

            case '$' -> {
                context.lastAtomCanRepeat = false;
                context.buffer.appendU8(
                        context.isMultiline() ? RegExpOpcode.LINE_END_M.getCode() : RegExpOpcode.LINE_END.getCode());
                context.pos++;
            }

            case '.' -> {
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
                context.buffer.appendU8(context.isDotAll() ? RegExpOpcode.ANY.getCode() : RegExpOpcode.DOT.getCode());
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
                context.pos++;
            }

            case '\\' -> {
                context.pos++;
                compileEscape(context, isBackwardDirection);
            }

            case '[' -> {
                RegExpCharacterClass.compileCharacterClass(context, isBackwardDirection);
            }

            case '(' -> {
                compileGroup(context, isBackwardDirection);
            }

            case '*', '+', '?' -> {
                throw new RegExpSyntaxException("Nothing to repeat");
            }

            case '{' -> {
                if (startsWithValidQuantifier(context)) {
                    throw new RegExpSyntaxException("Nothing to repeat");
                }
                // In unicode mode, lone '{' is a syntax error (not AnnexB literal)
                if (context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Lone quantifier brackets");
                }
                compileLiteralChar(context, ch, isBackwardDirection);
                context.pos++;
            }

            case '}' -> {
                // In unicode mode, lone '}' outside a quantifier is a syntax error
                if (context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Lone quantifier brackets");
                }
                compileLiteralChar(context, ch, isBackwardDirection);
                context.pos++;
            }

            case ']' -> {
                if (context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Lone quantifier brackets");
                }
                compileLiteralChar(context, ch, isBackwardDirection);
                context.pos++;
            }

            case ')' -> {
                context.lastAtomCanRepeat = false;
                // Don't consume - let parent handle it
            }

            case '|' -> {
                context.lastAtomCanRepeat = false;
                // Don't consume - let parent handle it
            }

            default -> {
                // Literal character
                compileLiteralChar(context, ch, isBackwardDirection);
                context.pos++;
            }
        }
    }

    private void compileDisjunction(CompileContext context, boolean isBackwardDirection) {
        // A disjunction is a sequence of alternatives separated by |
        int start = context.buffer.size();

        // Compile first alternative
        compileAlternative(context, isBackwardDirection);

        // Handle additional alternatives
        while (context.pos < context.codePoints.length && context.codePoints[context.pos] == '|') {
            context.pos++; // Skip '|'

            int len = context.buffer.size() - start;

            // Insert a SPLIT at the start to choose between alternatives
            context.buffer.insert(start, 5);
            context.buffer.setU8(start, RegExpOpcode.SPLIT_NEXT_FIRST.getCode());
            context.buffer.setU32(start + 1, len + 5);

            // Emit GOTO to skip the next alternative
            int gotoPos = context.buffer.size();
            context.buffer.appendU8(RegExpOpcode.GOTO.getCode());
            context.buffer.appendU32(0); // Placeholder for jump offset

            // Compile next alternative
            compileAlternative(context, isBackwardDirection);

            // Patch the GOTO offset
            int gotoOffset = context.buffer.size() - (gotoPos + 5);
            context.buffer.setU32(gotoPos + 1, gotoOffset);
        }
    }

    private void compileEscape(CompileContext context, boolean isBackwardDirection) {
        if (context.pos >= context.codePoints.length) {
            throw new RegExpSyntaxException("Incomplete escape sequence");
        }

        int ch = context.codePoints[context.pos++];
        switch (ch) {
            case 'n' -> compileLiteralChar(context, '\n', isBackwardDirection);
            case 'r' -> compileLiteralChar(context, '\r', isBackwardDirection);
            case 't' -> compileLiteralChar(context, '\t', isBackwardDirection);
            case 'f' -> compileLiteralChar(context, '\f', isBackwardDirection);
            case 'v' -> compileLiteralChar(context, '\u000B', isBackwardDirection);
            case 'd', 'D', 'w', 'W', 's', 'S' -> {
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
                if (ch == 's' || ch == 'S') {
                    context.buffer.appendU8((ch == 's' ? RegExpOpcode.SPACE : RegExpOpcode.NOT_SPACE).getCode());
                } else {
                    boolean inverted = ch == 'D' || ch == 'W';
                    context.buffer.appendU8((inverted ? RegExpOpcode.NOT_RANGE : RegExpOpcode.RANGE).getCode());
                    RegExpCharacterClass.emitRangePayload(context,
                            RegExpCharacterClass.characterClassRanges(context, Character.toLowerCase(ch)));
                }
                if (isBackwardDirection) {
                    context.buffer.appendU8(RegExpOpcode.PREV.getCode());
                }
            }
            case 'b' -> {
                // \b - match word boundary
                context.buffer.appendU8(context.isIgnoreCase() && context.isUnicodeMode()
                        ? RegExpOpcode.WORD_BOUNDARY_I.getCode()
                        : RegExpOpcode.WORD_BOUNDARY.getCode());
            }
            case 'B' -> {
                // \B - match non-word boundary
                context.buffer.appendU8(context.isIgnoreCase() && context.isUnicodeMode()
                        ? RegExpOpcode.NOT_WORD_BOUNDARY_I.getCode()
                        : RegExpOpcode.NOT_WORD_BOUNDARY.getCode());
            }
            case 'k' -> {
                // AnnexB: In non-unicode mode with no named capture groups,
                // \k is an identity escape matching literal 'k'
                if (!context.isUnicodeMode() && namedCaptureIndices.isEmpty()) {
                    compileLiteralChar(context, 'k', isBackwardDirection);
                    break;
                }
                if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '<') {
                    throw new RegExpSyntaxException("expecting group name");
                }
                context.pos++; // Skip '<'

                GroupNameParseResult groupNameParseResult = parseGroupName(context.codePoints, context.pos);
                if (groupNameParseResult == null) {
                    throw new RegExpSyntaxException("Invalid capture group name");
                }
                context.pos = groupNameParseResult.nextPos();

                Integer groupNum = namedCaptureIndices.get(groupNameParseResult.name());
                if (groupNum == null) {
                    throw new RegExpSyntaxException("Invalid named capture referenced");
                }

                context.buffer.appendU8(context.isIgnoreCase()
                        ? (isBackwardDirection
                                ? RegExpOpcode.BACKWARD_BACK_REFERENCE_I.getCode()
                                : RegExpOpcode.BACK_REFERENCE_I.getCode())
                        : (isBackwardDirection
                                ? RegExpOpcode.BACKWARD_BACK_REFERENCE.getCode()
                                : RegExpOpcode.BACK_REFERENCE.getCode()));
                context.buffer.appendU16(groupNum);
            }
            case 'p', 'P' -> {
                if (!context.isUnicodeMode()) {
                    compileLiteralChar(context, ch, isBackwardDirection);
                    break;
                }
                RegExpCharacterClass.compileUnicodePropertyEscape(context, ch == 'P', isBackwardDirection);
            }
            case '0' -> {
                // \0 escape: null character or legacy octal escape
                if (context.isUnicodeMode()) {
                    // Unicode mode: \0 is only valid when not followed by a decimal digit
                    if (context.pos < context.codePoints.length) {
                        int next = context.codePoints[context.pos];
                        if (next >= '0' && next <= '9') {
                            throw new RegExpSyntaxException("Invalid escape sequence");
                        }
                    }
                    compileLiteralChar(context, 0, isBackwardDirection);
                } else {
                    // Non-Unicode mode: legacy octal escape (\0, \00, \000, etc.)
                    int value = parseLegacyOctalEscape(context, 0);
                    compileLiteralChar(context, value, isBackwardDirection);
                }
            }
            case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                // Back reference \1, \2, etc.
                // Use totalCaptureCount (pre-scanned) to support forward references
                int groupNum = ch - '0';
                int savedPos = context.pos;
                // Check for multi-digit group numbers
                while (context.pos < context.codePoints.length) {
                    int nextCh = context.codePoints[context.pos];
                    if (nextCh >= '0' && nextCh <= '9') {
                        groupNum = groupNum * 10 + (nextCh - '0');
                        context.pos++;
                    } else {
                        break;
                    }
                }

                if (groupNum < totalCaptureCount) {
                    // Valid backreference (including forward references)
                    context.buffer.appendU8(context.isIgnoreCase()
                            ? (isBackwardDirection
                                    ? RegExpOpcode.BACKWARD_BACK_REFERENCE_I.getCode()
                                    : RegExpOpcode.BACK_REFERENCE_I.getCode())
                            : (isBackwardDirection
                                    ? RegExpOpcode.BACKWARD_BACK_REFERENCE.getCode()
                                    : RegExpOpcode.BACK_REFERENCE.getCode()));
                    context.buffer.appendU16(groupNum);
                } else if (!context.isUnicodeMode()) {
                    // AnnexB: In non-unicode mode, invalid backreferences are
                    // treated as octal escapes (\1-\7) or identity escapes (\8-\9)
                    context.pos = savedPos; // Rewind consumed digits
                    int digit = ch - '0';
                    if (digit <= 7) {
                        // Octal escape: \1-\7 → U+0001-U+0007
                        // Also handle multi-digit octal: \12, \377, etc.
                        int octalValue = digit;
                        while (context.pos < context.codePoints.length) {
                            int nextCh = context.codePoints[context.pos];
                            if (nextCh >= '0' && nextCh <= '7') {
                                int newValue = octalValue * 8 + (nextCh - '0');
                                if (newValue > 0377) {
                                    break;
                                }
                                octalValue = newValue;
                                context.pos++;
                            } else {
                                break;
                            }
                        }
                        compileLiteralChar(context, octalValue, isBackwardDirection);
                    } else {
                        // \8, \9 → identity escape (literal digit)
                        compileLiteralChar(context, ch, isBackwardDirection);
                    }
                } else {
                    // Unicode mode: invalid backreference is a syntax error
                    throw new RegExpSyntaxException("Invalid backreference \\" + groupNum);
                }
            }
            case 'c' -> {
                // \c ControlLetter
                if (context.pos < context.codePoints.length) {
                    int next = context.codePoints[context.pos];
                    if ((next >= 'a' && next <= 'z') || (next >= 'A' && next <= 'Z')) {
                        // Valid control escape: \cA-\cZ or \ca-\cz
                        context.pos++;
                        compileLiteralChar(context, next % 32, isBackwardDirection);
                        break;
                    }
                }
                // AnnexB: In non-unicode mode, \c without valid control letter
                // returns literal '\' and backs up to re-parse 'c' as next term
                // (matching QuickJS get_class_atom behavior)
                if (context.isUnicodeMode()) {
                    throw new RegExpSyntaxException("Invalid control escape");
                }
                context.pos--; // back up to 'c' so it's re-parsed as next atom
                compileLiteralChar(context, '\\', isBackwardDirection);
            }
            case 'x' -> compileLiteralChar(context, readFixedHexEscape(context, 'x', 2), isBackwardDirection);
            case 'u' -> compileLiteralChar(context, parseUnicodeEscape(context), isBackwardDirection);
            default -> {
                // Identity escape
                if (context.isUnicodeMode()) {
                    // In unicode mode, only syntax characters are valid identity escapes
                    // QuickJS: ^$\.*+?()[]{}|/
                    if (!isSyntaxCharacter(ch)) {
                        throw new RegExpSyntaxException("invalid escape sequence in regular expression");
                    }
                }
                // Non-unicode mode: any character is a valid identity escape (Annex B)
                compileLiteralChar(context, ch, isBackwardDirection);
            }
        }
    }

    private void compileGroup(CompileContext context, boolean isBackwardDirection) {
        // Capture groups (...) or non-capturing groups (?:...)
        context.pos++; // Skip '('

        // Check for special group syntax
        boolean isCapturing = true;
        String captureName = null;
        if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '?') {
            context.pos++;
            if (context.pos >= context.codePoints.length) {
                throw new RegExpSyntaxException("Invalid group");
            }
            InlineModifierGroupParseResult inlineModifierGroupParseResult = tryParseInlineModifierGroup(context);
            if (inlineModifierGroupParseResult != null) {
                int savedFlags = context.currentFlags;
                context.currentFlags = inlineModifierGroupParseResult.scopedFlags();
                try {
                    compileDisjunction(context, isBackwardDirection);
                } finally {
                    context.currentFlags = savedFlags;
                }
                if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != ')') {
                    throw new RegExpSyntaxException("Unterminated group");
                }
                context.pos++;
                context.lastAtomCanRepeat = true;
                return;
            }
            int groupType = context.codePoints[context.pos++];
            if (groupType == ':') {
                // Non-capturing group (?:...)
                isCapturing = false;
            } else if (groupType == '=') {
                // Positive lookahead (?=...)
                compileLookaround(context, false, false);
                context.lastAtomCanRepeat = !context.isUnicodeMode();
                return;
            } else if (groupType == '!') {
                // Negative lookahead (?!...)
                compileLookaround(context, true, false);
                context.lastAtomCanRepeat = !context.isUnicodeMode();
                return;
            } else if (groupType == '<') {
                if (context.pos < context.codePoints.length
                        && (context.codePoints[context.pos] == '=' || context.codePoints[context.pos] == '!')) {
                    boolean isNegative = context.codePoints[context.pos] == '!';
                    context.pos++;
                    compileLookaround(context, isNegative, true);
                    context.lastAtomCanRepeat = false;
                    return;
                }

                GroupNameParseResult groupNameParseResult = parseGroupName(context.codePoints, context.pos);
                if (groupNameParseResult == null) {
                    throw new RegExpSyntaxException("Invalid capture group name");
                }
                context.pos = groupNameParseResult.nextPos();
                captureName = groupNameParseResult.name();
            } else {
                throw new RegExpSyntaxException("Invalid group");
            }
        }

        int groupIndex = -1;
        if (isCapturing) {
            if (captureCount >= CAPTURE_COUNT_MAX) {
                throw new RegExpSyntaxException("Too many captures");
            }
            groupIndex = captureCount++;
            ensureGroupNameSize(groupIndex + 1);
            if (captureName != null) {
                groupNames.set(groupIndex, captureName);
            }
            // Save start
            context.buffer.appendU8(
                    isBackwardDirection ? RegExpOpcode.SAVE_END.getCode() : RegExpOpcode.SAVE_START.getCode());
            context.buffer.appendU16(groupIndex);
        }

        // Compile group contents
        compileDisjunction(context, isBackwardDirection);

        if (isCapturing) {
            // Save end
            context.buffer.appendU8(
                    isBackwardDirection ? RegExpOpcode.SAVE_START.getCode() : RegExpOpcode.SAVE_END.getCode());
            context.buffer.appendU16(groupIndex);
        }

        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != ')') {
            throw new RegExpSyntaxException("Unterminated group");
        }
        context.pos++; // Skip ')'
        context.lastAtomCanRepeat = true;
    }

    private void compileLookaround(CompileContext context, boolean isNegative, boolean backward) {
        RegExpOpcode start;
        RegExpOpcode match;
        if (backward) {
            start = isNegative ? RegExpOpcode.NEGATIVE_LOOKBEHIND : RegExpOpcode.LOOKBEHIND;
            match = isNegative ? RegExpOpcode.NEGATIVE_LOOKBEHIND_MATCH : RegExpOpcode.LOOKBEHIND_MATCH;
        } else {
            start = isNegative ? RegExpOpcode.NEGATIVE_LOOKAHEAD : RegExpOpcode.LOOKAHEAD;
            match = isNegative ? RegExpOpcode.NEGATIVE_LOOKAHEAD_MATCH : RegExpOpcode.LOOKAHEAD_MATCH;
        }
        context.buffer.appendU8(start.getCode());
        int lengthOffset = context.buffer.size();
        context.buffer.appendU32(0);
        compileDisjunction(context, backward);
        context.buffer.appendU8(match.getCode());
        context.buffer.setU32(lengthOffset, context.buffer.size() - lengthOffset - 4);
        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != ')') {
            throw new RegExpSyntaxException("Unterminated group");
        }
        context.pos++; // Skip ')'
    }

    private void compilePattern(CompileContext context) {
        // Save start of capture group 0
        context.buffer.appendU8(RegExpOpcode.SAVE_START.getCode());
        context.buffer.appendU16(0); // Capture group 0

        compileDisjunction(context, false);

        // Save end of capture group 0
        context.buffer.appendU8(RegExpOpcode.SAVE_END.getCode());
        context.buffer.appendU16(0); // Capture group 0
    }

    private void compileQuantifier(CompileContext context, int atomStart, int captureCountBeforeAtom) {
        int ch = context.codePoints[context.pos];
        int min, max;
        boolean greedy = true;

        // Parse quantifier
        switch (ch) {
            case '*' -> {
                min = 0;
                max = Integer.MAX_VALUE;
                context.pos++;
            }
            case '+' -> {
                min = 1;
                max = Integer.MAX_VALUE;
                context.pos++;
            }
            case '?' -> {
                min = 0;
                max = 1;
                context.pos++;
            }
            case '{' -> {
                // Parse {n} or {n,} or {n,m}
                context.pos++;
                int[] result = parseQuantifierBounds(context);
                min = result[0];
                max = result[1];
                if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '}') {
                    throw new RegExpSyntaxException("Unclosed quantifier at position " + context.pos);
                }
                context.pos++;
            }
            default -> {
                return; // Not a quantifier
            }
        }

        // Check for non-greedy modifier '?'
        if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '?') {
            greedy = false;
            context.pos++;
        }

        // Get the atom bytecode
        int atomEnd = context.buffer.size();
        int atomSize = atomEnd - atomStart;
        byte[] atomCode = context.buffer.getRange(atomStart, atomSize);

        // Remove the atom from the buffer
        context.buffer.truncate(atomStart);

        // Following QuickJS: check if the atom can match without advancing the position.
        // If so, we need to add SET_CHAR_POS/CHECK_ADVANCE to prevent infinite loops.
        boolean addZeroAdvanceCheck = needCheckAdvance(atomCode);

        // Following QuickJS: reset captures inside the quantifier body at each iteration.
        // This is needed when the atom contains optional captures (via SPLIT/GOTO) that
        // might not be set on every iteration. Without resetting, captures from a previous
        // iteration leak into the current one.
        boolean hasCapturesInAtom = captureCountBeforeAtom != captureCount;
        boolean needCaptureReset = hasCapturesInAtom && needCaptureInit(atomCode);
        boolean needInitialCaptureResetBeforeQuantifier = false;
        if (needCaptureReset) {
            // Prepend SAVE_RESET to the atom code so captures are cleared at each iteration
            byte[] resetPrefix = new byte[5];
            resetPrefix[0] = (byte) RegExpOpcode.SAVE_RESET.getCode();
            resetPrefix[1] = (byte) captureCountBeforeAtom;
            resetPrefix[2] = (byte) (captureCountBeforeAtom >>> 8);
            resetPrefix[3] = (byte) (captureCount - 1);
            resetPrefix[4] = (byte) ((captureCount - 1) >>> 8);
            byte[] newAtomCode = new byte[resetPrefix.length + atomCode.length];
            System.arraycopy(resetPrefix, 0, newAtomCode, 0, resetPrefix.length);
            System.arraycopy(atomCode, 0, newAtomCode, resetPrefix.length, atomCode.length);
            atomCode = newAtomCode;
            atomSize = atomCode.length;
        }
        if (hasCapturesInAtom && min == 0) {
            // When quant_min == 0 and all captures are always initialized in the atom,
            // we still need a one-time reset before the SPLIT for the case where the
            // atom is not executed at all (following QuickJS). This is also needed when
            // per-iteration resets are injected, because the zero-iteration branch skips
            // the atom bytecode entirely.
            needInitialCaptureResetBeforeQuantifier = true;
        }

        if (needInitialCaptureResetBeforeQuantifier) {
            context.buffer.appendU8(RegExpOpcode.SAVE_RESET.getCode());
            context.buffer.appendU16(captureCountBeforeAtom);
            context.buffer.appendU16(captureCount - 1);
        }

        // Allocate a unique register for this quantifier's advance check to avoid
        // conflicts with nested quantifiers that also use SET_CHAR_POS/CHECK_ADVANCE.
        int advReg = addZeroAdvanceCheck ? allocateRegister(context) : 0;

        // Implement quantifier using SPLIT opcodes
        if (min == 0 && max == 1) {
            // ? quantifier: SPLIT then atom
            // Greedy: try atom first, then skip
            // Non-greedy: try skip first, then atom
            if (addZeroAdvanceCheck) {
                context.buffer.appendU8(RegExpOpcode.SET_CHAR_POS.getCode());
                context.buffer.appendU8(advReg);
            }
            context.buffer.appendU8(
                    greedy ? RegExpOpcode.SPLIT_NEXT_FIRST.getCode() : RegExpOpcode.SPLIT_GOTO_FIRST.getCode());
            context.buffer.appendU32(atomSize + (addZeroAdvanceCheck ? 2 : 0));
            context.buffer.append(atomCode);
            if (addZeroAdvanceCheck) {
                context.buffer.appendU8(RegExpOpcode.CHECK_ADVANCE.getCode());
                context.buffer.appendU8(advReg);
            }
        } else if (min == 0 && max == Integer.MAX_VALUE) {
            // * quantifier: SPLIT (skip or continue), SET_CHAR_POS?, atom, CHECK_ADVANCE?, GOTO back
            // Following QuickJS: if atom can match empty, insert SET_CHAR_POS before atom and
            // CHECK_ADVANCE after atom to prevent infinite loops on zero-width patterns.
            emitUnboundedRepeat(context, atomCode, greedy, addZeroAdvanceCheck, advReg);
        } else if (min == 1 && max == Integer.MAX_VALUE) {
            // + quantifier: atom, then SPLIT back or continue
            // With advance check: atom, SET_CHAR_POS, SPLIT, SET_CHAR_POS, atom, CHECK_ADVANCE, GOTO SPLIT
            if (addZeroAdvanceCheck) {
                // First iteration (no advance check needed)
                context.buffer.append(atomCode);
                emitUnboundedRepeat(context, atomCode, greedy, true, advReg);
            } else {
                context.buffer.append(atomCode);
                int loopStart = context.buffer.size() - atomSize;
                context.buffer.appendU8(
                        greedy ? RegExpOpcode.SPLIT_GOTO_FIRST.getCode() : RegExpOpcode.SPLIT_NEXT_FIRST.getCode());
                int offset = loopStart - (context.buffer.size() + 4);
                context.buffer.appendU32(offset);
            }
        } else {
            // Count large repetitions instead of changing their bounds or expanding bytecode linearly.
            if (min > MAX_UNROLLED_QUANTIFIER_REPETITIONS) {
                int register = allocateRegister(context);
                emitCounter(context, register, min);
                int loopStart = context.buffer.size();
                context.buffer.append(atomCode);
                emitLoop(context, register, loopStart);
                // The counter is dead once the loop has run; the optional repetitions below can count in it.
                context.nextRegister = register;
            } else {
                for (int i = 0; i < min; i++) {
                    context.buffer.append(atomCode);
                }
            }
            if (max != min) {
                if (max == Integer.MAX_VALUE) {
                    // {n,} - unbounded: use loop with advance check if needed
                    emitUnboundedRepeat(context, atomCode, greedy, addZeroAdvanceCheck, advReg);
                } else {
                    int remaining = max - min;
                    int register = allocateRegister(context);
                    emitCounter(context, register, remaining);
                    int loopStart = context.buffer.size();
                    context.buffer.appendU8(
                            greedy ? RegExpOpcode.SPLIT_NEXT_FIRST.getCode() : RegExpOpcode.SPLIT_GOTO_FIRST.getCode());
                    context.buffer.appendU32(atomSize + 6 + (addZeroAdvanceCheck ? 4 : 0));
                    emitRepeatedAtom(context, atomCode, addZeroAdvanceCheck, advReg);
                    emitLoop(context, register, loopStart);
                }
            }
        }
    }

    private void compileTerm(CompileContext context, boolean isBackwardDirection) {
        int atomStart = context.buffer.size();
        int captureCountBeforeAtom = captureCount;
        int registerBase = context.nextRegister;
        int enclosingPeak = context.registerPeak;
        context.registerPeak = registerBase;

        // Parse the atom
        compileAtom(context, isBackwardDirection);

        // The atom's own quantifiers have released their registers, but a quantifier wrapped around the atom runs
        // them on every iteration, so its counters go above the highest register the atom reached.
        int atomPeak = context.registerPeak;
        context.registerPeak = Math.max(enclosingPeak, atomPeak);

        // Check for quantifier
        if (context.pos < context.codePoints.length) {
            int ch = context.codePoints[context.pos];

            if (!context.lastAtomCanRepeat
                    && (ch == '*' || ch == '+' || ch == '?' || (ch == '{' && startsWithValidQuantifier(context)))) {
                throw new RegExpSyntaxException("Nothing to repeat");
            }

            if (ch == '*' || ch == '+' || ch == '?' || (ch == '{' && startsWithValidQuantifier(context))) {
                context.nextRegister = atomPeak;
                compileQuantifier(context, atomStart, captureCountBeforeAtom);
            } else if (ch == '{' && context.isUnicodeMode() && context.lastAtomCanRepeat) {
                // After an atom an unparsable '{' is a broken quantifier, as V8 reports it, not a lone bracket.
                throw new RegExpSyntaxException("Incomplete quantifier");
            }
        }
        // The term is complete and its counters are dead: the next term starts from the same registers.
        context.nextRegister = registerBase;
    }

    private void emitCounter(CompileContext context, int register, int count) {
        context.buffer.appendU8(RegExpOpcode.SET_I32.getCode());
        context.buffer.appendU8(register);
        context.buffer.appendU32(count);
    }

    private void emitLoop(CompileContext context, int register, int target) {
        context.buffer.appendU8(RegExpOpcode.LOOP.getCode());
        context.buffer.appendU8(register);
        context.buffer.appendU32(target - (context.buffer.size() + 4));
    }

    private void ensureGroupNameSize(int size) {
        while (groupNames.size() < size) {
            groupNames.add(null);
        }
    }

    private boolean isInlineModifierFlag(int ch) {
        return ch == 'i' || ch == 'm' || ch == 's';
    }

    private boolean isJsIdentifierPart(int codePoint) {
        return codePoint == '$' || codePoint == '_' || codePoint == 0x200C || codePoint == 0x200D
                || Character.isUnicodeIdentifierPart(codePoint);
    }

    private boolean isJsIdentifierStart(int codePoint) {
        return codePoint == '$' || codePoint == '_' || Character.isUnicodeIdentifierStart(codePoint);
    }

    /**
     * Following QuickJS re_need_check_adv_and_capture_init: determines if captures inside a quantifier body need
     * explicit resetting at each iteration. Returns true if the atom contains complex opcodes (SPLIT, GOTO, back
     * references) that might cause some captures to not be initialized on every iteration.
     */
    private boolean needCaptureInit(byte[] atomCode) {
        int pos = 0;
        while (pos < atomCode.length) {
            int opcode = atomCode[pos] & 0xFF;
            RegExpOpcode op = RegExpOpcode.fromCode(opcode);
            switch (op) {
                case CHAR, CHAR_I, CHAR32, CHAR32_I, DOT, ANY, SPACE, NOT_SPACE, PREV :
                    break;
                case RANGE, RANGE_I, RANGE32, RANGE32_I, NOT_RANGE, NOT_RANGE_I : {
                    pos += 5 + readRangePayloadLength(atomCode, pos);
                    continue;
                }
                case LINE_START, LINE_START_M, LINE_END, LINE_END_M, WORD_BOUNDARY, WORD_BOUNDARY_I, NOT_WORD_BOUNDARY,
                        NOT_WORD_BOUNDARY_I, SAVE_START, SAVE_END, SAVE_RESET, SET_CHAR_POS, SET_I32 :
                    break;
                case BACK_REFERENCE, BACK_REFERENCE_I, BACKWARD_BACK_REFERENCE, BACKWARD_BACK_REFERENCE_I :
                    return true;
                default :
                    // Complex opcode (SPLIT, GOTO, etc.) - captures may not be initialized
                    return true;
            }
            pos += op.getLength();
        }
        return false;
    }

    /**
     * Determine whether a quantified atom needs a zero-advance check: whether it might match without advancing the
     * position (a lookahead, an anchor, a word boundary, an optional body). Following QuickJS re_check_advance, the
     * first opcode that settles the question decides: everything before the first branch runs on every path through the
     * atom, so a leading character match proves the atom advances, while a branch, loop or other complex opcode met
     * first leaves the outcome unknown. Scanning past that point would only add checks, and registers, that
     * {@code (?:a{1,2}){1,2}} does not need.
     */
    private boolean needCheckAdvance(byte[] atomCode) {
        int pos = 0;
        while (pos < atomCode.length) {
            int opcode = atomCode[pos] & 0xFF;
            RegExpOpcode op = RegExpOpcode.fromCode(opcode);
            switch (op) {
                case CHAR, CHAR_I, CHAR32, CHAR32_I, DOT, ANY, SPACE, NOT_SPACE :
                    // These always advance the position
                    return false;
                case PREV :
                    // PREV moves the position in backward-compiled lookbehind atoms.
                    return false;
                case RANGE, RANGE_I, RANGE32, RANGE32_I, NOT_RANGE, NOT_RANGE_I :
                    return false;
                case LINE_START, LINE_START_M, LINE_END, LINE_END_M, WORD_BOUNDARY, WORD_BOUNDARY_I, NOT_WORD_BOUNDARY,
                        NOT_WORD_BOUNDARY_I, SAVE_START, SAVE_END, SAVE_RESET, SET_CHAR_POS, SET_I32 :
                    // These don't advance - no effect on the check
                    break;
                default :
                    // Unknown or complex opcode - assume might not advance
                    return true;
            }
            pos += op.getLength();
        }
        return true;
    }

    private GroupNameParseResult parseGroupName(int[] codePoints, int startPos) {
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

    private int[] parseQuantifierBounds(CompileContext context) {
        long minLong = 0;
        int min;
        int max;

        // Parse first number
        while (context.pos < context.codePoints.length) {
            int ch = context.codePoints[context.pos];
            if (ch >= '0' && ch <= '9') {
                minLong = appendDecimalDigitWithClamp(minLong, ch - '0');
                context.pos++;
            } else {
                break;
            }
        }
        min = (int) Math.min(minLong, MAX_QUANTIFIER_BOUND);

        // Check for comma
        if (context.pos < context.codePoints.length && context.codePoints[context.pos] == ',') {
            context.pos++;

            // Check if there's a max value
            if (context.pos < context.codePoints.length && context.codePoints[context.pos] >= '0'
                    && context.codePoints[context.pos] <= '9') {
                long maxLong = 0;
                while (context.pos < context.codePoints.length) {
                    int ch = context.codePoints[context.pos];
                    if (ch >= '0' && ch <= '9') {
                        maxLong = appendDecimalDigitWithClamp(maxLong, ch - '0');
                        context.pos++;
                    } else {
                        break;
                    }
                }
                max = (int) Math.min(maxLong, MAX_QUANTIFIER_BOUND);
            } else {
                max = Integer.MAX_VALUE; // {n,} means n or more
            }
        } else {
            max = min; // {n} means exactly n
        }

        if (max < min) {
            throw new RegExpSyntaxException("numbers out of order in {} quantifier");
        }

        return new int[]{min, max};
    }

    private void reverseAlternativeTerm(CompileContext context, int alternativeStart, int termStart) {
        if (termStart <= alternativeStart || termStart >= context.buffer.size()) {
            return;
        }
        int end = context.buffer.size();
        byte[] previousTerms = context.buffer.getRange(alternativeStart, termStart - alternativeStart);
        byte[] currentTerm = context.buffer.getRange(termStart, end - termStart);
        context.buffer.truncate(alternativeStart);
        context.buffer.append(currentTerm);
        context.buffer.append(previousTerms);
    }

    private Map<String, Integer> scanNamedCaptureGroups(String pattern) {
        int[] codePoints = pattern.codePoints().toArray();
        Map<String, Integer> captureIndices = new HashMap<>();
        boolean inCharacterClass = false;
        int captureIndex = 1;

        // Track disjunction nesting for ES2025 duplicate named groups.
        // Duplicate names are allowed in different alternatives of the same disjunction.
        // currentAltStack: names seen in the current alternative at each nesting level
        // allAltStack: names from all completed alternatives at each nesting level
        Deque<Set<String>> currentAltStack = new ArrayDeque<>();
        Deque<Set<String>> allAltStack = new ArrayDeque<>();
        currentAltStack.push(new HashSet<>());
        allAltStack.push(new HashSet<>());

        for (int i = 0; i < codePoints.length; i++) {
            int codePoint = codePoints[i];

            if (codePoint == '\\') {
                if (i + 1 < codePoints.length) {
                    i++;
                }
                continue;
            }
            if (inCharacterClass) {
                if (codePoint == ']') {
                    inCharacterClass = false;
                }
                continue;
            }
            if (codePoint == '[') {
                inCharacterClass = true;
                continue;
            }
            if (codePoint == '|') {
                allAltStack.peek().addAll(currentAltStack.peek());
                currentAltStack.peek().clear();
                continue;
            }
            if (codePoint == ')') {
                if (currentAltStack.size() > 1) {
                    Set<String> poppedCurrent = currentAltStack.pop();
                    Set<String> poppedAll = allAltStack.pop();
                    poppedAll.addAll(poppedCurrent);
                    currentAltStack.peek().addAll(poppedAll);
                }
                continue;
            }
            if (codePoint != '(') {
                continue;
            }

            if (i + 1 < codePoints.length && codePoints[i + 1] == '?') {
                if (i + 2 >= codePoints.length) {
                    currentAltStack.push(new HashSet<>());
                    allAltStack.push(new HashSet<>());
                    continue;
                }
                int groupType = codePoints[i + 2];
                if (groupType == ':' || groupType == '=' || groupType == '!' || groupType == '>') {
                    currentAltStack.push(new HashSet<>());
                    allAltStack.push(new HashSet<>());
                    continue;
                }
                if (groupType == '<') {
                    if (i + 3 < codePoints.length && (codePoints[i + 3] == '=' || codePoints[i + 3] == '!')) {
                        currentAltStack.push(new HashSet<>());
                        allAltStack.push(new HashSet<>());
                        continue;
                    }
                    GroupNameParseResult groupNameParseResult = parseGroupName(codePoints, i + 3);
                    if (groupNameParseResult == null) {
                        throw new RegExpSyntaxException("Invalid capture group name");
                    }
                    String name = groupNameParseResult.name();
                    if (currentAltStack.peek().contains(name)) {
                        throw new RegExpSyntaxException("Duplicate capture group name");
                    }
                    currentAltStack.peek().add(name);
                    if (!captureIndices.containsKey(name)) {
                        captureIndices.put(name, captureIndex);
                    }
                    captureIndex++;
                    i = groupNameParseResult.nextPos() - 1;
                    currentAltStack.push(new HashSet<>());
                    allAltStack.push(new HashSet<>());
                    continue;
                }
                currentAltStack.push(new HashSet<>());
                allAltStack.push(new HashSet<>());
                continue;
            }

            captureIndex++;
            currentAltStack.push(new HashSet<>());
            allAltStack.push(new HashSet<>());
        }
        totalCaptureCount = captureIndex;
        return captureIndices;
    }

    private boolean startsWithValidQuantifier(CompileContext context) {
        if (context.pos >= context.codePoints.length || context.codePoints[context.pos] != '{') {
            return false;
        }
        int pos = context.pos + 1;
        if (pos >= context.codePoints.length || context.codePoints[pos] < '0' || context.codePoints[pos] > '9') {
            return false;
        }
        while (pos < context.codePoints.length && context.codePoints[pos] >= '0' && context.codePoints[pos] <= '9') {
            pos++;
        }
        if (pos < context.codePoints.length && context.codePoints[pos] == ',') {
            pos++;
            while (pos < context.codePoints.length && context.codePoints[pos] >= '0'
                    && context.codePoints[pos] <= '9') {
                pos++;
            }
        }
        return pos < context.codePoints.length && context.codePoints[pos] == '}';
    }

    private int toInlineModifierFlagBit(int ch) {
        return switch (ch) {
            case 'i' -> RegExpBytecode.FLAG_IGNORECASE;
            case 'm' -> RegExpBytecode.FLAG_MULTILINE;
            case 's' -> RegExpBytecode.FLAG_DOTALL;
            default -> 0;
        };
    }

    /**
     * Parse only a 4-digit \\uHHHH escape at the given position. Per QuickJS, surrogate pair combining only applies to
     * 4-digit escapes.
     */

    private InlineModifierGroupParseResult tryParseInlineModifierGroup(CompileContext context) {
        if (context.pos >= context.codePoints.length) {
            return null;
        }
        int startChar = context.codePoints[context.pos];
        if (!(isInlineModifierFlag(startChar) || startChar == '-')) {
            return null;
        }

        int addFlags = 0;
        int removeFlags = 0;
        boolean parsingRemoveFlags = false;
        boolean sawDash = false;
        boolean sawModifierFlag = false;

        while (context.pos < context.codePoints.length) {
            int currentChar = context.codePoints[context.pos];
            if (currentChar == ':') {
                if (!sawModifierFlag) {
                    throw new RegExpSyntaxException("Invalid flag group");
                }
                context.pos++;
                int scopedFlags = (context.currentFlags | addFlags) & ~removeFlags;
                return new InlineModifierGroupParseResult(scopedFlags);
            }
            if (currentChar == '-') {
                if (sawDash) {
                    throw new RegExpSyntaxException("Multiple dashes in flag group");
                }
                sawDash = true;
                parsingRemoveFlags = true;
                context.pos++;
                continue;
            }
            if (!isInlineModifierFlag(currentChar)) {
                throw new RegExpSyntaxException("Invalid group");
            }
            int flagBit = toInlineModifierFlagBit(currentChar);
            if (!parsingRemoveFlags) {
                if ((addFlags & flagBit) != 0) {
                    throw new RegExpSyntaxException("Repeated flag in flag group");
                }
                addFlags |= flagBit;
            } else {
                if ((removeFlags & flagBit) != 0 || (addFlags & flagBit) != 0) {
                    throw new RegExpSyntaxException("Repeated flag in flag group");
                }
                removeFlags |= flagBit;
            }
            sawModifierFlag = true;
            context.pos++;
        }

        throw new RegExpSyntaxException("Invalid group");
    }

    private static int combineTrailingLowSurrogateEscapeIfPresent(CompileContext context, int codePoint) {
        if (!context.isUnicodeMode()) {
            return codePoint;
        }
        if (!Character.isHighSurrogate((char) codePoint)) {
            return codePoint;
        }
        // Per QuickJS: only combine with 4-digit \\uHHHH trailing escapes,
        // NOT braced \\u{XXXX} escapes.
        EscapedCodePoint unicodeEscapeParseResult = tryParseUnicodeEscapeAt(context.codePoints, context.pos, false);
        if (unicodeEscapeParseResult == null) {
            return codePoint;
        }
        int trailingCodePoint = unicodeEscapeParseResult.codePoint();
        if (!Character.isLowSurrogate((char) trailingCodePoint)) {
            return codePoint;
        }
        context.pos = unicodeEscapeParseResult.nextPos();
        return Character.toCodePoint((char) codePoint, (char) trailingCodePoint);
    }

    static void compileLiteralChar(CompileContext context, int ch) {
        if (ch <= 0xFFFF) {
            context.buffer
                    .appendU8(context.isIgnoreCase() ? RegExpOpcode.CHAR_I.getCode() : RegExpOpcode.CHAR.getCode());
            context.buffer.appendU16(ch);
        } else {
            context.buffer
                    .appendU8(context.isIgnoreCase() ? RegExpOpcode.CHAR32_I.getCode() : RegExpOpcode.CHAR32.getCode());
            context.buffer.appendU32(ch);
        }
    }

    static void compileLiteralChar(CompileContext context, int ch, boolean isBackwardDirection) {
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
        compileLiteralChar(context, ch);
        if (isBackwardDirection) {
            context.buffer.appendU8(RegExpOpcode.PREV.getCode());
        }
    }

    private static void emitRepeatedAtom(CompileContext context, byte[] atomCode, boolean addZeroAdvanceCheck,
            int advReg) {
        if (addZeroAdvanceCheck) {
            context.buffer.appendU8(RegExpOpcode.SET_CHAR_POS.getCode());
            context.buffer.appendU8(advReg);
        }
        context.buffer.append(atomCode);
        if (addZeroAdvanceCheck) {
            context.buffer.appendU8(RegExpOpcode.CHECK_ADVANCE.getCode());
            context.buffer.appendU8(advReg);
        }
    }

    private static void emitUnboundedRepeat(CompileContext context, byte[] atomCode, boolean greedy,
            boolean addZeroAdvanceCheck, int advReg) {
        int advCheckSize = addZeroAdvanceCheck ? 4 : 0; // SET_CHAR_POS(2) + CHECK_ADVANCE(2)
        int loopStart = context.buffer.size();
        context.buffer
                .appendU8(greedy ? RegExpOpcode.SPLIT_NEXT_FIRST.getCode() : RegExpOpcode.SPLIT_GOTO_FIRST.getCode());
        context.buffer.appendU32(atomCode.length + 5 + advCheckSize); // Skip atom + GOTO + advance checks
        emitRepeatedAtom(context, atomCode, addZeroAdvanceCheck, advReg);
        context.buffer.appendU8(RegExpOpcode.GOTO.getCode());
        int offset = loopStart - (context.buffer.size() + 4);
        context.buffer.appendU32(offset);
    }

    private static int fixedHexValue(int[] codePoints, int start, int digits) {
        if (start > codePoints.length - digits) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < digits; i++) {
            int digit = hexValue(codePoints[start + i]);
            if (digit < 0) {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    private static int hexValue(int ch) {
        return ch >= 0 && ch < HEX_VALUES.length ? HEX_VALUES[ch] : -1;
    }

    private static byte[] hexValues() {
        byte[] values = new byte[128];
        java.util.Arrays.fill(values, (byte) -1);
        for (int i = 0; i < 16; i++) {
            if (i < 10) {
                values['0' + i] = (byte) i;
            } else {
                values['a' + i - 10] = (byte) i;
                values['A' + i - 10] = (byte) i;
            }
        }
        return values;
    }

    /**
     * Returns true if the character is a RegExp syntax character per ES2024 11.8.5. These are: ^ $ \ . * + ? ( ) [ ] {
     * } | /
     */
    static boolean isSyntaxCharacter(int ch) {
        return ch == '^' || ch == '$' || ch == '\\' || ch == '.' || ch == '*' || ch == '+' || ch == '?' || ch == '('
                || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}' || ch == '|' || ch == '/';
    }

    private static int parseBracedUnicodeEscape(CompileContext context) {
        int firstDigit = ++context.pos;
        int value = 0;
        while (context.pos < context.codePoints.length && context.codePoints[context.pos] != '}') {
            int digit = hexValue(context.codePoints[context.pos]);
            if (digit < 0) {
                throw new RegExpSyntaxException("Invalid unicode escape");
            }
            context.pos++;
            value = (value << 4) | digit;
            if (value > MAX_UNICODE_CODE_POINT) {
                throw new RegExpSyntaxException("Unicode escape out of range");
            }
        }
        if (context.pos == firstDigit) {
            throw new RegExpSyntaxException("Empty unicode escape");
        }
        if (context.pos == context.codePoints.length) {
            throw new RegExpSyntaxException("Invalid unicode escape");
        }
        context.pos++;
        // Only four-digit escapes combine with a trailing escaped surrogate.
        return value;
    }

    static int parseLegacyOctalEscape(CompileContext context, int firstDigit) {
        int value = firstDigit;
        if (context.pos < context.codePoints.length) {
            int next = context.codePoints[context.pos];
            if (next >= '0' && next <= '7') {
                value = (value << 3) | (next - '0');
                context.pos++;
                if (value < 32 && context.pos < context.codePoints.length) {
                    int third = context.codePoints[context.pos];
                    if (third >= '0' && third <= '7') {
                        value = (value << 3) | (third - '0');
                        context.pos++;
                    }
                }
            }
        }
        return value;
    }

    static int parseUnicodeEscape(CompileContext context) {
        if (context.isUnicodeMode() && context.pos < context.codePoints.length
                && context.codePoints[context.pos] == '{') {
            return parseBracedUnicodeEscape(context);
        }
        int value = readFixedHexEscape(context, 'u', 4);
        return combineTrailingLowSurrogateEscapeIfPresent(context, value);
    }

    /** Leave the cursor on the first following character for an Annex B identity escape. */
    static int readFixedHexEscape(CompileContext context, int escape, int digits) {
        int value = fixedHexValue(context.codePoints, context.pos, digits);
        if (value >= 0) {
            context.pos += digits;
            return value;
        }
        if (context.isUnicodeMode()) {
            throw new RegExpSyntaxException(escape == 'x' ? "Invalid hex escape" : "Invalid unicode escape");
        }
        return escape;
    }

    /**
     * The declared payload length of a {@code RANGE}-family instruction.
     * <p>
     * The compiler re-reads its own bytecode when it decides whether a quantifier body always advances and whether it
     * needs per-iteration capture resets, so the header layout is known in two places besides the matcher. When the
     * length field was widened from 16 to 32 bits these readers kept decoding two bytes, walked off the end of the
     * instruction, and concluded that a body as ordinary as {@code \D} might not advance — which added an advance check
     * and a register to every iteration and made a long subject exhaust the backtracking budget.
     *
     * @param code
     *            the atom bytecode
     * @param offset
     *            the offset of the opcode byte
     * @return the number of payload bytes following the length field
     */
    private static int readRangePayloadLength(byte[] code, int offset) {
        return (code[offset + 1] & 0xFF) | ((code[offset + 2] & 0xFF) << 8) | ((code[offset + 3] & 0xFF) << 16)
                | ((code[offset + 4] & 0xFF) << 24);
    }

    static EscapedCodePoint tryParseUnicodeEscapeAt(int[] codePoints, int startPos, boolean allowBraced) {
        if (startPos + 1 >= codePoints.length || codePoints[startPos] != '\\' || codePoints[startPos + 1] != 'u') {
            return null;
        }
        int currentPos = startPos + 2;
        if (currentPos < codePoints.length && codePoints[currentPos] == '{') {
            if (!allowBraced) {
                return null;
            }
            currentPos++;
            int value = 0;
            int digitCount = 0;
            while (currentPos < codePoints.length && codePoints[currentPos] != '}') {
                int hexValue = hexValue(codePoints[currentPos]);
                if (hexValue < 0) {
                    return null;
                }
                value = (value << 4) | hexValue;
                digitCount++;
                if (digitCount > 6 || value > MAX_UNICODE_CODE_POINT) {
                    return null;
                }
                currentPos++;
            }
            if (digitCount == 0 || currentPos >= codePoints.length || codePoints[currentPos] != '}') {
                return null;
            }
            return new EscapedCodePoint(value, currentPos + 1);
        }
        int value = fixedHexValue(codePoints, currentPos, 4);
        if (value < 0) {
            return null;
        }
        return new EscapedCodePoint(value, currentPos + 4);
    }

    static final class CompileContext {
        final DynamicBuffer buffer;
        final int[] codePoints;
        int currentFlags;
        final int flags;
        boolean lastAtomCanRepeat;
        boolean lastUnicodeSetsOperandWasRange;
        /** The next free register; released back to a term's base once the term is compiled. */
        int nextRegister;
        final String pattern;
        int pos;
        final UnicodePropertyResolver properties;
        /** Registers the whole pattern needs, the high-water mark of every allocation. */
        int registerCount;
        /** The highest register reached while compiling the current term's atom. */
        int registerPeak;

        CompileContext(String pattern, int flags, DynamicBuffer buffer, UnicodePropertyResolver properties) {
            this.properties = properties;
            this.pattern = pattern;
            // In non-unicode mode, work with UTF-16 code units so that
            // raw surrogates in the pattern are individual atoms.
            // In unicode mode, combine surrogate pairs into code points.
            boolean isUnicodeMode = (flags & (RegExpBytecode.FLAG_UNICODE | RegExpBytecode.FLAG_UNICODE_SETS)) != 0;
            this.codePoints = isUnicodeMode ? pattern.codePoints().toArray() : pattern.chars().toArray();
            this.flags = flags;
            this.currentFlags = flags;
            this.buffer = buffer;
            this.lastAtomCanRepeat = false;
            this.nextRegister = 0;
            this.registerCount = 0;
            this.registerPeak = 0;
            this.pos = 0;
        }

        boolean consume(int codePoint) {
            if (pos == codePoints.length || codePoints[pos] != codePoint) {
                return false;
            }
            pos++;
            return true;
        }

        boolean isDotAll() {
            return (currentFlags & RegExpBytecode.FLAG_DOTALL) != 0;
        }

        boolean isIgnoreCase() {
            return (currentFlags & RegExpBytecode.FLAG_IGNORECASE) != 0;
        }

        boolean isMultiline() {
            return (currentFlags & RegExpBytecode.FLAG_MULTILINE) != 0;
        }

        boolean isUnicodeMode() {
            return (currentFlags & (RegExpBytecode.FLAG_UNICODE | RegExpBytecode.FLAG_UNICODE_SETS)) != 0;
        }

        boolean isUnicodeSetsMode() {
            return (currentFlags & RegExpBytecode.FLAG_UNICODE_SETS) != 0;
        }
    }

    record EscapedCodePoint(int codePoint, int nextPos) {
    }

    private record GroupNameParseResult(String name, int nextPos) {
    }
    private record InlineModifierGroupParseResult(int scopedFlags) {
    }

    /**
     * Exception thrown when regex pattern has syntax errors.
     */
    public static class RegExpSyntaxException extends RuntimeException {
        public RegExpSyntaxException(String message) {
            super(message);
        }

        public RegExpSyntaxException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
