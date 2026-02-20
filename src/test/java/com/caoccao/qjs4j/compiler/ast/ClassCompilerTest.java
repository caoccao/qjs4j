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

package com.caoccao.qjs4j.compiler.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class declaration compilation and execution.
 */
public class ClassCompilerTest extends BaseJavetTest {

    @Test
    public void testClassComputedInstanceFieldDefaultInitializer() {
        assertBooleanWithJavet("""
                let i = 0;
                class C {
                    [++i];
                }
                const c = new C();
                i === 1 && c[1] === undefined""");
    }

    @Test
    public void testClassComputedInstanceFieldKeyEvaluatedOnceAcrossInstances() {
        assertBooleanWithJavet("""
                let i = 0;
                class C {
                    [++i] = 7;
                }
                const c1 = new C();
                const c2 = new C();
                i === 1 && c1[1] === 7 && c2[1] === 7""");
    }

    @Test
    public void testClassComputedInstanceFieldToPropertyKeyEvaluatedOnce() {
        assertBooleanWithJavet("""
                let count = 0;
                const keyObj = {
                    toString() {
                        count++;
                        return 'k';
                    }
                };
                class C {
                    [keyObj] = 1;
                }
                new C();
                new C();
                count === 1""");
    }

    @Test
    public void testClassComputedStaticFieldAndStaticBlockOrdering() {
        assertBooleanWithJavet("""
                const log = [];
                class C {
                    static {
                        log.push('block1');
                    }
                    static [(log.push('key'), 'x')] = 1;
                    static {
                        log.push('block2');
                    }
                }
                C.x === 1 && log.join(',') === 'key,block1,block2'""");
    }

    @Test
    public void testClassComputedStaticFieldUsesClassAsThis() {
        assertIntegerWithJavet("""
                class C {
                    static base = 41;
                    static ['result'] = this.base + 1;
                }
                C.result""");
    }

    @Test
    public void testClassDeclarationStaticMethodCanAccessPrivateField() {
        assertIntegerWithJavet("""
                class Counter {
                    #count = 7;
                    static read(instance) {
                        return instance.#count;
                    }
                }
                Counter.read(new Counter())""");
    }

    @Test
    public void testClassExpressionAnonymousStaticMethodInvocation() {
        assertIntegerWithJavet("""
                (class {
                    static valuePlusTwo() {
                        return 42;
                    }
                }).valuePlusTwo()""");
    }

    @Test
    public void testClassExpressionComputedFields() {
        assertBooleanWithJavet("""
                let i = 0;
                const C = class {
                    [++i] = 10;
                    static ['tag'] = 42;
                };
                const c1 = new C();
                const c2 = new C();
                i === 1 && c1[1] === 10 && c2[1] === 10 && C.tag === 42""");
    }

    @Test
    public void testClassExpressionStaticMethod() {
        assertIntegerWithJavet("""
                const MathUtils = class {
                    static add(a, b) {
                        return a + b;
                    }
                };
                MathUtils.add(5, 3)""");
    }

    @Test
    public void testClassExpressionStaticMethodCanAccessPrivateField() {
        assertIntegerWithJavet("""
                const Counter = class {
                    #count = 9;
                    static read(instance) {
                        return instance.#count;
                    }
                };
                Counter.read(new Counter())""");
    }

    @Test
    public void testClassExpressionStaticMethodThisBinding() {
        assertBooleanWithJavet("""
                const A = class {
                    static isSelf() {
                        return this === A;
                    }
                };
                A.isSelf()""");
    }

    @Test
    public void testClassExpressionStaticVsInstanceMethodNamespace() {
        assertBooleanWithJavet("""
                const Calculator = class {
                    method() {
                        return 1;
                    }
                    static method() {
                        return 2;
                    }
                };
                const c = new Calculator();
                c.method() === 1 && Calculator.method() === 2 && c.method !== Calculator.method""");
    }

    @Test
    public void testClassGetterSetter() {
        assertBooleanWithJavet("""
                class MyObj {
                    get name() { return 'hello'; }
                    set name(v) { this._name = v; }
                }
                var o = new MyObj();
                o.name === 'hello' && (o.name = 'world', o._name === 'world')""");
    }

    @Test
    public void testClassSetterThrows() {
        assertBooleanWithJavet("""
                class MyArray {
                    set length(v) {
                        throw new Error("setter called");
                    }
                }
                var a = new MyArray();
                var caught = false;
                try { a.length = 5; } catch(e) { caught = e.message === "setter called"; }
                caught""");
    }

    @Test
    public void testClassToString() {
        assertStringWithJavet(
                """
                        class A {
                        }
                        typeof A""",
                """
                        class A {
                          toString() {
                            return 'A';
                          }
                        }
                        A.toString()""",
                """
                        class A {
                          toString() {
                            return 'A';
                          }
                        }
                        String(A)""");
    }

    @Test
    public void testClassWithConstructor() {
        assertIntegerWithJavet("""
                class Point {
                    constructor(x, y) {
                        this.x = x;
                        this.y = y;
                    }
                }
                const p = new Point(1, 2);
                p.x + p.y""");
    }

    @Test
    public void testClassWithDuplicatePrivateMethodThrows() {
        assertThatThrownBy(() -> resetContext().eval("class C { #m() {} #m() {} }"))
                .isInstanceOf(JSException.class);
    }

    @Test
    public void testClassWithFieldsAndConstructor() {
        assertIntegerWithJavet("""
                class Point {
                    z = 5;
                    constructor(x, y) {
                        this.x = x;
                        this.y = y;
                    }
                }
                const p = new Point(1, 2);
                p.x + p.y + p.z""");
    }

    @Test
    public void testClassWithMethod() {
        assertIntegerWithJavet("""
                class Counter {
                    increment() {
                        return 42;
                    }
                }
                const c = new Counter();
                c.increment()""");
    }

    @Test
    public void testClassWithMixedMethods() {
        assertIntegerWithJavet("""
                class Calculator {
                    constructor(value) {
                        this.value = value;
                    }
                
                    add(n) {
                        return this.value + n;
                    }
                
                    static multiply(a, b) {
                        return a * b;
                    }
                }
                
                const c = new Calculator(10);
                const instanceResult = c.add(5);
                const staticResult = Calculator.multiply(3, 4);
                instanceResult + staticResult""");
    }

    @Test
    public void testClassWithMultipleStaticBlocks() {
        assertIntegerWithJavet("""
                class Test {
                    static x = 0;
                    static {
                        this.x = 10;
                    }
                    static {
                        this.x = this.x + 5;
                    }
                }
                Test.x""");
    }

    @Test
    public void testClassWithPrivateField() {
        assertBooleanWithJavet("""
                class Counter {
                    #count = 1;
                }
                const c = new Counter();
                c.count === undefined""");
    }

    @Test
    public void testClassWithPrivateFieldAccess() {
        assertIntegerWithJavet("""
                class Counter {
                    #count = 5;
                    getCount() {
                        return this.#count;
                    }
                    setCount(val) {
                        this.#count = val;
                    }
                }
                const c = new Counter();
                c.setCount(10);
                c.getCount()""");
    }

    @Test
    public void testClassWithPrivateInOperatorCrossClassIsolation() {
        assertBooleanWithJavet("""
                class A {
                    #x = 1;
                    has(obj) {
                        return #x in obj;
                    }
                }
                class B {
                    #x = 2;
                }
                const a = new A();
                const b = new B();
                a.has(a) && !a.has(b)""");
    }

    @Test
    public void testClassWithPrivateInOperatorForInstanceField() {
        assertBooleanWithJavet("""
                class C {
                    #x = 1;
                    has(obj) {
                        return #x in obj;
                    }
                }
                const c = new C();
                c.has(c) && !c.has({})""");
    }

    @Test
    public void testClassWithPrivateInOperatorForPrivateMethod() {
        assertBooleanWithJavet("""
                class C {
                    #m() {
                        return 1;
                    }
                    has(obj) {
                        return #m in obj;
                    }
                }
                const c = new C();
                c.has(c) && !c.has({})""");
    }

    @Test
    public void testClassWithPrivateInOperatorForStaticPrivateField() {
        assertBooleanWithJavet("""
                class C {
                    static #x = 1;
                    static has(obj) {
                        return #x in obj;
                    }
                }
                C.has(C) && !C.has({})""");
    }

    @Test
    public void testClassWithPrivateInOperatorInvalidRightOperandThrows() {
        assertThatThrownBy(() -> resetContext().eval("""
                class C {
                    #x = 1;
                    has(value) {
                        return #x in value;
                    }
                }
                new C().has(1)"""))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("invalid 'in' operand");
    }

    @Test
    public void testClassWithPrivateInOperatorUndefinedPrivateFieldThrows() {
        assertThatThrownBy(() -> resetContext().eval("""
                class C {
                    #x = 1;
                    has(obj) {
                        return #y in obj;
                    }
                }"""))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("undefined private field '#y'");
    }

    @Test
    public void testClassWithPrivateMethod() {
        assertIntegerWithJavet("""
                class Calculator {
                    #double(value) {
                        return value * 2;
                    }
                    run(value) {
                        return this.#double(value);
                    }
                }
                new Calculator().run(21)""");
    }

    @Test
    public void testClassWithPrivateMethodAccessibleDuringFieldInitialization() {
        assertIntegerWithJavet("""
                class C {
                    value = this.#getValue();
                    #getValue() {
                        return 9;
                    }
                }
                new C().value""");
    }

    @Test
    public void testClassWithPrivateMethodNotExposedAsPublicProperty() {
        assertBooleanWithJavet("""
                class C {
                    #hidden() {
                        return 1;
                    }
                }
                const c = new C();
                c.hidden === undefined""");
    }

    @Test
    public void testClassWithPrivateMethodReference() {
        assertIntegerWithJavet("""
                class C {
                    constructor() {
                        this.x = 5;
                    }
                    #read() {
                        return this.x;
                    }
                    getReader() {
                        return this.#read;
                    }
                }
                const c = new C();
                const fn = c.getReader();
                fn.call(c)""");
    }

    @Test
    public void testClassWithPublicField() {
        assertIntegerWithJavet("""
                class Counter {
                    count = 0;
                }
                const c = new Counter();
                c.count""");
    }

    @Test
    public void testClassWithPublicFieldInitializer() {
        assertIntegerWithJavet("""
                class Point {
                    x = 10;
                    y = 20;
                }
                const p = new Point();
                p.x + p.y""");
    }

    @Test
    public void testClassWithStaticBlock() {
        assertStringWithJavet("""
                class Config {
                    static apiUrl;
                    static {
                        this.apiUrl = 'http://localhost:3000';
                    }
                }
                Config.apiUrl""");
    }

    @Test
    public void testClassWithStaticMethod() {
        assertIntegerWithJavet("""
                class MathUtils {
                    static add(a, b) {
                        return a + b;
                    }
                }
                MathUtils.add(5, 3)""");
    }

    @Test
    public void testClassWithStaticPrivateFieldInClassExpression() {
        assertIntegerWithJavet("""
                const C = class {
                    static #value = 42;
                    static getValue() {
                        return this.#value;
                    }
                };
                C.getValue()""");
    }

    @Test
    public void testClassWithStaticPrivateFieldInMixedPrivateNamespace() {
        assertIntegerWithJavet("""
                class C {
                    #instanceValue = 3;
                    static #staticValue = 4;
                    getInstanceValue() {
                        return this.#instanceValue;
                    }
                    static getStaticValue() {
                        return this.#staticValue;
                    }
                }
                const c = new C();
                c.getInstanceValue() + C.getStaticValue()""");
    }

    @Test
    public void testClassWithStaticPrivateFieldInitializedByStaticBlock() {
        assertIntegerWithJavet("""
                class C {
                    static #value = 10;
                    static {
                        this.#value = this.#value + 5;
                    }
                    static getValue() {
                        return this.#value;
                    }
                }
                C.getValue()""");
    }

    @Test
    public void testClassWithStaticPrivateFieldInitializerAndMutation() {
        assertIntegerWithJavet("""
                class Counter {
                    static #count = 1;
                    static increment() {
                        this.#count = this.#count + 1;
                    }
                    static getCount() {
                        return this.#count;
                    }
                }
                Counter.increment();
                Counter.getCount()""");
    }

    @Test
    public void testClassWithStaticPrivateFieldWithoutInitializer() {
        assertBooleanWithJavet("""
                class C {
                    static #value;
                    static getValue() {
                        return this.#value;
                    }
                }
                C.getValue() === undefined""");
    }

    @Test
    public void testClassWithStaticPrivateMethod() {
        assertIntegerWithJavet("""
                class C {
                    static #inc(value) {
                        return value + 1;
                    }
                    static run(value) {
                        return this.#inc(value);
                    }
                }
                C.run(41)""");
    }

    @Test
    public void testDefaultDerivedConstructorForwardsArgumentsToSuper() {
        assertBooleanWithJavet("""
                class Parent {
                    constructor(a, b, c) {
                        this.argCount = arguments.length;
                        this.second = b;
                        this.third = c;
                    }
                }
                class Child extends Parent {}
                const child = new Child(1, 2, 3);
                child instanceof Child &&
                    child instanceof Parent &&
                    child.argCount === 3 &&
                    child.second === 2 &&
                    child.third === 3""");
    }

    @Test
    public void testDefaultDerivedTypedArrayConstructorOutOfBoundsThrowsRangeError() {
        assertBooleanWithJavet("""
                const buffer = new ArrayBuffer(4);
                class MyUint8Array extends Uint8Array {}
                try {
                    new MyUint8Array(buffer, 0, 8);
                    false;
                } catch (e) {
                    e instanceof RangeError;
                }""");
    }

    @Test
    public void testDefaultDerivedTypedArrayConstructorResizableArrayBufferOutOfBoundsThrowsRangeError() {
        assertBooleanWithJavet("""
                const rab = new ArrayBuffer(40, { maxByteLength: 80 });
                class MyUint8Array extends Uint8Array {}
                class MyFloat32Array extends Float32Array {}
                let ok = true;
                try {
                    new MyUint8Array(rab, 40, 1);
                    ok = false;
                } catch (e) {
                    ok = ok && (e instanceof RangeError);
                }
                try {
                    new MyFloat32Array(rab, 1);
                    ok = false;
                } catch (e) {
                    ok = ok && (e instanceof RangeError);
                }
                ok""");
    }

    @Test
    public void testDefaultDerivedTypedArrayConstructorUsesConstructorPath() {
        assertBooleanWithJavet("""
                class MyUint8Array extends Uint8Array {}
                const arr = new MyUint8Array(4);
                arr instanceof MyUint8Array &&
                    arr instanceof Uint8Array &&
                    arr.length === 4 &&
                    arr[0] === 0""");
    }

    @Test
    public void testPrivateInOperatorOutsideClassThrows() {
        assertThatThrownBy(() -> resetContext().eval("#x in ({})"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("undefined private field '#x'");
    }
}
