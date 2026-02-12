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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSArrayBuffer;
import com.caoccao.qjs4j.core.JSDataView;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for DataView constructor functionality.
 * Tests various constructor call patterns.
 */
public class DataViewConstructorTest extends BaseJavetTest {

    @Test
    public void testBasicConstructor() {
        assertIntegerWithJavet("""
                const buffer = new ArrayBuffer(16);
                const dv = new DataView(buffer);
                dv.byteLength;""");
    }

    @Test
    public void testBigIntMethods() {
        assertStringWithJavet("""
                const dv = new DataView(new ArrayBuffer(8));
                dv.setBigInt64(0, '1');
                dv.getBigInt64(0).toString();
                """);
        assertStringWithJavet("""
                const dv = new DataView(new ArrayBuffer(8));
                dv.setBigInt64(0, true);
                dv.getBigInt64(0).toString();
                """);
        assertStringWithJavet("""
                const dv = new DataView(new ArrayBuffer(8));
                dv.setBigUint64(0, '-1');
                dv.getBigUint64(0).toString();
                """);

        assertThatThrownBy(() -> context.eval("""
                const dv = new DataView(new ArrayBuffer(8));
                dv.setBigInt64(0, 1);
                """))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
    }

    @Test
    public void testBufferReference() {
        assertBooleanWithJavet("""
                const buffer = new ArrayBuffer(16);
                const dv = new DataView(buffer);
                dv.buffer === buffer;""");
    }

    @Test
    public void testByteOffset() {
        assertIntegerWithJavet("""
                const buffer = new ArrayBuffer(16);
                const dv = new DataView(buffer, 8);
                dv.byteOffset;""");
    }

    @Test
    public void testConstructorWithBuffer() {
        assertIntegerWithJavet("""
                const buffer = new ArrayBuffer(16);
                const dv = new DataView(buffer);
                dv.byteLength;""");
    }

    @Test
    public void testConstructorWithByteOffset() {
        assertIntegerWithJavet("""
                const buffer = new ArrayBuffer(16);
                const dv = new DataView(buffer, 4);
                dv.byteLength;""");
    }

    @Test
    public void testConstructorWithByteOffsetAndLength() {
        assertIntegerWithJavet("""
                const buffer = new ArrayBuffer(16);
                const dv = new DataView(buffer, 4, 8);
                dv.byteLength;""");
    }

    @Test
    public void testConstructorWithOffset() {
        assertIntegerWithJavet("""
                const buffer = new ArrayBuffer(16);
                const dv = new DataView(buffer, 4);
                dv.byteLength;""");
    }

    @Test
    public void testConstructorWithOffsetAndLength() {
        assertIntegerWithJavet("""
                const buffer = new ArrayBuffer(16);
                const dv = new DataView(buffer, 4, 8);
                dv.byteLength;""");
    }

    @Test
    public void testDataViewBuffer() {
        assertBooleanWithJavet("""
                const buffer = new ArrayBuffer(16);
                const dv = new DataView(buffer);
                dv.buffer === buffer;""");
    }

    @Test
    public void testDataViewByteOffset() {
        assertIntegerWithJavet("""
                const buffer = new ArrayBuffer(16);
                const dv = new DataView(buffer, 8);
                dv.byteOffset;""");
    }

    @Test
    public void testDataViewConstructorCall() {
        // Calling DataView as function should throw (requires new)
        assertErrorWithJavet("""
                const buffer = new ArrayBuffer(16);
                DataView(buffer);""");
    }

    @Test
    public void testDataViewInstanceOf() {
        assertBooleanWithJavet("""
                const buffer = new ArrayBuffer(8);
                const dv = new DataView(buffer);
                dv instanceof DataView;""");
    }

    @Test
    public void testDataViewIsNotArray() {
        assertBooleanWithJavet("""
                const buffer = new ArrayBuffer(8);
                const dv = new DataView(buffer);
                Array.isArray(dv);""");
    }

    @Test
    public void testDataViewJavaConstructor() {
        // Test creating DataView from Java
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = (JSDataView) JSDataView.create(context, buffer);

        assertThat(dataView).isNotNull();
        assertThat(dataView.getByteLength()).isEqualTo(16);
        assertThat(dataView.getByteOffset()).isEqualTo(0);
    }

    @Test
    public void testDataViewJavaConstructorWithOffset() {
        // Test creating DataView with offset from Java
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = (JSDataView) JSDataView.create(context, buffer, new JSNumber(4));

        assertThat(dataView).isNotNull();
        assertThat(dataView.getByteLength()).isEqualTo(12);
        assertThat(dataView.getByteOffset()).isEqualTo(4);
    }

    @Test
    public void testDataViewJavaConstructorWithOffsetAndLength() {
        // Test creating DataView with offset and length from Java
        JSArrayBuffer buffer = new JSArrayBuffer(16);
        JSDataView dataView = (JSDataView) JSDataView.create(context, buffer, new JSNumber(4), new JSNumber(8));

        assertThat(dataView).isNotNull();
        assertThat(dataView.getByteLength()).isEqualTo(8);
        assertThat(dataView.getByteOffset()).isEqualTo(4);
    }

    @Test
    public void testDataViewLength() {
        assertIntegerWithJavet("DataView.length;");
    }

    @Test
    public void testDataViewName() {
        assertStringWithJavet("DataView.name;");
    }

    @Test
    public void testDataViewPrototypeChain() {
        assertBooleanWithJavet("""
                const buffer = new ArrayBuffer(8);
                const dv = new DataView(buffer);
                Object.getPrototypeOf(dv) === DataView.prototype;""");
    }

    @Test
    public void testDataViewReadWrite() {
        assertIntegerWithJavet("""
                const buffer = new ArrayBuffer(8);
                const dv = new DataView(buffer);
                dv.setInt32(0, 42);
                dv.getInt32(0);""");
    }

    @Test
    public void testDataViewToString() {
        assertStringWithJavet("""
                const buffer = new ArrayBuffer(8);
                const dv = new DataView(buffer);
                Object.prototype.toString.call(dv);""");
    }

    @Test
    public void testDataViewType() {
        assertStringWithJavet("typeof DataView;");
    }

    @Test
    public void testInstanceOf() {
        assertBooleanWithJavet("""
                const buffer = new ArrayBuffer(8);
                const dv = new DataView(buffer);
                dv instanceof DataView;""");
    }

    @Test
    public void testMethodDescriptorsAndLengths() {
        assertThat(context.eval("""
                (() => {
                  const proto = DataView.prototype;
                  const methods = [
                    ['getInt8', 1], ['getUint8', 1],
                    ['getInt16', 1], ['getUint16', 1],
                    ['getInt32', 1], ['getUint32', 1],
                    ['getBigInt64', 1], ['getBigUint64', 1],
                    ['getFloat32', 1], ['getFloat64', 1],
                    ['setInt8', 2], ['setUint8', 2],
                    ['setInt16', 2], ['setUint16', 2],
                    ['setInt32', 2], ['setUint32', 2],
                    ['setBigInt64', 2], ['setBigUint64', 2],
                    ['setFloat32', 2], ['setFloat64', 2],
                  ];
                  for (const method of methods) {
                    const name = method[0];
                    const len = method[1];
                    const desc = Object.getOwnPropertyDescriptor(proto, name);
                    if (!(typeof proto[name] === 'function'
                      && proto[name].length === len
                      && desc.writable === true
                      && desc.enumerable === false
                      && desc.configurable === true)) {
                      return false;
                    }
                  }
                  return true;
                })();
                """).toJavaObject()).isEqualTo(true);
        assertStringWithJavet("""
                Object.getOwnPropertyDescriptor(DataView.prototype, Symbol.toStringTag).value;
                """);

        assertThat(context.eval("""
                (() => {
                  const proto = DataView.prototype;
                  const methods = [['getFloat16', 1], ['setFloat16', 2]];
                  for (const method of methods) {
                    const name = method[0];
                    const len = method[1];
                    const desc = Object.getOwnPropertyDescriptor(proto, name);
                    if (!(typeof proto[name] === 'function'
                      && proto[name].length === len
                      && desc.writable === true
                      && desc.enumerable === false
                      && desc.configurable === true)) {
                      return false;
                    }
                  }
                  return true;
                })();
                """).toJavaObject()).isEqualTo(true);
    }

    @Test
    public void testNewDataViewWorks() {
        assertStringWithJavet("""
                const buffer = new ArrayBuffer(16);
                const dv = new DataView(buffer);
                typeof dv;""");
    }

    @Test
    public void testNumericMethodsAndIndexConversion() {
        assertIntegerWithJavet("""
                const dv = new DataView(new ArrayBuffer(8));
                dv.setUint16(0, 0xFEDC, true);
                dv.getUint16(0, true);
                """);
        assertDoubleWithJavet("""
                const dv = new DataView(new ArrayBuffer(8));
                dv.setUint32(0, 0xFFFFFFFF, true);
                dv.getUint32(0, true);
                """);
        assertIntegerWithJavet("""
                const dv = new DataView(new ArrayBuffer(8));
                dv.setInt8(1.9, 7);
                dv.getInt8(1);
                """);
        assertDoubleWithJavet("""
                const dv = new DataView(new ArrayBuffer(8));
                dv.setFloat16(0, 1.5, true);
                dv.getFloat16(0, true);
                """);

        assertThatThrownBy(() -> context.eval("""
                const dv = new DataView(new ArrayBuffer(8));
                dv.getInt8(-1);
                """))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
        assertThatThrownBy(() -> context.eval("""
                const dv = new DataView(new ArrayBuffer(8));
                dv.getInt8(Symbol());
                """))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
    }

    @Test
    public void testPrototypeChain() {
        assertBooleanWithJavet("""
                const buffer = new ArrayBuffer(8);
                const dv = new DataView(buffer);
                Object.getPrototypeOf(dv) === DataView.prototype;
                """);
    }

    @Test
    public void testReadWriteFloat64() {
        assertDoubleWithJavet("""
                const buffer = new ArrayBuffer(8);
                const dv = new DataView(buffer);
                dv.setFloat64(0, 3.14159);
                dv.getFloat64(0);
                """);
    }

    @Test
    public void testReadWriteInt32() {
        assertIntegerWithJavet("""
                const buffer = new ArrayBuffer(8);
                const dv = new DataView(buffer);
                dv.setInt32(0, 42);
                dv.getInt32(0);
                """);
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet(
                "typeof DataView;");
        assertIntegerWithJavet(
                "DataView.length;");
    }
}
