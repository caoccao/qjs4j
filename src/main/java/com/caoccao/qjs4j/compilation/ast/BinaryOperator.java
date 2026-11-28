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

import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.vm.Opcode;

/**
 * A binary operator.
 * <p>
 * An eager operator evaluates both operands and then applies its {@link #getOpcode() opcode}. The short-circuit
 * operators {@code &&}, {@code ||} and {@code ??} are not eager: whether the right operand is evaluated at all depends
 * on the value of the left one, so they compile to a conditional jump and have no opcode of their own.
 */
public enum BinaryOperator {
    ADD(TokenType.PLUS, Precedence.ADDITIVE, Opcode.ADD),
    BIT_AND(TokenType.BIT_AND, Precedence.BIT_AND, Opcode.AND),
    BIT_OR(TokenType.BIT_OR, Precedence.BIT_OR, Opcode.OR),
    BIT_XOR(TokenType.BIT_XOR, Precedence.BIT_XOR, Opcode.XOR),
    DIV(TokenType.DIV, Precedence.MULTIPLICATIVE, Opcode.DIV),
    EQ(TokenType.EQ, Precedence.EQUALITY, Opcode.EQ),
    EXP(null, null, Opcode.POW),
    GE(TokenType.GE, Precedence.RELATIONAL, Opcode.GTE),
    GT(TokenType.GT, Precedence.RELATIONAL, Opcode.GT),
    IN(TokenType.IN, Precedence.RELATIONAL, Opcode.IN),
    INSTANCEOF(TokenType.INSTANCEOF, Precedence.RELATIONAL, Opcode.INSTANCEOF),
    LE(TokenType.LE, Precedence.RELATIONAL, Opcode.LTE),
    LOGICAL_AND(null, null, null),
    LOGICAL_OR(null, null, null),
    LSHIFT(TokenType.LSHIFT, Precedence.SHIFT, Opcode.SHL),
    LT(TokenType.LT, Precedence.RELATIONAL, Opcode.LT),
    MOD(TokenType.MOD, Precedence.MULTIPLICATIVE, Opcode.MOD),
    MUL(TokenType.MUL, Precedence.MULTIPLICATIVE, Opcode.MUL),
    NE(TokenType.NE, Precedence.EQUALITY, Opcode.NEQ),
    NULLISH_COALESCING(null, null, null),
    RSHIFT(TokenType.RSHIFT, Precedence.SHIFT, Opcode.SAR),
    STRICT_EQ(TokenType.STRICT_EQ, Precedence.EQUALITY, Opcode.STRICT_EQ),
    STRICT_NE(TokenType.STRICT_NE, Precedence.EQUALITY, Opcode.STRICT_NEQ),
    SUB(TokenType.MINUS, Precedence.ADDITIVE, Opcode.SUB),
    URSHIFT(TokenType.URSHIFT, Precedence.SHIFT, Opcode.SHR);

    private static final BinaryOperator[] INFIX_BY_TOKEN = new BinaryOperator[TokenType.values().length];

    static {
        for (BinaryOperator operator : values()) {
            if (operator.token != null) {
                INFIX_BY_TOKEN[operator.token.ordinal()] = operator;
            }
        }
    }

    private final Opcode opcode;
    private final Precedence precedence;

    private final TokenType token;

    BinaryOperator(TokenType token, Precedence precedence, Opcode opcode) {
        this.token = token;
        this.precedence = precedence;
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
        return opcode != null;
    }

    public Precedence precedence() {
        return precedence;
    }

    public static BinaryOperator infixOperator(TokenType token) {
        return INFIX_BY_TOKEN[token.ordinal()];
    }
    public enum Precedence {
        ADDITIVE(7),
        BIT_AND(3),
        BIT_OR(1),
        BIT_XOR(2),
        EQUALITY(4),
        MULTIPLICATIVE(8),
        RELATIONAL(5),
        SHIFT(6);
        public final int level;
        Precedence(int level) {
            this.level = level;
        }
    }
}
