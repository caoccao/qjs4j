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

package com.caoccao.qjs4j;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interfaces.IJavetSupplier;
import com.caoccao.javet.interfaces.IJavetUniFunction;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.options.V8RuntimeOptions;
import com.caoccao.javet.utils.JavetOSUtils;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.primitive.*;
import com.caoccao.javet.values.reference.V8ValueBooleanObject;
import com.caoccao.javet.values.reference.V8ValueLongObject;
import com.caoccao.javet.values.reference.V8ValuePromise;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BaseJavetTest extends BaseTest {
    protected static final String FILE_NAME = "test.js";

    static {
        File icuDataFile = new File(JavetOSUtils.WORKING_DIRECTORY).toPath().resolve("icu/icudtl.dat").normalize()
                .toFile();
        V8RuntimeOptions.V8_FLAGS.setIcuDataFile(icuDataFile.getAbsolutePath());
    }

    protected boolean debugMode;
    protected boolean moduleMode;
    protected V8Runtime v8Runtime;

    protected void assertBigIntegerObjectWithJavet(String... codeArray) {
        assertResultsWithJavet(codeArray, V8ValueLongObject.class, v -> BigInteger.valueOf(v.valueOf().getValue()),
                p -> BigInteger.valueOf(p.getResultLong()),
                v -> v.asBigIntObject().map(JSBigIntObject::getValue).map(JSBigInt::value).orElseThrow());
    }

    protected void assertBigIntegerWithJavet(String... codeArray) {
        assertResultsWithJavet(codeArray, V8ValueBigInteger.class, V8ValueBigInteger::getValue,
                V8ValuePromise::getResultBigInteger, JSValue::toJavaObject);
    }

    protected void assertBooleanObjectWithJavet(String... codeArray) {
        assertResultsWithJavet(codeArray, V8ValueBooleanObject.class, v -> v.valueOf().getValue(),
                V8ValuePromise::getResultBoolean, JSValue::toJavaObject);
    }

    protected void assertBooleanWithJavet(String... codeArray) {
        assertResultsWithJavet(codeArray, V8ValueBoolean.class, V8ValueBoolean::getValue,
                V8ValuePromise::getResultBoolean, JSValue::toJavaObject);
    }

    protected void assertDoubleWithJavet(String... codeArray) {
        assertResultsWithJavet(codeArray, V8ValueDouble.class, V8ValueDouble::getValue, V8ValuePromise::getResultDouble,
                JSValue::toJavaObject);
    }

    protected void assertErrorWithJavet(String... codeArray) {
        for (String code : getNormalizedCodeStrings(codeArray)) {
            try {
                v8Runtime.resetContext();
            } catch (JavetException e) {
            }
            String expectedMessage = assertThatThrownBy(
                    () -> v8Runtime.getExecutor(code).setModule(moduleMode).executeVoid())
                    .isInstanceOf(JavetException.class).actual().getMessage();
            assertThatThrownBy(() -> resetContext().eval(code, FILE_NAME, moduleMode), expectedMessage)
                    .isInstanceOf(JSException.class).hasMessage(expectedMessage);
        }
    }

    protected void assertIntegerWithJavet(String... codeArray) {
        assertResultsWithJavet(codeArray, V8ValueInteger.class, V8ValueInteger::getValue,
                V8ValuePromise::getResultInteger, value -> {
                    assertThat(value).isInstanceOf(JSNumber.class);
                    return (int) ((JSNumber) value).value();
                });
    }

    protected void assertLongWithJavet(String... codeArray) {
        assertResultsWithJavet(codeArray, V8ValueLong.class, v -> BigInteger.valueOf(v.getValue()),
                p -> BigInteger.valueOf(p.getResultLong()), JSValue::toJavaObject);
    }

    protected void assertObjectWithJavet(String... codeArray) {
        assertResultsWithJavet(codeArray, V8Value.class, v8Runtime::toObject, V8ValuePromise::getResultObject,
                JSValue::toJavaObject);
    }

    private <V extends V8Value> void assertResultsWithJavet(String[] codeArray, Class<V> expectedType,
            IJavetUniFunction<V, Object, JavetException> readValue,
            IJavetUniFunction<V8ValuePromise, Object, JavetException> readPromise,
            Function<JSValue, Object> readJSValue) {
        for (String code : getNormalizedCodeStrings(codeArray)) {
            printCode(code);
            assertWithJavet(() -> {
                v8Runtime.resetContext();
                try (V8Value value = v8Runtime.getExecutor(code).setModule(moduleMode).execute()) {
                    if (value instanceof V8ValuePromise promise) {
                        v8Runtime.await();
                        assertThat(promise.getState()).as(code).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                        return readPromise.apply(promise);
                    }
                    assertThat(value).as(code).isInstanceOf(expectedType);
                    return readValue.apply(expectedType.cast(value));
                }
            }, () -> {
                JSValue value = resetContext().eval(code, FILE_NAME, moduleMode);
                if (value instanceof JSPromise promise) {
                    awaitPromise(promise);
                    value = promise.getResult();
                }
                return readJSValue.apply(value);
            });
        }
    }

    protected void assertStringWithJavet(String... codeArray) {
        assertResultsWithJavet(codeArray, V8ValueString.class, V8ValueString::getValue, V8ValuePromise::getResultString,
                JSValue::toJavaObject);
    }

    protected void assertUndefinedWithJavet(String... codeArray) {
        assertResultsWithJavet(codeArray, V8ValueUndefined.class, v -> true, p -> p.getResult().isUndefined(),
                JSValue::isUndefined);
    }

    protected <E extends Exception, T> void assertWithJavet(IJavetSupplier<T, E> javetSupplier,
            Supplier<T> qjs4jSupplier) {
        T expectedResult = null;
        Throwable expectedException = null;
        try {
            expectedResult = javetSupplier.get();
        } catch (Throwable t) {
            expectedException = t;
            assertThat(expectedException).isInstanceOf(JavetException.class);
        }
        if (expectedException == null) {
            T result = qjs4jSupplier.get();
            assertThat(result).isEqualTo(expectedResult);
        } else {
            String expectedMessage = expectedException.getMessage();
            assertThatThrownBy(qjs4jSupplier::get, expectedMessage).isInstanceOfSatisfying(JSException.class,
                    jsException -> {
                        assertThat(jsException.getMessage()).isEqualTo(expectedMessage);
                    });
        }
    }

    protected List<String> getNormalizedCodeStrings(String... codeArray) {
        return Stream.of(codeArray).flatMap(code -> Stream.of(code, "'use strict';\n" + code)).toList();
    }

    protected void printCode(String code) {
        if (debugMode) {
            System.out.println(code);
            System.out.println("----------------");
        }
    }

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        debugMode = false;
        moduleMode = false;
        V8RuntimeOptions.V8_FLAGS.setJsFloat16Array(true).setUseStrict(false);
        v8Runtime = V8Host.getV8I18nInstance().createV8Runtime();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        assertThat(v8Runtime.getReferenceCount()).as("V8 runtime reference count").isEqualTo(0);
        v8Runtime.close();
        debugMode = false;
        moduleMode = false;
        super.tearDown();
    }
}
