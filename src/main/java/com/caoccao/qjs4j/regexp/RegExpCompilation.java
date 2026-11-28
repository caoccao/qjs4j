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
import com.caoccao.qjs4j.regexp.RegExpCompiler.RegExpSyntaxException;

/** A single parse of a RegExp pattern, including its bytecode buffer, captures and register allocation. */
final class RegExpCompilation {
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

    RegExpCompilation(String pattern, int flags, UnicodePropertyResolver properties) {
        this.properties = properties;
        // In non-unicode mode, work with UTF-16 code units so that
        // raw surrogates in the pattern are individual atoms.
        // In unicode mode, combine surrogate pairs into code points.
        boolean isUnicodeMode = (flags & (RegExpBytecode.FLAG_UNICODE | RegExpBytecode.FLAG_UNICODE_SETS)) != 0;
        this.codePoints = isUnicodeMode ? pattern.codePoints().toArray() : pattern.chars().toArray();
        this.captures = RegExpCaptures.scan(codePoints, pattern.indexOf("(?<") >= 0);
        this.flags = captures.hasNames() ? flags | RegExpBytecode.FLAG_NAMED_GROUPS : flags;
        this.currentFlags = this.flags;
        this.buffer = new DynamicBuffer(256);
        this.lastAtomCanRepeat = false;
        this.nextRegister = 0;
        this.registerCount = 0;
        this.registerPeak = 0;
        this.pos = 0;
    }

    boolean at(int codePoint) {
        return hasMore() && peek() == codePoint;
    }

    RegExpBytecode compile() {
        try {
            compilePattern();
            if (pos != codePoints.length) {
                throw new RegExpSyntaxException(peek() == ')' ? "Unmatched ')'" : "Unexpected character in pattern");
            }
            // End with MATCH opcode
            emit(RegExpOpcode.MATCH);

            return new RegExpBytecode(buffer.toByteArray(), flags, captures.count(),
                    // Registers this pattern actually allocated. The matcher saves them on every
                    // backtrack point where state changed, so reporting the real count instead of
                    // the maximum is the difference between 12 and 76 bytes of saved state per
                    // point for a typical pattern.
                    registerCount, captures.names());
        } catch (RegExpSyntaxException e) {
            // The reason as it was found, as V8 reports it.
            throw e;
        } catch (Exception e) {
            throw new RegExpSyntaxException("Failed to compile pattern: " + e.getMessage(), e);
        }
    }

    private void compileAlternative(boolean isBackwardDirection) {
        // An alternative is a sequence of terms (atoms with optional quantifiers)
        int alternativeStart = buffer.size();
        while (hasMore()) {
            int ch = peek();

            // Check for end of alternative
            if (ch == '|' || ch == ')') {
                return;
            }

            // Parse a term (atom + optional quantifier)
            int termStart = buffer.size();
            compileTerm(isBackwardDirection);
            if (isBackwardDirection) {
                reverseAlternativeTerm(alternativeStart, termStart);
            }
        }
    }

    private void compileAtom(boolean isBackwardDirection) {
        if (!hasMore()) {
            return;
        }
        lastAtomCanRepeat = true;

        int ch = peek();

        switch (ch) {
            case '^' -> {
                lastAtomCanRepeat = false;
                emit(isMultiline() ? RegExpOpcode.LINE_START_M : RegExpOpcode.LINE_START);
                pos++;
            }

            case '$' -> {
                lastAtomCanRepeat = false;
                emit(isMultiline() ? RegExpOpcode.LINE_END_M : RegExpOpcode.LINE_END);
                pos++;
            }

            case '.' -> {
                emitPreviousIfBackward(isBackwardDirection);
                emit(isDotAll() ? RegExpOpcode.ANY : RegExpOpcode.DOT);
                emitPreviousIfBackward(isBackwardDirection);
                pos++;
            }

            case '\\' -> {
                pos++;
                compileEscape(isBackwardDirection);
            }

            case '[' -> {
                RegExpCharacterClass.compileCharacterClass(this, isBackwardDirection);
            }

            case '(' -> {
                compileGroup(isBackwardDirection);
            }

            case '*', '+', '?' -> {
                throw new RegExpSyntaxException("Nothing to repeat");
            }

            case '{', '}', ']' -> {
                if (ch == '{' && RegExpQuantifier.read(this) != null) {
                    throw new RegExpSyntaxException("Nothing to repeat");
                }
                if (isUnicodeMode()) {
                    throw new RegExpSyntaxException("Lone quantifier brackets");
                }
                compileLiteralChar(ch, isBackwardDirection);
                pos++;
            }
            case ')', '|' -> lastAtomCanRepeat = false;
            default -> {
                // Literal character
                compileLiteralChar(ch, isBackwardDirection);
                pos++;
            }
        }
    }

    private void compileDisjunction(boolean isBackwardDirection) {
        // A disjunction is a sequence of alternatives separated by |
        int start = buffer.size();

        // Compile first alternative
        compileAlternative(isBackwardDirection);

        // Handle additional alternatives
        while (consume('|')) {

            int len = buffer.size() - start;

            // Insert a SPLIT at the start to choose between alternatives
            buffer.insert(start, 5);
            buffer.setU8(start, RegExpOpcode.SPLIT_NEXT_FIRST.getCode());
            buffer.setU32(start + 1, len + 5);

            // Emit GOTO to skip the next alternative
            int target = emitForwardJump(RegExpOpcode.GOTO);

            // Compile next alternative
            compileAlternative(isBackwardDirection);

            patchJumpToHere(target);
        }
    }

    private void compileEscape(boolean isBackwardDirection) {
        if (!hasMore()) {
            throw new RegExpSyntaxException("Incomplete escape sequence");
        }

        int ch = read();
        switch (ch) {
            case 'n', 'r', 't', 'f', 'v', '0', 'x', 'u' ->
                compileLiteralChar(readCharacterEscape(this, ch), isBackwardDirection);
            case 'd', 'D', 'w', 'W', 's', 'S' -> {
                emitPreviousIfBackward(isBackwardDirection);
                if (ch == 's' || ch == 'S') {
                    emit(ch == 's' ? RegExpOpcode.SPACE : RegExpOpcode.NOT_SPACE);
                } else {
                    boolean inverted = ch == 'D' || ch == 'W';
                    emit(inverted ? RegExpOpcode.NOT_RANGE : RegExpOpcode.RANGE);
                    RegExpCharacterClass.emitRangePayload(this,
                            RegExpCharacterClass.characterClassRanges(this, Character.toLowerCase(ch)));
                }
                emitPreviousIfBackward(isBackwardDirection);
            }
            case 'b' -> {
                // \b - match word boundary
                emit(isIgnoreCase() && isUnicodeMode() ? RegExpOpcode.WORD_BOUNDARY_I : RegExpOpcode.WORD_BOUNDARY);
            }
            case 'B' -> {
                // \B - match non-word boundary
                emit(isIgnoreCase() && isUnicodeMode()
                        ? RegExpOpcode.NOT_WORD_BOUNDARY_I
                        : RegExpOpcode.NOT_WORD_BOUNDARY);
            }
            case 'k' -> {
                // AnnexB: In non-unicode mode with no named capture groups,
                // \k is an identity escape matching literal 'k'
                if (!isUnicodeMode() && !captures.hasNames()) {
                    compileLiteralChar('k', isBackwardDirection);
                    break;
                }
                if (!consume('<')) {
                    throw new RegExpSyntaxException("expecting group name");
                }

                String name = RegExpCaptures.readName(this);
                Integer groupNum = captures.indexOf(name);
                if (groupNum == null) {
                    throw new RegExpSyntaxException("Invalid named capture referenced");
                }

                emitBackReference(isBackwardDirection, groupNum);
            }
            case 'p', 'P' -> {
                if (!isUnicodeMode()) {
                    compileLiteralChar(ch, isBackwardDirection);
                    break;
                }
                RegExpCharacterClass.compileUnicodePropertyEscape(this, ch == 'P', isBackwardDirection);
            }
            case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                // Back reference \1, \2, etc.
                // Use totalCaptureCount (pre-scanned) to support forward references
                int groupNum = ch - '0';
                int savedPos = pos;
                // Check for multi-digit group numbers
                while (hasMore()) {
                    int nextCh = peek();
                    if (nextCh >= '0' && nextCh <= '9') {
                        groupNum = groupNum * 10 + (nextCh - '0');
                        pos++;
                    } else {
                        break;
                    }
                }

                if (groupNum < captures.totalCount()) {
                    // Valid backreference (including forward references)
                    emitBackReference(isBackwardDirection, groupNum);
                } else if (!isUnicodeMode()) {
                    // AnnexB: In non-unicode mode, invalid backreferences are
                    // treated as octal escapes (\1-\7) or identity escapes (\8-\9)
                    pos = savedPos; // Rewind consumed digits
                    int digit = ch - '0';
                    if (digit <= 7) {
                        // Octal escape: \1-\7 → U+0001-U+0007
                        // Also handle multi-digit octal: \12, \377, etc.
                        int octalValue = parseLegacyOctalEscape(this, digit);
                        compileLiteralChar(octalValue, isBackwardDirection);
                    } else {
                        // \8, \9 → identity escape (literal digit)
                        compileLiteralChar(ch, isBackwardDirection);
                    }
                } else {
                    // Unicode mode: invalid backreference is a syntax error
                    throw new RegExpSyntaxException("Invalid backreference \\" + groupNum);
                }
            }
            case 'c' -> {
                int code = readControlLetter(this, false);
                if (code < 0) {
                    pos--; // Annex B reinterprets the c as the next atom.
                    code = '\\';
                }
                compileLiteralChar(code, isBackwardDirection);
            }
            default -> {
                // Identity escape
                if (isUnicodeMode()) {
                    // In unicode mode, only syntax characters are valid identity escapes
                    // QuickJS: ^$\.*+?()[]{}|/
                    if (!isSyntaxCharacter(ch)) {
                        throw new RegExpSyntaxException("invalid escape sequence in regular expression");
                    }
                }
                // Non-unicode mode: any character is a valid identity escape (Annex B)
                compileLiteralChar(ch, isBackwardDirection);
            }
        }
    }

    private void compileGroup(boolean isBackwardDirection) {
        // Capture groups (...) or non-capturing groups (?:...)
        pos++; // Skip '('

        // Check for special group syntax
        boolean isCapturing = true;
        String captureName = null;
        if (consume('?')) {
            if (!hasMore()) {
                throw new RegExpSyntaxException("Invalid group");
            }
            InlineModifierGroupParseResult inlineModifierGroupParseResult = tryParseInlineModifierGroup();
            if (inlineModifierGroupParseResult != null) {
                int savedFlags = currentFlags;
                currentFlags = inlineModifierGroupParseResult.scopedFlags();
                try {
                    compileDisjunction(isBackwardDirection);
                } finally {
                    currentFlags = savedFlags;
                }
                if (!consume(')')) {
                    throw new RegExpSyntaxException("Unterminated group");
                }
                lastAtomCanRepeat = true;
                return;
            }
            int groupType = read();
            if (groupType == ':') {
                // Non-capturing group (?:...)
                isCapturing = false;
            } else if (groupType == '=') {
                // Positive lookahead (?=...)
                compileLookaround(false, false);
                lastAtomCanRepeat = !isUnicodeMode();
                return;
            } else if (groupType == '!') {
                // Negative lookahead (?!...)
                compileLookaround(true, false);
                lastAtomCanRepeat = !isUnicodeMode();
                return;
            } else if (groupType == '<') {
                if (hasMore() && (peek() == '=' || peek() == '!')) {
                    boolean isNegative = peek() == '!';
                    pos++;
                    compileLookaround(isNegative, true);
                    lastAtomCanRepeat = false;
                    return;
                }

                captureName = RegExpCaptures.readName(this);
            } else {
                throw new RegExpSyntaxException("Invalid group");
            }
        }

        int groupIndex = -1;
        if (isCapturing) {
            groupIndex = captures.add(captureName);
            // Save start
            emitU16(isBackwardDirection ? RegExpOpcode.SAVE_END : RegExpOpcode.SAVE_START, groupIndex);
        }

        // Compile group contents
        compileDisjunction(isBackwardDirection);

        if (isCapturing) {
            // Save end
            emitU16(isBackwardDirection ? RegExpOpcode.SAVE_START : RegExpOpcode.SAVE_END, groupIndex);
        }

        if (!consume(')')) {
            throw new RegExpSyntaxException("Unterminated group");
        }
        lastAtomCanRepeat = true;
    }

    void compileLiteralChar(int ch) {
        if (ch <= 0xFFFF) {
            emitU16(isIgnoreCase() ? RegExpOpcode.CHAR_I : RegExpOpcode.CHAR, ch);
        } else {
            emitU32(isIgnoreCase() ? RegExpOpcode.CHAR32_I : RegExpOpcode.CHAR32, ch);
        }
    }

    void compileLiteralChar(int ch, boolean isBackwardDirection) {
        emitPreviousIfBackward(isBackwardDirection);
        compileLiteralChar(ch);
        emitPreviousIfBackward(isBackwardDirection);
    }

    private void compileLookaround(boolean isNegative, boolean backward) {
        RegExpOpcode start;
        RegExpOpcode match;
        if (backward) {
            start = isNegative ? RegExpOpcode.NEGATIVE_LOOKBEHIND : RegExpOpcode.LOOKBEHIND;
            match = isNegative ? RegExpOpcode.NEGATIVE_LOOKBEHIND_MATCH : RegExpOpcode.LOOKBEHIND_MATCH;
        } else {
            start = isNegative ? RegExpOpcode.NEGATIVE_LOOKAHEAD : RegExpOpcode.LOOKAHEAD;
            match = isNegative ? RegExpOpcode.NEGATIVE_LOOKAHEAD_MATCH : RegExpOpcode.LOOKAHEAD_MATCH;
        }
        int target = emitForwardJump(start);
        compileDisjunction(backward);
        emit(match);
        patchJumpToHere(target);
        if (!consume(')')) {
            throw new RegExpSyntaxException("Unterminated group");
        }
    }

    private void compilePattern() {
        // Save start of capture group 0
        emitU16(RegExpOpcode.SAVE_START, 0); // Capture group 0

        compileDisjunction(false);

        // Save end of capture group 0
        emitU16(RegExpOpcode.SAVE_END, 0); // Capture group 0
    }

    private void compileTerm(boolean isBackwardDirection) {
        int atomStart = buffer.size();
        int captureCountBeforeAtom = captures.count();
        int registerBase = nextRegister;
        int enclosingPeak = registerPeak;
        registerPeak = registerBase;

        // Parse the atom
        compileAtom(isBackwardDirection);

        // The atom's own quantifiers have released their registers, but a quantifier wrapped around the atom runs
        // them on every iteration, so its counters go above the highest register the atom reached.
        int atomPeak = registerPeak;
        registerPeak = Math.max(enclosingPeak, atomPeak);

        // Check for quantifier
        if (hasMore()) {
            int ch = peek();

            RegExpQuantifier quantifier = RegExpQuantifier.read(this);
            if (quantifier != null) {
                if (!lastAtomCanRepeat) {
                    throw new RegExpSyntaxException("Nothing to repeat");
                }
                nextRegister = atomPeak;
                quantifier.emit(this, atomStart, captureCountBeforeAtom, captures.count());
            } else if (ch == '{' && isUnicodeMode() && lastAtomCanRepeat) {
                // After an atom an unparsable '{' is a broken quantifier, as V8 reports it, not a lone bracket.
                throw new RegExpSyntaxException("Incomplete quantifier");
            }
        }
        // The term is complete and its counters are dead: the next term starts from the same registers.
        nextRegister = registerBase;
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

    private void emitBackReference(boolean backward, int group) {
        RegExpOpcode opcode;
        if (backward) {
            opcode = isIgnoreCase() ? RegExpOpcode.BACKWARD_BACK_REFERENCE_I : RegExpOpcode.BACKWARD_BACK_REFERENCE;
        } else {
            opcode = isIgnoreCase() ? RegExpOpcode.BACK_REFERENCE_I : RegExpOpcode.BACK_REFERENCE;
        }
        emitU16(opcode, group);
    }

    int emitForwardJump(RegExpOpcode opcode) {
        emit(opcode);
        int operand = buffer.size();
        buffer.appendU32(0);
        return operand;
    }

    void emitPreviousIfBackward(boolean backward) {
        if (backward) {
            emit(RegExpOpcode.PREV);
        }
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

    private boolean isInlineModifierFlag(int ch) {
        return ch == 'i' || ch == 'm' || ch == 's';
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

    private void reverseAlternativeTerm(int alternativeStart, int termStart) {
        if (termStart <= alternativeStart || termStart >= buffer.size()) {
            return;
        }
        int end = buffer.size();
        byte[] previousTerms = buffer.getRange(alternativeStart, termStart - alternativeStart);
        byte[] currentTerm = buffer.getRange(termStart, end - termStart);
        buffer.truncate(alternativeStart);
        buffer.append(currentTerm);
        buffer.append(previousTerms);
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

    private InlineModifierGroupParseResult tryParseInlineModifierGroup() {
        if (!hasMore()) {
            return null;
        }
        int startChar = peek();
        if (!(isInlineModifierFlag(startChar) || startChar == '-')) {
            return null;
        }

        int addFlags = 0;
        int removeFlags = 0;
        boolean parsingRemoveFlags = false;
        boolean sawDash = false;
        boolean sawModifierFlag = false;

        while (hasMore()) {
            int currentChar = peek();
            if (currentChar == ':') {
                if (!sawModifierFlag) {
                    throw new RegExpSyntaxException("Invalid flag group");
                }
                pos++;
                int scopedFlags = (currentFlags | addFlags) & ~removeFlags;
                return new InlineModifierGroupParseResult(scopedFlags);
            }
            if (currentChar == '-') {
                if (sawDash) {
                    throw new RegExpSyntaxException("Multiple dashes in flag group");
                }
                sawDash = true;
                parsingRemoveFlags = true;
                pos++;
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
            pos++;
        }

        throw new RegExpSyntaxException("Invalid group");
    }

    private record InlineModifierGroupParseResult(int scopedFlags) {
    }

}
