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

import static com.caoccao.qjs4j.regexp.RegExpCompiler.*;

/** Parses repetition bounds and emits the corresponding loop bytecode. */
record RegExpQuantifier(int min, int max) {
    private static final RegExpQuantifier STAR = new RegExpQuantifier(0, Integer.MAX_VALUE);
    private static final RegExpQuantifier PLUS = new RegExpQuantifier(1, Integer.MAX_VALUE);
    private static final RegExpQuantifier OPTIONAL = new RegExpQuantifier(0, 1);
    private static final int MAX_UNROLLED_QUANTIFIER_REPETITIONS = 4096;

    /** Consume a complete quantifier. Leave an incomplete brace expression at its opening brace. */
    static RegExpQuantifier read(CompileContext context) {
        int start = context.pos;
        switch (context.codePoints[start]) {
            case '*' :
                context.pos++;
                return STAR;
            case '+' :
                context.pos++;
                return PLUS;
            case '?' :
                context.pos++;
                return OPTIONAL;
            case '{' :
                break;
            default :
                return null;
        }
        context.pos++;
        int min = readDecimal(context);
        if (min >= 0) {
            int max = min;
            if (context.pos < context.codePoints.length && context.codePoints[context.pos] == ',') {
                context.pos++;
                max = readDecimal(context);
                if (max < 0) {
                    max = Integer.MAX_VALUE;
                }
            }
            if (context.pos < context.codePoints.length && context.codePoints[context.pos] == '}') {
                context.pos++;
                return new RegExpQuantifier(min, max);
            }
        }
        context.pos = start;
        return null;
    }

    private static int readDecimal(CompileContext context) {
        int start = context.pos;
        long value = 0;
        while (context.pos < context.codePoints.length) {
            int digit = context.codePoints[context.pos] - '0';
            if (digit < 0 || digit > 9) {
                break;
            }
            value = Math.min(Integer.MAX_VALUE, value * 10 + digit);
            context.pos++;
        }
        return context.pos == start ? -1 : (int) value;
    }

    private static AtomBehavior analyzeAtom(byte[] code, boolean hasCaptures) {
        boolean advances = false;
        for (int pos = 0; pos < code.length;) {
            RegExpOpcode opcode = RegExpOpcode.fromCode(code[pos] & 0xFF);
            int length = opcode.getLength();
            switch (opcode) {
                case RANGE, RANGE_I, RANGE32, RANGE32_I, NOT_RANGE, NOT_RANGE_I -> {
                    advances = true;
                    // RANGE stores its payload length as a little-endian u32 immediately after the opcode.
                    length = 5 + RegExpBytecode.readU32(code, pos + 1);
                }
                case CHAR, CHAR_I, CHAR32, CHAR32_I, DOT, ANY, SPACE, NOT_SPACE, PREV -> advances = true;
                case LINE_START, LINE_START_M, LINE_END, LINE_END_M, WORD_BOUNDARY, WORD_BOUNDARY_I, NOT_WORD_BOUNDARY,
                        NOT_WORD_BOUNDARY_I, SAVE_START, SAVE_END, SAVE_RESET, SET_CHAR_POS, SET_I32 -> {
                    // Neither consumes input nor branches.
                }
                default -> {
                    // A branch or backreference can bypass later instructions.
                    return advances ? AtomBehavior.CONSUMES_WITH_BRANCH : AtomBehavior.UNKNOWN;
                }
            }
            if (advances && !hasCaptures) {
                return AtomBehavior.CONSUMES;
            }
            pos += length;
        }
        return advances ? AtomBehavior.CONSUMES : AtomBehavior.EMPTY;
    }

    private enum AtomBehavior {
        CONSUMES(false, false),
        CONSUMES_WITH_BRANCH(false, true),
        EMPTY(true, false),
        UNKNOWN(true, true);
        final boolean checkAdvance;
        final boolean resetCaptures;
        AtomBehavior(boolean checkAdvance, boolean resetCaptures) {
            this.checkAdvance = checkAdvance;
            this.resetCaptures = resetCaptures;
        }
    }

    void emit(CompileContext context, int atomStart, int captureCountBeforeAtom, int captureCount) {
        if (max < min) {
            throw new RegExpSyntaxException("numbers out of order in {} quantifier");
        }
        boolean greedy = true;
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

        boolean hasCapturesInAtom = captureCountBeforeAtom != captureCount;
        AtomBehavior behavior = analyzeAtom(atomCode, hasCapturesInAtom);
        boolean addZeroAdvanceCheck = behavior.checkAdvance;
        boolean needCaptureReset = hasCapturesInAtom && behavior.resetCaptures;
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
            context.emitU16(RegExpOpcode.SAVE_RESET, captureCountBeforeAtom);
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
                context.emitU8(RegExpOpcode.SET_CHAR_POS, advReg);
            }
            context.emitU32(greedy ? RegExpOpcode.SPLIT_NEXT_FIRST : RegExpOpcode.SPLIT_GOTO_FIRST,
                    atomSize + (addZeroAdvanceCheck ? 2 : 0));
            context.buffer.append(atomCode);
            if (addZeroAdvanceCheck) {
                context.emitU8(RegExpOpcode.CHECK_ADVANCE, advReg);
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
                context.emit(greedy ? RegExpOpcode.SPLIT_GOTO_FIRST : RegExpOpcode.SPLIT_NEXT_FIRST);
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
                    context.emitU32(greedy ? RegExpOpcode.SPLIT_NEXT_FIRST : RegExpOpcode.SPLIT_GOTO_FIRST,
                            atomSize + 6 + (addZeroAdvanceCheck ? 4 : 0));
                    emitRepeatedAtom(context, atomCode, addZeroAdvanceCheck, advReg);
                    emitLoop(context, register, loopStart);
                }
            }
        }
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

    private void emitCounter(CompileContext context, int register, int count) {
        context.emitU8(RegExpOpcode.SET_I32, register);
        context.buffer.appendU32(count);
    }

    private void emitLoop(CompileContext context, int register, int target) {
        context.emitU8(RegExpOpcode.LOOP, register);
        context.buffer.appendU32(target - (context.buffer.size() + 4));
    }

    private static void emitRepeatedAtom(CompileContext context, byte[] atomCode, boolean addZeroAdvanceCheck,
            int advReg) {
        if (addZeroAdvanceCheck) {
            context.emitU8(RegExpOpcode.SET_CHAR_POS, advReg);
        }
        context.buffer.append(atomCode);
        if (addZeroAdvanceCheck) {
            context.emitU8(RegExpOpcode.CHECK_ADVANCE, advReg);
        }
    }

    private static void emitUnboundedRepeat(CompileContext context, byte[] atomCode, boolean greedy,
            boolean addZeroAdvanceCheck, int advReg) {
        int advCheckSize = addZeroAdvanceCheck ? 4 : 0; // SET_CHAR_POS(2) + CHECK_ADVANCE(2)
        int loopStart = context.buffer.size();
        context.emitU32(greedy ? RegExpOpcode.SPLIT_NEXT_FIRST : RegExpOpcode.SPLIT_GOTO_FIRST,
                atomCode.length + 5 + advCheckSize); // Skip atom + GOTO + advance checks
        emitRepeatedAtom(context, atomCode, addZeroAdvanceCheck, advReg);
        context.emit(RegExpOpcode.GOTO);
        int offset = loopStart - (context.buffer.size() + 4);
        context.buffer.appendU32(offset);
    }

}
