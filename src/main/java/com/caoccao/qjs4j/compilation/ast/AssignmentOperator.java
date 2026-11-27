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
 * An assignment operator.
 * <p>
 * A compound operator such as {@code +=} reads the target, applies its {@link #getOpcode() opcode} to the old value and
 * the right operand, and stores the result. Plain {@code =} and the logical assignments {@code &&=}, {@code ||=} and
 * {@code ??=} have no opcode: the first stores the right operand as it is, the others compile to a conditional jump.
 */
public enum AssignmentOperator {
    AND_ASSIGN(Opcode.AND),
    ASSIGN(null),
    DIV_ASSIGN(Opcode.DIV),
    EXP_ASSIGN(Opcode.POW),
    LOGICAL_AND_ASSIGN(null),
    LOGICAL_OR_ASSIGN(null),
    LSHIFT_ASSIGN(Opcode.SHL),
    MINUS_ASSIGN(Opcode.SUB),
    MOD_ASSIGN(Opcode.MOD),
    MUL_ASSIGN(Opcode.MUL),
    NULLISH_ASSIGN(null),
    OR_ASSIGN(Opcode.OR),
    PLUS_ASSIGN(Opcode.ADD),
    RSHIFT_ASSIGN(Opcode.SAR),
    URSHIFT_ASSIGN(Opcode.SHR),
    XOR_ASSIGN(Opcode.XOR);

    private final Opcode opcode;

    AssignmentOperator(Opcode opcode) {
        this.opcode = opcode;
    }

    /**
     * The opcode a compound operator applies to the old value and the right operand.
     *
     * @return the opcode, or {@code null} for {@code =} and the logical assignments
     */
    public Opcode getOpcode() {
        return opcode;
    }
}
