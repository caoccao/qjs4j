package com.caoccao.qjs4j.core.intl;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

class Review02IntlTest extends BaseJavetTest {
    @Test
    void collatorUsesNumericAndCaseOptions() {
        for (String locale : new String[]{"en", "de", "fr"}) {
            for (String options : new String[]{"{numeric:true}", "{caseFirst:'upper'}", "{caseFirst:'lower'}",
                    "{numeric:true,caseFirst:'upper'}", "{numeric:true,sensitivity:'base'}",
                    "{caseFirst:'upper',sensitivity:'accent'}"}) {
                assertStringWithJavet("""
                        (()=>{const c=new Intl.Collator('%s',%s);return JSON.stringify([
                        ['2','10'],['0002','2'],['a2','a10'],['a','A'],['aa','aA'],['a2','A10'],
                        ['a','á'],['12b','12a'],['١٢','13'],['a2','a2b'],['0','00']
                        ].map(([a,b])=>Math.sign(c.compare(a,b))))})()
                        """.formatted(locale, options));
            }
        }
    }

    @Test
    void pluralRangesUseBothEndpoints() {
        for (String locale : new String[]{"en", "de", "fr", "ru", "ar", "ja", "sl", "cy"}) {
            assertStringWithJavet("""
                    (()=>{const p=new Intl.PluralRules('%s');return JSON.stringify([
                     [0,1],[1,1],[1,2],[2,3],[3,4],[4,5],[2,1],[1.001,1.002],[Infinity,Infinity]
                    ].map(([a,b])=>p.selectRange(a,b)))})()
                    """.formatted(locale));
        }
    }

    @Test
    void pluralRulesRetainFractionAndExponentOperands() {
        for (String locale : new String[]{"fr", "pt", "it", "ca", "is", "da"}) {
            for (String notation : new String[]{"standard", "compact", "scientific", "engineering"}) {
                assertStringWithJavet("""
                        JSON.stringify([1,1.1,1.11,1.21,2.1,1000,1000000,1500000].map(n=>
                        new Intl.PluralRules('%s',{notation:'%s'}).select(n)))
                        """.formatted(locale, notation));
            }
        }
    }

    @Test
    void pluralRulesUseLocaleRulesAndVisibleFractionDigits() {
        for (String locale : new String[]{"en", "ru", "ja", "fr", "ar", "pl", "sl", "cy", "lv", "pt-PT", "pt", "fa",
                "gv"}) {
            for (String options : new String[]{"{}", "{type:'ordinal'}", "{minimumFractionDigits:1}",
                    "{minimumSignificantDigits:2}", "{maximumFractionDigits:0}"}) {
                assertStringWithJavet(
                        """
                                (()=>{const p=new Intl.PluralRules('%s',%s);return JSON.stringify([
                                  p.resolvedOptions().pluralCategories,
                                  [0,1,2,3,4,5,7,8,10,11,12,13,21,22,101,1000000,0.1,1.1,1.01,2.1,3.14,-1,NaN,Infinity].map(x=>p.select(x))
                                ])})()
                                """
                                .formatted(locale, options));
            }
        }
    }

    @Test
    void segmentBoundariesAndContainingMatchV8() {
        for (String text : new String[]{"a\\u200Db", "👩‍👩‍👧‍👦a", "e\\u0301a", "hello world", "🇺🇸🇩🇪", "각",
                ""}) {
            for (String granularity : new String[]{"grapheme", "word", "sentence"}) {
                assertStringWithJavet(
                        """
                                (()=>{const text='%s';const s=new Intl.Segmenter('en',{granularity:'%s'}).segment(text);
                                const it=s[Symbol.iterator]();const first=it.next();const containing=[];
                                for(let i=text.length-1;i>=-1;i--)containing.push(s.containing(i));
                                return JSON.stringify([first,containing,Array.from(s),Array.from(it),s.containing(Infinity)]);})()
                                """
                                .formatted(text, granularity));
            }
        }
    }
}
