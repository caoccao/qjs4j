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

import static com.caoccao.qjs4j.regexp.RegExpEscapes.*;

import com.caoccao.qjs4j.unicode.UnicodePropertyResolver;
import com.caoccao.qjs4j.utils.DynamicBuffer;

import java.util.*;

/**
 * Compiles regex patterns to bytecode. Based on QuickJS libregexp.c compiler.
 * <p>
 * Supports captures, lookarounds, Unicode properties and quantified subexpressions.
 */
public final class RegExpCompiler {

    static final int MAX_UNICODE_CODE_POINT = 0x10FFFF;

    private final UnicodePropertyResolver unicodePropertyResolver;

    public RegExpCompiler(UnicodePropertyResolver unicodePropertyResolver) {
        this.unicodePropertyResolver = unicodePropertyResolver;
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
        DynamicBuffer buffer = new DynamicBuffer(256);
        CompileContext context = new CompileContext(pattern, flagBits, buffer, unicodePropertyResolver);

        try {
            compilePattern(context);
            if (context.pos != context.codePoints.length) {
                throw new RegExpSyntaxException(
                        context.peek() == ')' ? "Unmatched ')'" : "Unexpected character in pattern");
            }
            // End with MATCH opcode
            context.emit(RegExpOpcode.MATCH);

            return new RegExpBytecode(buffer.toByteArray(), context.flags, context.captures.count(),
                    // Registers this pattern actually allocated. The matcher saves them on every
                    // backtrack point where state changed, so reporting the real count instead of
                    // the maximum is the difference between 12 and 76 bytes of saved state per
                    // point for a typical pattern.
                    context.registerCount, context.captures.names());
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
        while (context.hasMore()) {
            int ch = context.peek();

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
        if (!context.hasMore()) {
            return;
        }
        context.lastAtomCanRepeat = true;

        int ch = context.peek();

        switch (ch) {
            case '^' -> {
                context.lastAtomCanRepeat = false;
                context.emit(context.isMultiline() ? RegExpOpcode.LINE_START_M : RegExpOpcode.LINE_START);
                context.pos++;
            }

            case '$' -> {
                context.lastAtomCanRepeat = false;
                context.emit(context.isMultiline() ? RegExpOpcode.LINE_END_M : RegExpOpcode.LINE_END);
                context.pos++;
            }

            case '.' -> {
                if (isBackwardDirection) {
                    context.emit(RegExpOpcode.PREV);
                }
                context.emit(context.isDotAll() ? RegExpOpcode.ANY : RegExpOpcode.DOT);
                if (isBackwardDirection) {
                    context.emit(RegExpOpcode.PREV);
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
                if (RegExpQuantifier.read(context) != null) {
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
        while (context.consume('|')) {

            int len = context.buffer.size() - start;

            // Insert a SPLIT at the start to choose between alternatives
            context.buffer.insert(start, 5);
            context.buffer.setU8(start, RegExpOpcode.SPLIT_NEXT_FIRST.getCode());
            context.buffer.setU32(start + 1, len + 5);

            // Emit GOTO to skip the next alternative
            int target = context.emitForwardJump(RegExpOpcode.GOTO);

            // Compile next alternative
            compileAlternative(context, isBackwardDirection);

            context.patchJumpToHere(target);
        }
    }

    private void compileEscape(CompileContext context, boolean isBackwardDirection) {
        if (!context.hasMore()) {
            throw new RegExpSyntaxException("Incomplete escape sequence");
        }

        int ch = context.read();
        switch (ch) {
            case 'n', 'r', 't', 'f', 'v', '0', 'x', 'u' ->
                compileLiteralChar(context, readCharacterEscape(context, ch), isBackwardDirection);
            case 'd', 'D', 'w', 'W', 's', 'S' -> {
                if (isBackwardDirection) {
                    context.emit(RegExpOpcode.PREV);
                }
                if (ch == 's' || ch == 'S') {
                    context.emit(ch == 's' ? RegExpOpcode.SPACE : RegExpOpcode.NOT_SPACE);
                } else {
                    boolean inverted = ch == 'D' || ch == 'W';
                    context.emit(inverted ? RegExpOpcode.NOT_RANGE : RegExpOpcode.RANGE);
                    RegExpCharacterClass.emitRangePayload(context,
                            RegExpCharacterClass.characterClassRanges(context, Character.toLowerCase(ch)));
                }
                if (isBackwardDirection) {
                    context.emit(RegExpOpcode.PREV);
                }
            }
            case 'b' -> {
                // \b - match word boundary
                context.emit(context.isIgnoreCase() && context.isUnicodeMode()
                        ? RegExpOpcode.WORD_BOUNDARY_I
                        : RegExpOpcode.WORD_BOUNDARY);
            }
            case 'B' -> {
                // \B - match non-word boundary
                context.emit(context.isIgnoreCase() && context.isUnicodeMode()
                        ? RegExpOpcode.NOT_WORD_BOUNDARY_I
                        : RegExpOpcode.NOT_WORD_BOUNDARY);
            }
            case 'k' -> {
                // AnnexB: In non-unicode mode with no named capture groups,
                // \k is an identity escape matching literal 'k'
                if (!context.isUnicodeMode() && !context.captures.hasNames()) {
                    compileLiteralChar(context, 'k', isBackwardDirection);
                    break;
                }
                if (!context.consume('<')) {
                    throw new RegExpSyntaxException("expecting group name");
                }

                String name = RegExpCaptures.readName(context);
                Integer groupNum = context.captures.indexOf(name);
                if (groupNum == null) {
                    throw new RegExpSyntaxException("Invalid named capture referenced");
                }

                emitBackReference(context, isBackwardDirection, groupNum);
            }
            case 'p', 'P' -> {
                if (!context.isUnicodeMode()) {
                    compileLiteralChar(context, ch, isBackwardDirection);
                    break;
                }
                RegExpCharacterClass.compileUnicodePropertyEscape(context, ch == 'P', isBackwardDirection);
            }
            case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                // Back reference \1, \2, etc.
                // Use totalCaptureCount (pre-scanned) to support forward references
                int groupNum = ch - '0';
                int savedPos = context.pos;
                // Check for multi-digit group numbers
                while (context.hasMore()) {
                    int nextCh = context.peek();
                    if (nextCh >= '0' && nextCh <= '9') {
                        groupNum = groupNum * 10 + (nextCh - '0');
                        context.pos++;
                    } else {
                        break;
                    }
                }

                if (groupNum < context.captures.totalCount()) {
                    // Valid backreference (including forward references)
                    emitBackReference(context, isBackwardDirection, groupNum);
                } else if (!context.isUnicodeMode()) {
                    // AnnexB: In non-unicode mode, invalid backreferences are
                    // treated as octal escapes (\1-\7) or identity escapes (\8-\9)
                    context.pos = savedPos; // Rewind consumed digits
                    int digit = ch - '0';
                    if (digit <= 7) {
                        // Octal escape: \1-\7 → U+0001-U+0007
                        // Also handle multi-digit octal: \12, \377, etc.
                        int octalValue = parseLegacyOctalEscape(context, digit);
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
                int code = readControlLetter(context, false);
                if (code < 0) {
                    context.pos--; // Annex B reinterprets the c as the next atom.
                    code = '\\';
                }
                compileLiteralChar(context, code, isBackwardDirection);
            }
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
        if (context.consume('?')) {
            if (!context.hasMore()) {
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
                if (!context.consume(')')) {
                    throw new RegExpSyntaxException("Unterminated group");
                }
                context.lastAtomCanRepeat = true;
                return;
            }
            int groupType = context.read();
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
                if (context.hasMore() && (context.peek() == '=' || context.peek() == '!')) {
                    boolean isNegative = context.peek() == '!';
                    context.pos++;
                    compileLookaround(context, isNegative, true);
                    context.lastAtomCanRepeat = false;
                    return;
                }

                captureName = RegExpCaptures.readName(context);
            } else {
                throw new RegExpSyntaxException("Invalid group");
            }
        }

        int groupIndex = -1;
        if (isCapturing) {
            groupIndex = context.captures.add(captureName);
            // Save start
            context.emitU16(isBackwardDirection ? RegExpOpcode.SAVE_END : RegExpOpcode.SAVE_START, groupIndex);
        }

        // Compile group contents
        compileDisjunction(context, isBackwardDirection);

        if (isCapturing) {
            // Save end
            context.emitU16(isBackwardDirection ? RegExpOpcode.SAVE_START : RegExpOpcode.SAVE_END, groupIndex);
        }

        if (!context.consume(')')) {
            throw new RegExpSyntaxException("Unterminated group");
        }
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
        int target = context.emitForwardJump(start);
        compileDisjunction(context, backward);
        context.emit(match);
        context.patchJumpToHere(target);
        if (!context.consume(')')) {
            throw new RegExpSyntaxException("Unterminated group");
        }
    }

    private void compilePattern(CompileContext context) {
        // Save start of capture group 0
        context.emitU16(RegExpOpcode.SAVE_START, 0); // Capture group 0

        compileDisjunction(context, false);

        // Save end of capture group 0
        context.emitU16(RegExpOpcode.SAVE_END, 0); // Capture group 0
    }

    private void compileTerm(CompileContext context, boolean isBackwardDirection) {
        int atomStart = context.buffer.size();
        int captureCountBeforeAtom = context.captures.count();
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
        if (context.hasMore()) {
            int ch = context.peek();

            RegExpQuantifier quantifier = RegExpQuantifier.read(context);
            if (quantifier != null) {
                if (!context.lastAtomCanRepeat) {
                    throw new RegExpSyntaxException("Nothing to repeat");
                }
                context.nextRegister = atomPeak;
                quantifier.emit(context, atomStart, captureCountBeforeAtom, context.captures.count());
            } else if (ch == '{' && context.isUnicodeMode() && context.lastAtomCanRepeat) {
                // After an atom an unparsable '{' is a broken quantifier, as V8 reports it, not a lone bracket.
                throw new RegExpSyntaxException("Incomplete quantifier");
            }
        }
        // The term is complete and its counters are dead: the next term starts from the same registers.
        context.nextRegister = registerBase;
    }

    private boolean isInlineModifierFlag(int ch) {
        return ch == 'i' || ch == 'm' || ch == 's';
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
        if (!context.hasMore()) {
            return null;
        }
        int startChar = context.peek();
        if (!(isInlineModifierFlag(startChar) || startChar == '-')) {
            return null;
        }

        int addFlags = 0;
        int removeFlags = 0;
        boolean parsingRemoveFlags = false;
        boolean sawDash = false;
        boolean sawModifierFlag = false;

        while (context.hasMore()) {
            int currentChar = context.peek();
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

    static void compileLiteralChar(CompileContext context, int ch) {
        if (ch <= 0xFFFF) {
            context.emitU16(context.isIgnoreCase() ? RegExpOpcode.CHAR_I : RegExpOpcode.CHAR, ch);
        } else {
            context.emitU32(context.isIgnoreCase() ? RegExpOpcode.CHAR32_I : RegExpOpcode.CHAR32, ch);
        }
    }

    static void compileLiteralChar(CompileContext context, int ch, boolean isBackwardDirection) {
        if (isBackwardDirection) {
            context.emit(RegExpOpcode.PREV);
        }
        compileLiteralChar(context, ch);
        if (isBackwardDirection) {
            context.emit(RegExpOpcode.PREV);
        }
    }

    private static void emitBackReference(CompileContext context, boolean backward, int group) {
        RegExpOpcode opcode;
        if (backward) {
            opcode = context.isIgnoreCase()
                    ? RegExpOpcode.BACKWARD_BACK_REFERENCE_I
                    : RegExpOpcode.BACKWARD_BACK_REFERENCE;
        } else {
            opcode = context.isIgnoreCase() ? RegExpOpcode.BACK_REFERENCE_I : RegExpOpcode.BACK_REFERENCE;
        }
        context.emitU16(opcode, group);
    }

    static final class CompileContext {
        final DynamicBuffer buffer;
        final RegExpCaptures captures;
        final int[] codePoints;
        int currentFlags;
        final int flags;
        boolean lastAtomCanRepeat;
        boolean lastUnicodeSetsOperandWasRange;
        /** The next free register; released back to a term's base once the term is compiled. */
        int nextRegister;
        int pos;
        final UnicodePropertyResolver properties;
        /** Registers the whole pattern needs, the high-water mark of every allocation. */
        int registerCount;
        /** The highest register reached while compiling the current term's atom. */
        int registerPeak;

        CompileContext(String pattern, int flags, DynamicBuffer buffer, UnicodePropertyResolver properties) {
            this.properties = properties;
            // In non-unicode mode, work with UTF-16 code units so that
            // raw surrogates in the pattern are individual atoms.
            // In unicode mode, combine surrogate pairs into code points.
            boolean isUnicodeMode = (flags & (RegExpBytecode.FLAG_UNICODE | RegExpBytecode.FLAG_UNICODE_SETS)) != 0;
            this.codePoints = isUnicodeMode ? pattern.codePoints().toArray() : pattern.chars().toArray();
            this.captures = RegExpCaptures.scan(codePoints, pattern.indexOf("(?<") >= 0);
            this.flags = captures.hasNames() ? flags | RegExpBytecode.FLAG_NAMED_GROUPS : flags;
            this.currentFlags = this.flags;
            this.buffer = buffer;
            this.lastAtomCanRepeat = false;
            this.nextRegister = 0;
            this.registerCount = 0;
            this.registerPeak = 0;
            this.pos = 0;
        }

        boolean at(int codePoint) {
            return hasMore() && peek() == codePoint;
        }

        boolean consume(int codePoint) {
            if (!at(codePoint)) {
                return false;
            }
            pos++;
            return true;
        }

        void emit(RegExpOpcode opcode) {
            buffer.appendU8(opcode.getCode());
        }

        int emitForwardJump(RegExpOpcode opcode) {
            emit(opcode);
            int operand = buffer.size();
            buffer.appendU32(0);
            return operand;
        }

        void emitU16(RegExpOpcode opcode, int operand) {
            emit(opcode);
            buffer.appendU16(operand);
        }

        void emitU32(RegExpOpcode opcode, int operand) {
            emit(opcode);
            buffer.appendU32(operand);
        }

        void emitU8(RegExpOpcode opcode, int operand) {
            emit(opcode);
            buffer.appendU8(operand);
        }

        boolean hasMore() {
            return pos < codePoints.length;
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

        void patchJumpToHere(int operand) {
            buffer.setU32(operand, buffer.size() - operand - Integer.BYTES);
        }

        int peek() {
            return codePoints[pos];
        }

        int read() {
            return codePoints[pos++];
        }
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
