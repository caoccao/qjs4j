package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class VariableReadParityTest extends BaseJavetTest {
    @Test
    public void testDirectEvalResolvesClassInnerName() {
        assertStringWithJavet("""
                (() => {
                  const subject = class binding {
                    static read() { return eval('binding'); }
                    read() { return eval('binding'); }
                  };
                  const normalize = value => value === subject ? 'class' : String(value);
                  return [normalize(subject.read()), normalize(new subject().read())].join('|');
                })()
                """, """
                (() => {
                  const subject = class binding {
                    static read() { return eval('typeof binding'); }
                    read() { return eval('typeof binding'); }
                  };
                  const normalize = value => value === subject ? 'class' : String(value);
                  return [normalize(subject.read()), normalize(new subject().read())].join('|');
                })()
                """);
    }

    @Test
    public void testDirectEvalResolvesDynamicBindings() {
        assertStringWithJavet("""
                globalThis.binding = 'global';
                Function("eval('var binding = true'); return String(eval('binding'));")()
                """, """
                globalThis.binding = 'global';
                Function("eval('var binding = true'); return String(eval('typeof binding'));")()
                """);
    }

    @Test
    public void testDirectEvalResolvesShadowingBindings() {
        assertStringWithJavet("""
                (function (binding) {
                  const capture = () => binding;
                  return (function () {
                    const results = [String(eval('binding'))];
                    results.push((function (binding) { return String(eval('binding')); })(7));
                    results.push(String(eval('binding')));
                    results.push(capture());
                    return results.join('|');
                  })();
                })('outer')
                """, """
                (function (binding) {
                  const capture = () => binding;
                  return (function () {
                    const results = [String(eval('typeof binding'))];
                    results.push((function (binding) { return String(eval('typeof binding')); })(7));
                    results.push(String(eval('typeof binding')));
                    results.push(capture());
                    return results.join('|');
                  })();
                })('outer')
                """);
    }

    @Test
    public void testGlobalGettersAndMissingNames() {
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
                  try { result = String(binding); }
                  catch (e) { result = e === marker ? 'marker' : e.name; }
                  delete globalThis.binding;
                  return mode + ':' + result + ':' + log.join(',');
                }).join('|'))()
                """, """
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
                  try { result = String(typeof binding); }
                  catch (e) { result = e === marker ? 'marker' : e.name; }
                  delete globalThis.binding;
                  return mode + ':' + result + ':' + log.join(',');
                }).join('|'))()
                """);
    }

    @Test
    public void testGlobalLexicalBindingShadowsGlobalProperty() {
        assertStringWithJavet("""
                globalThis.binding = 'property';
                let binding = 17;
                (() => String(binding) + '|' + globalThis.binding)()
                """, """
                globalThis.binding = 'property';
                let binding = 17;
                (() => String(typeof binding) + '|' + globalThis.binding)()
                """);
    }

    @Test
    public void testGlobalLexicalInitializationErrors() {
        assertStringWithJavet("""
                var before;
                try { eval('binding'); } catch (e) { before = e.name; }
                let binding = 9;
                before + '|' + String(eval('binding'));
                """, """
                var before;
                try { eval('typeof binding'); } catch (e) { before = e.name; }
                let binding = 9;
                before + '|' + String(eval('typeof binding'));
                """);
    }

    @Test
    public void testMissingCallDiagnosticRetainsPropertyPath() {
        assertErrorWithJavet("""
                globalThis.holder = { nested: { invoke: 0 } };
                holder.nested.invoke(typeof missingArgument);
                """);
    }
}
