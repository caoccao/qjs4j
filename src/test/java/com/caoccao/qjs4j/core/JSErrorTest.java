package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class JSErrorTest extends BaseJavetTest {
    @Test
    public void testInstanceof() {
        assertBooleanWithJavet(
                "new Error('Error') instanceof Error",
                "new EvalError('EvalError') instanceof EvalError",
                "new Error('Error') instanceof Error",
                "new RangeError('RangeError') instanceof RangeError",
                "new ReferenceError('ReferenceError') instanceof ReferenceError",
                "new SyntaxError('SyntaxError') instanceof SyntaxError",
                "new TypeError('TypeError') instanceof TypeError",
                "new URIError('URIError') instanceof URIError",
                "new Error('Error') instanceof Error",
                "new AggregateError('AggregateError') instanceof AggregateError",
                "new SuppressedError(new Error('a'), new Error('b'), 'msg') instanceof SuppressedError");
    }

    @Test
    public void testSuppressedError() {
        assertStringWithJavet(
                "const e1 = new SuppressedError(new Error('main'), new Error('suppressed'), 'custom message'); e1.name + ': ' + e1.message",
                "const e2 = new SuppressedError(new Error('A'), new Error('B')); e2.error.message + '|' + e2.suppressed.message",
                "try { throw new SuppressedError(new Error('query failed'), new Error('cleanup failed'), 'transaction aborted'); } catch (e) { e.name + ': ' + e.message; }",
                "try { throw new SuppressedError(new Error('E1'), new Error('E2'), 'msg'); } catch (e) { e.error.message + '|' + e.suppressed.message; }",
                "try { const err = new SuppressedError(new Error('primary'), new Error('secondary')); throw err; } catch (e) { e.name + ': ' + e.error.message + ' / ' + e.suppressed.message; }");
    }

    @Test
    public void testTryCatchTypeError() {
        assertStringWithJavet("try { throw new TypeError('I am a TypeError'); } catch (e) { e.name + ': ' + e.message; }");
    }
}
