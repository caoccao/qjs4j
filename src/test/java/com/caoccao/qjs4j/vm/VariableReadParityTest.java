package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class VariableReadParityTest extends BaseJavetTest {
    @ParameterizedTest
    @ValueSource(strings = {"binding", "typeof binding"})
    public void testDirectEvalResolvesClassInnerName(String expression) {
        assertStringWithJavet("""
                (() => {
                  const subject = class binding {
                    static read() { return eval('%s'); }
                    read() { return eval('%s'); }
                  };
                  const normalize = value => value === subject ? 'class' : String(value);
                  return [normalize(subject.read()), normalize(new subject().read())].join('|');
                })()
                """.formatted(expression, expression));
    }

    @ParameterizedTest
    @ValueSource(strings = {"binding", "typeof binding"})
    public void testDirectEvalResolvesDynamicBindings(String expression) {
        assertStringWithJavet("""
                globalThis.binding = 'global';
                Function("eval('var binding = true'); return String(eval('%s'));")()
                """.formatted(expression));
    }

    @ParameterizedTest
    @ValueSource(strings = {"binding", "typeof binding"})
    public void testDirectEvalResolvesShadowingBindings(String expression) {
        assertStringWithJavet("""
                (function (binding) {
                  const capture = () => binding;
                  return (function () {
                    const results = [String(eval('%s'))];
                    results.push((function (binding) { return String(eval('%s')); })(7));
                    results.push(String(eval('%s')));
                    results.push(capture());
                    return results.join('|');
                  })();
                })('outer')
                """.formatted(expression, expression, expression));
    }

    @ParameterizedTest
    @ValueSource(strings = {"binding", "typeof binding"})
    public void testGlobalGettersAndMissingNames(String expression) {
        assertStringWithJavet("""
                (() => ['missing', 'undefined', 'value', 'throw'].map(mode => {
                  const log = [];
                  const marker = {};
                  if (mode !== 'missing') {
                    Object.defineProperty(globalThis, 'binding', { configurable: true, get() {
                      log.push('get');
                      if (mode === 'throw') throw marker;
                      return mode === 'value' ? 7 : undefined;
                    } });
                  }
                  let result;
                  try { result = String(%s); }
                  catch (e) { result = e === marker ? 'marker' : e.name; }
                  delete globalThis.binding;
                  return mode + ':' + result + ':' + log.join(',');
                }).join('|'))()
                """.formatted(expression));
    }

    @ParameterizedTest
    @ValueSource(strings = {"binding", "typeof binding"})
    public void testGlobalLexicalBindingShadowsGlobalProperty(String expression) {
        assertStringWithJavet("""
                globalThis.binding = 'property';
                let binding = 17;
                (() => String(%s) + '|' + globalThis.binding)()
                """.formatted(expression));
    }

    @ParameterizedTest
    @ValueSource(strings = {"binding", "typeof binding"})
    public void testGlobalLexicalInitializationErrors(String expression) {
        assertStringWithJavet("""
                var before;
                try { eval('%s'); } catch (e) { before = e.name; }
                let binding = 9;
                before + '|' + String(eval('%s'));
                """.formatted(expression, expression));
    }

    @Test
    public void testMissingCallDiagnosticRetainsPropertyPath() {
        assertErrorWithJavet("""
                globalThis.holder = { nested: { invoke: 0 } };
                holder.nested.invoke(typeof missingArgument);
                """);
    }
}
