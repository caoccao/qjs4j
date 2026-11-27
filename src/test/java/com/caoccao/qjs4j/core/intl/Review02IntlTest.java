package com.caoccao.qjs4j.core.intl;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

class Review02IntlTest extends BaseJavetTest {
    @Test
    void collatorUsesNumericAndCaseOptions() {
        assertStringWithJavet(
                """
                        (() => {
                          const results = [];
                          for (const locale of ["en", "de", "fr"]) {
                            for (const options of [{numeric:true}, {caseFirst:'upper'}, {caseFirst:'lower'}, {numeric:true,caseFirst:'upper'}, {numeric:true,sensitivity:'base'}, {caseFirst:'upper',sensitivity:'accent'}]) {
                              results.push((()=>{const c=new Intl.Collator(locale,options);return JSON.stringify([
                              ['2','10'],['0002','2'],['a2','a10'],['a','A'],['aa','aA'],['a2','A10'],
                              ['a','á'],['12b','12a'],['١٢','13'],['a2','a2b'],['0','00']
                              ].map(([a,b])=>Math.sign(c.compare(a,b))))})());
                            }
                          }
                          return JSON.stringify(results);
                        })()""");
    }

    @Test
    void pluralRangesUseBothEndpoints() {
        assertStringWithJavet("""
                (() => {
                  const results = [];
                  for (const locale of ["en", "de", "fr", "ru", "ar", "ja", "sl", "cy"]) {
                    results.push((()=>{const p=new Intl.PluralRules(locale);return JSON.stringify([
                     [0,1],[1,1],[1,2],[2,3],[3,4],[4,5],[2,1],[1.001,1.002],[Infinity,Infinity]
                    ].map(([a,b])=>p.selectRange(a,b)))})());
                  }
                  return JSON.stringify(results);
                })()""");
    }

    @Test
    void pluralRulesRetainFractionAndExponentOperands() {
        assertStringWithJavet("""
                (() => {
                  const results = [];
                  for (const locale of ["fr", "pt", "it", "ca", "is", "da"]) {
                    for (const notation of ["standard", "compact", "scientific", "engineering"]) {
                      results.push(JSON.stringify([1,1.1,1.11,1.21,2.1,1000,1000000,1500000].map(n=>
                      new Intl.PluralRules(locale,{notation:notation}).select(n))));
                    }
                  }
                  return JSON.stringify(results);
                })()""");
    }

    @Test
    void pluralRulesUseLocaleRulesAndVisibleFractionDigits() {
        assertStringWithJavet(
                """
                        (() => {
                          const results = [];
                          for (const locale of ["en", "ru", "ja", "fr", "ar", "pl", "sl", "cy", "lv", "pt-PT", "pt", "fa", "gv"]) {
                            for (const options of [{}, {type:'ordinal'}, {minimumFractionDigits:1}, {minimumSignificantDigits:2}, {maximumFractionDigits:0}]) {
                              results.push((()=>{const p=new Intl.PluralRules(locale,options);return JSON.stringify([
                                p.resolvedOptions().pluralCategories,
                                [0,1,2,3,4,5,7,8,10,11,12,13,21,22,101,1000000,0.1,1.1,1.01,2.1,3.14,-1,NaN,Infinity].map(x=>p.select(x))
                              ])})());
                            }
                          }
                          return JSON.stringify(results);
                        })()""");
    }

    @Test
    void segmentBoundariesAndContainingMatchV8() {
        assertStringWithJavet(
                """
                        (() => {
                          const results = [];
                          for (const text of ['a\\u200Db', '👩‍👩‍👧‍👦a', 'e\\u0301a', 'hello world', '🇺🇸🇩🇪', '각', '']) {
                            for (const granularity of ["grapheme", "word", "sentence"]) {
                              results.push((()=>{const s=new Intl.Segmenter('en',{granularity:granularity}).segment(text);
                              const it=s[Symbol.iterator]();const first=it.next();const containing=[];
                              for(let i=text.length-1;i>=-1;i--)containing.push(s.containing(i));
                              return JSON.stringify([first,containing,Array.from(s),Array.from(it),s.containing(Infinity)]);})());
                            }
                          }
                          return JSON.stringify(results);
                        })()""");
    }
}
