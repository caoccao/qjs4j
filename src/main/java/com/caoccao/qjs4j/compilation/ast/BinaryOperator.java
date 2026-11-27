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

package com.caoccao.qjs4j.compilation.ast;

import com.caoccao.qjs4j.vm.Opcode;

/**
 * A binary operator.
 * <p>
 * An eager operator evaluates both operands and then applies its {@link #getOpcode() opcode}. The short-circuit
 * operators {@code &&}, {@code ||} and {@code ??} are not eager: whether the right operand is evaluated at all depends
 * on the value of the left one, so they compile to a conditional jump and have no opcode of their own.
 */
public enum BinaryOperator {
    ADD(true, Opcode.ADD),
    BIT_AND(true, Opcode.AND),
    BIT_OR(true, Opcode.OR),
    BIT_XOR(true, Opcode.XOR),
    DIV(true, Opcode.DIV),
    EQ(true, Opcode.EQ),
    EXP(true, Opcode.POW),
    GE(true, Opcode.GTE),
    GT(true, Opcode.GT),
    IN(true, Opcode.IN),
    INSTANCEOF(true, Opcode.INSTANCEOF),
    LE(true, Opcode.LTE),
    LOGICAL_AND(false, null),
    LOGICAL_OR(false, null),
    LSHIFT(true, Opcode.SHL),
    LT(true, Opcode.LT),
    MOD(true, Opcode.MOD),
    MUL(true, Opcode.MUL),
    NE(true, Opcode.NEQ),
    NULLISH_COALESCING(false, null),
    RSHIFT(true, Opcode.SAR),
    STRICT_EQ(true, Opcode.STRICT_EQ),
    STRICT_NE(true, Opcode.STRICT_NEQ),
    SUB(true, Opcode.SUB),
    URSHIFT(true, Opcode.SHR);

    private final boolean eagerOperator;
    private final Opcode opcode;

    BinaryOperator(boolean eagerOperator, Opcode opcode) {
        this.eagerOperator = eagerOperator;
        this.opcode = opcode;
    }

    /**
     * The opcode an eager operator applies to its two operands.
     *
     * @return the opcode, or {@code null} for a short-circuit operator
     */
    public Opcode getOpcode() {
        return opcode;
    }

    public boolean isEagerOperator() {
        return eagerOperator;
    }
}
