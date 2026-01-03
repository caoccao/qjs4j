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
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.options.V8RuntimeOptions;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.primitive.*;
import com.caoccao.javet.values.reference.V8ValueBooleanObject;
import com.caoccao.javet.values.reference.V8ValueLongObject;
import com.caoccao.javet.values.reference.V8ValuePromise;
import com.caoccao.qjs4j.core.JSBigInt;
import com.caoccao.qjs4j.core.JSBigIntObject;
import com.caoccao.qjs4j.core.JSPromise;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

public class BaseJavetTest extends BaseTest {
    protected boolean moduleMode;
    protected V8Runtime v8Runtime;

    protected void assertBigIntegerObjectWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> {
                        try (V8Value v8Value = v8Runtime.getExecutor(code).setModule(moduleMode).execute()) {
                            if (v8Value instanceof V8ValuePromise v8ValuePromise) {
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).as(code).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                return BigInteger.valueOf(v8ValuePromise.getResultLong());
                            } else {
                                assertThat(v8Value).as(code).isInstanceOf(V8ValueLongObject.class);
                                return BigInteger.valueOf(((V8ValueLongObject) v8Value).valueOf().getValue());
                            }
                        }
                    },
                    () -> {
                        JSValue jsValue = context.eval(code);
                        if (jsValue instanceof JSPromise jsPromise) {
                            awaitPromise(jsPromise);
                            jsValue = jsPromise.getResult();
                        }
                        return jsValue.asBigIntObject().map(JSBigIntObject::getValue).map(JSBigInt::value).orElseThrow();
                    });
        }
    }

    protected void assertBigIntegerWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> {
                        try (V8Value v8Value = v8Runtime.getExecutor(code).setModule(moduleMode).execute()) {
                            if (v8Value instanceof V8ValuePromise v8ValuePromise) {
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).as(code).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                return BigInteger.valueOf(v8ValuePromise.getResultLong());
                            } else {
                                assertThat(v8Value).as(code).isInstanceOf(V8ValueLong.class);
                                return BigInteger.valueOf(((V8ValueLong) v8Value).getValue());
                            }
                        }
                    },
                    () -> {
                        JSValue jsValue = context.eval(code);
                        if (jsValue instanceof JSPromise jsPromise) {
                            awaitPromise(jsPromise);
                            jsValue = jsPromise.getResult();
                        }
                        return jsValue.toJavaObject();
                    });
        }
    }

    protected void assertBooleanObjectWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> {
                        try (V8Value v8Value = v8Runtime.getExecutor(code).setModule(moduleMode).execute()) {
                            if (v8Value instanceof V8ValuePromise v8ValuePromise) {
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).as(code).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                return v8ValuePromise.getResultBoolean();
                            } else {
                                assertThat(v8Value).as(code).isInstanceOf(V8ValueBooleanObject.class);
                                return ((V8ValueBooleanObject) v8Value).valueOf().getValue();
                            }
                        }
                    },
                    () -> {
                        JSValue jsValue = context.eval(code);
                        if (jsValue instanceof JSPromise jsPromise) {
                            awaitPromise(jsPromise);
                            jsValue = jsPromise.getResult();
                        }
                        return jsValue.toJavaObject();
                    });
        }
    }

    protected void assertBooleanWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> {
                        try (V8Value v8Value = v8Runtime.getExecutor(code).setModule(moduleMode).execute()) {
                            if (v8Value instanceof V8ValuePromise v8ValuePromise) {
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).as(code).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                return v8ValuePromise.getResultBoolean();
                            } else {
                                assertThat(v8Value).as(code).isInstanceOf(V8ValueBoolean.class);
                                return ((V8ValueBoolean) v8Value).getValue();
                            }
                        }
                    },
                    () -> {
                        JSValue jsValue = context.eval(code);
                        if (jsValue instanceof JSPromise jsPromise) {
                            awaitPromise(jsPromise);
                            jsValue = jsPromise.getResult();
                        }
                        return jsValue.toJavaObject();
                    });
        }
    }

    protected void assertDoubleWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> {
                        try (V8Value v8Value = v8Runtime.getExecutor(code).setModule(moduleMode).execute()) {
                            if (v8Value instanceof V8ValuePromise v8ValuePromise) {
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                return v8ValuePromise.getResultDouble();
                            } else {
                                assertThat(v8Value).isInstanceOf(V8ValueDouble.class);
                                return ((V8ValueDouble) v8Value).getValue();
                            }
                        }
                    },
                    () -> {
                        JSValue jsValue = context.eval(code);
                        if (jsValue instanceof JSPromise jsPromise) {
                            awaitPromise(jsPromise);
                            jsValue = jsPromise.getResult();
                        }
                        return jsValue.toJavaObject();
                    });
        }
    }

    protected void assertErrorWithJavet(String... codeArray) {
        for (String code : codeArray) {
            String expectedMessage = assertThatThrownBy(() -> v8Runtime.getExecutor(code).setModule(moduleMode).executeVoid())
                    .isInstanceOf(JavetException.class).actual().getMessage();
            assertThatThrownBy(() -> context.eval(code), expectedMessage)
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining(expectedMessage);
        }
    }

    protected void assertIntegerWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> {
                        try (V8Value v8Value = v8Runtime.getExecutor(code).setModule(moduleMode).execute()) {
                            if (v8Value instanceof V8ValuePromise v8ValuePromise) {
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                return (double) v8ValuePromise.getResultInteger();
                            } else {
                                assertThat(v8Value).isInstanceOf(V8ValueInteger.class);
                                return (double) ((V8ValueInteger) v8Value).getValue();
                            }
                        }
                    },
                    () -> {
                        JSValue jsValue = context.eval(code);
                        if (jsValue instanceof JSPromise jsPromise) {
                            awaitPromise(jsPromise);
                            jsValue = jsPromise.getResult();
                        }
                        return jsValue.toJavaObject();
                    });
        }
    }

    protected void assertLongWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> {
                        try (V8Value v8Value = v8Runtime.getExecutor(code).setModule(moduleMode).execute()) {
                            if (v8Value instanceof V8ValuePromise v8ValuePromise) {
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).as(code).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                return BigInteger.valueOf(v8ValuePromise.getResultLong());
                            } else {
                                assertThat(v8Value).as(code).isInstanceOf(V8ValueLong.class);
                                return BigInteger.valueOf(((V8ValueLong) v8Value).getValue());
                            }
                        }
                    },
                    () -> {
                        JSValue jsValue = context.eval(code);
                        if (jsValue instanceof JSPromise jsPromise) {
                            awaitPromise(jsPromise);
                            jsValue = jsPromise.getResult();
                        }
                        return jsValue.toJavaObject();
                    });
        }
    }

    protected void assertObjectWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> {
                        try (V8Value v8Value = v8Runtime.getExecutor(code).setModule(moduleMode).execute()) {
                            if (v8Value instanceof V8ValuePromise v8ValuePromise) {
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).as(code).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                return v8ValuePromise.getResultObject();
                            } else {
                                return v8Runtime.toObject(v8Value);
                            }
                        }
                    },
                    () -> {
                        JSValue jsValue = context.eval(code);
                        if (jsValue instanceof JSPromise jsPromise) {
                            awaitPromise(jsPromise);
                            jsValue = jsPromise.getResult();
                        }
                        return jsValue.toJavaObject();
                    });
        }
    }

    protected void assertStringWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> {
                        try (V8Value v8Value = v8Runtime.getExecutor(code).setModule(moduleMode).execute()) {
                            if (v8Value instanceof V8ValuePromise v8ValuePromise) {
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).as(code).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                return v8ValuePromise.getResultString();
                            } else {
                                assertThat(v8Value).as(code).isInstanceOf(V8ValueString.class);
                                return ((V8ValueString) v8Value).getValue();
                            }
                        }
                    },
                    () -> {
                        JSValue jsValue = context.eval(code);
                        if (jsValue instanceof JSPromise jsPromise) {
                            awaitPromise(jsPromise);
                            jsValue = jsPromise.getResult();
                        }
                        return jsValue.toJavaObject();
                    });
        }
    }

    protected void assertUndefinedWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> {
                        try (V8Value v8Value = v8Runtime.getExecutor(code).setModule(moduleMode).execute()) {
                            if (v8Value instanceof V8ValuePromise v8ValuePromise) {
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).as(code).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                return v8ValuePromise.getResult().isUndefined();
                            } else {
                                assertThat(v8Value).as(code).isInstanceOf(V8ValueUndefined.class);
                                return true;
                            }
                        }
                    },
                    () -> {
                        JSValue jsValue = context.eval(code);
                        if (jsValue instanceof JSPromise jsPromise) {
                            awaitPromise(jsPromise);
                            jsValue = jsPromise.getResult();
                        }
                        return jsValue.isUndefined();
                    });
        }
    }

    protected <E extends Exception, T> void assertWithJavet(
            IJavetSupplier<T, E> javetSupplier,
            Supplier<T> qjs4jSupplier) {
        try {
            T expectedResult = javetSupplier.get();
            T result = qjs4jSupplier.get();
            assertThat(result).isEqualTo(expectedResult);
        } catch (Throwable t) {
            fail(t);
        }
    }

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        moduleMode = false;
        V8RuntimeOptions.V8_FLAGS.setJsFloat16Array(true);
        v8Runtime = V8Host.getV8Instance().createV8Runtime();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        assertThat(v8Runtime.getReferenceCount()).as("V8 runtime reference count").isEqualTo(0);
        v8Runtime.close();
        moduleMode = false;
        super.tearDown();
    }
}
