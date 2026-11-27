package com.caoccao.qjs4j.core.intl;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JSIntlCollatorTest extends BaseJavetTest {

    @Test
    void caseFoldingFollowsTheLocale() {
        // The JDK has no Azeri tailoring. Lithuanian accent folding has dedicated coverage below.
        assertStringWithJavet(
                """
                        (() => {
                          const locales = ["tr", "en", "de"];
                          const options = [
                            {sensitivity:'accent'},
                            {sensitivity:'base'},
                            {sensitivity:'variant'},
                            {numeric:true,sensitivity:'accent'},
                            {numeric:true},
                            {sensitivity:'case'}
                          ];
                          const pairs = [
                            ['I','ı'],['İ','i'],['I','i'],['ı','I'],['İ','I'],['I1','ı1'],['a1I','A1ı'],['a1I','A1i'],['ş','s'],['Iı','ıI'],['iİ','İi']
                          ];
                          return JSON.stringify(locales.map(locale => options.map(option => {
                            const collator = new Intl.Collator(locale, option);
                            return pairs.map(([a, b]) => Math.sign(collator.compare(a, b)));
                          })));
                        })()""");
    }

    @Test
    void collatorKeepsDigitVariantsApartOnlyAtVariantSensitivity() {
        assertStringWithJavet("""
                (() => {
                  const locales = ["en"];
                  const options = [
                    {},
                    {sensitivity:'variant'},
                    {sensitivity:'base'},
                    {sensitivity:'accent'},
                    {sensitivity:'case'},
                    {numeric:true},
                    {numeric:true,sensitivity:'variant'},
                    {numeric:false,sensitivity:'variant'}
                  ];
                  const pairs = [
                    ['1','１'],['１','1'],['1','𝟏'],['１','𝟏'],['1','١'],['a1','١'],['a1','１'],['１a','1b'],
                    ['a１０','a9'],['a١٠','a9'],['01','1'],['x１y','x1z'],['１','𝟏𝟏'],['b１','a2'],['1１','１1']
                  ];
                  return JSON.stringify(locales.map(locale => options.map(option => {
                    const collator = new Intl.Collator(locale, option);
                    return pairs.map(([a, b]) => Math.sign(collator.compare(a, b)));
                  })));
                })()""");
    }

    @Test
    void digitWidthAndLetterCaseWeighTogetherInCharacterOrder() {
        assertStringWithJavet("""
                (() => {
                  const locales = ["en", "da"];
                  const options = [
                    {},
                    {sensitivity:'variant'},
                    {sensitivity:'variant',caseFirst:'lower'},
                    {sensitivity:'variant',caseFirst:'upper'},
                    {sensitivity:'case'},
                    {sensitivity:'base'},
                    {sensitivity:'accent'},
                    {numeric:true,sensitivity:'variant'},
                    {caseFirst:'upper'}
                  ];
                  const pairs = [
                    ['1A','１a'],['A1','a１'],['a1','A１'],['１A','1a'],['x1y','x１Y'],['é1','e１'],['e1','é１'],
                    ['1a','１A'],['Ab１','ab1'],['a​1','a１'],['1A','1a'],['１','1'],['bb1','bB１'],['B1','b１']
                  ];
                  return JSON.stringify(locales.map(locale => options.map(option => {
                    const collator = new Intl.Collator(locale, option);
                    return pairs.map(([a, b]) => Math.sign(collator.compare(a, b)));
                  })));
                })()""");
    }

    @Test
    void forwardFrenchCollatorsShareOneCachedRuleSet() {
        assertThat(resetContext().eval("""
                ['fr', 'fr-FR', 'fr-BE', 'fr-x-a', 'fr-x-b', 'fr-x-c', 'fr-CA']
                  .map(tag => new Intl.Collator(tag).compare('éa', 'eá')).join(',')
                """).toString()).isEqualTo("1,1,1,1,1,1,-1");
        assertThat(JSIntlCollator.FORWARD_FRENCH_COLLATORS).hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void ignorableCharactersEndDigitRunsButCarryNoWeight() {
        assertStringWithJavet("""
                (() => {
                  const locales = ["en"];
                  const options = [
                    {numeric:true},
                    {numeric:true,sensitivity:'base'},
                    {numeric:false},
                    {numeric:true,caseFirst:'upper'}
                  ];
                  const pairs = [
                    ['1​2','12'],['12','1​2'],['1​','1'],['1​2','1​2'],['a​b1','ab1'],
                    ['1​2','1x2'],['1­2','12'],['12','12'],['​1','1'],['1​2','2'],
                    ['1​2','1​3'],['10​2','9​2'],['1​2','1​'],['a​','a'],['​','']
                  ];
                  return JSON.stringify(locales.map(locale => options.map(option => {
                    const collator = new Intl.Collator(locale, option);
                    return pairs.map(([a, b]) => Math.sign(collator.compare(a, b)));
                  })));
                })()""");
    }

    @Test
    void lithuanianAccentsSurviveCaseFolding() {
        assertStringWithJavet(
                """
                        (() => {
                          const locales = ["lt"];
                          const options = [
                            {sensitivity:'accent'},
                            {sensitivity:'base'},
                            {sensitivity:'variant'},
                            {numeric:true,sensitivity:'accent'},
                            {sensitivity:'case'}
                          ];
                          const pairs = [
                            ['Í','í'],['Ì','ì'],['Į','į'],['Ė','ė'],['Í','i'],['I','í'],['Į1','į1'],['Ĩ','ĩ'],['J','j'],['Ž','ž']
                          ];
                          return JSON.stringify(locales.map(locale => options.map(option => {
                            const collator = new Intl.Collator(locale, option);
                            return pairs.map(([a, b]) => Math.sign(collator.compare(a, b)));
                          })));
                        })()""");
    }

    @Test
    void numericCaseSensitivityIgnoresAccentsAndTrailingMarks() {
        assertStringWithJavet("""
                (() => {
                  const locales = ["en", "de", "fr-CA"];
                  const options = [
                    {numeric:true,sensitivity:'case',caseFirst:'upper'},
                    {numeric:true,sensitivity:'case'},
                    {numeric:true,sensitivity:'base'},
                    {numeric:true,sensitivity:'accent'},
                    {numeric:true,sensitivity:'variant'},
                    {numeric:true,caseFirst:'upper'},
                    {numeric:true,sensitivity:'case',caseFirst:'lower'}
                  ];
                  const pairs = [
                    ['a1Á','A1a'],['a1á','A1a'],['A1a','a1á'],['1́','1'],['1','1́'],['a1́','a1'],
                    ['1e','1é'],['é1','e1'],['a1','a1a'],['a1a','a1'],['1','1a'],['1́a','1a'],['a1A','A1á']
                  ];
                  return JSON.stringify(locales.map(locale => options.map(option => {
                    const collator = new Intl.Collator(locale, option);
                    return pairs.map(([a, b]) => Math.sign(collator.compare(a, b)));
                  })));
                })()""");
    }

    @Test
    void numericCollationComparesAccentsBeforeCase() {
        assertStringWithJavet(
                """
                        (() => {
                          const locales = ["en"];
                          const options = [
                            {numeric:true,caseFirst:'upper',sensitivity:'variant'},
                            {numeric:true,caseFirst:'lower',sensitivity:'variant'},
                            {numeric:true,sensitivity:'variant'},
                            {numeric:true,sensitivity:'base'},
                            {numeric:true,sensitivity:'accent'},
                            {numeric:true,sensitivity:'case'},
                            {numeric:true,caseFirst:'upper'},
                            {caseFirst:'upper',sensitivity:'variant'}
                          ];
                          const pairs = [
                            ['a1a','A1á'],['A1á','a1a'],['a1a','A1a'],['a1á','A1a'],['a1b','A1a'],['aa','Aá'],['a1A2a','A1a2á'],
                            ['á1a','a1A'],['A1a','a1á'],['a1','A1a'],['a2','A10'],['x1y','x1Y'],['x1y','x1ý'],['a1a1a','A1A1á'],['é1a','e1A']
                          ];
                          return JSON.stringify(locales.map(locale => options.map(option => {
                            const collator = new Intl.Collator(locale, option);
                            return pairs.map(([a, b]) => Math.sign(collator.compare(a, b)));
                          })));
                        })()""");
    }

    @Test
    void numericCollationFollowsTheLocalesAccentDirection() {
        assertStringWithJavet("""
                (() => {
                  const locales = ["fr-CA", "fr", "fr-FR", "de", "en"];
                  const options = [
                    {numeric:true,sensitivity:'accent'},
                    {numeric:true,sensitivity:'variant'},
                    {numeric:true,sensitivity:'base'},
                    {numeric:true,caseFirst:'upper'},
                    {numeric:true},
                    {},
                    {sensitivity:'variant'},
                    {sensitivity:'accent'}
                  ];
                  const pairs = [
                    ['é1e','e1é'],['e1é','é1e'],['é1e2a','e1é2a'],['a1E','A1é'],['é1E','e1e'],['cote1','côte1'],
                    ['côte1coté','cote1côté'],['éa','eá'],['côte','coté'],['cote','côte'],['e1e','é1é'],['e10é','é9e'],
                    ['Côte2','côte10'],['é1a','e1b']
                  ];
                  return JSON.stringify(locales.map(locale => options.map(option => {
                    const collator = new Intl.Collator(locale, option);
                    return pairs.map(([a, b]) => Math.sign(collator.compare(a, b)));
                  })));
                })()""");
    }

    @Test
    void reviewRegressionsCompareWithV8() {
        assertIntegerWithJavet(
                "new Intl.Collator('en', {numeric:true, sensitivity:'case', caseFirst:'upper'}).compare('a1Á','A1a')",
                "new Intl.Collator('en', {numeric:true, sensitivity:'case'}).compare('1\\u0301','1')",
                "new Intl.Collator('lt', {sensitivity:'accent'}).compare('Í','í')");
    }

    @Test
    void turkishCaseFoldingPreservesCanonicalEquivalence() {
        assertStringWithJavet("""
                (() => {
                    const collator = new Intl.Collator('tr', {sensitivity:'base'});
                    return JSON.stringify([
                        ['İ', 'I\\u0307'], ['I\\u0307', 'i'], ['I\\u0307', 'ı'],
                        ['I\\u0323\\u0307', 'i\\u0323'], ['İ1', 'I\\u03071'], ['I1', 'ı1'],
                        ['a1I\\u0307', 'a1i'], ['I\\u0301', 'ı\\u0301']
                    ].map(([a, b]) => Math.sign(collator.compare(a, b))));
                })()
                """, """
                (() => {
                    const collator = new Intl.Collator('tr', {sensitivity:'accent'});
                    return JSON.stringify([
                        ['İ', 'I\\u0307'], ['I\\u0307', 'i'], ['I\\u0307', 'ı'],
                        ['I\\u0323\\u0307', 'i\\u0323'], ['İ1', 'I\\u03071'], ['I1', 'ı1'],
                        ['a1I\\u0307', 'a1i'], ['I\\u0301', 'ı\\u0301']
                    ].map(([a, b]) => Math.sign(collator.compare(a, b))));
                })()
                """, """
                (() => {
                    const collator = new Intl.Collator('tr', {sensitivity:'case'});
                    return JSON.stringify([
                        ['İ', 'I\\u0307'], ['I\\u0307', 'i'], ['I\\u0307', 'ı'],
                        ['I\\u0323\\u0307', 'i\\u0323'], ['İ1', 'I\\u03071'], ['I1', 'ı1'],
                        ['a1I\\u0307', 'a1i'], ['I\\u0301', 'ı\\u0301']
                    ].map(([a, b]) => Math.sign(collator.compare(a, b))));
                })()
                """, """
                (() => {
                    const collator = new Intl.Collator('tr', {sensitivity:'variant'});
                    return JSON.stringify([
                        ['İ', 'I\\u0307'], ['I\\u0307', 'i'], ['I\\u0307', 'ı'],
                        ['I\\u0323\\u0307', 'i\\u0323'], ['İ1', 'I\\u03071'], ['I1', 'ı1'],
                        ['a1I\\u0307', 'a1i'], ['I\\u0301', 'ı\\u0301']
                    ].map(([a, b]) => Math.sign(collator.compare(a, b))));
                })()
                """, """
                (() => {
                    const collator = new Intl.Collator('tr', {numeric:true,sensitivity:'base'});
                    return JSON.stringify([
                        ['İ', 'I\\u0307'], ['I\\u0307', 'i'], ['I\\u0307', 'ı'],
                        ['I\\u0323\\u0307', 'i\\u0323'], ['İ1', 'I\\u03071'], ['I1', 'ı1'],
                        ['a1I\\u0307', 'a1i'], ['I\\u0301', 'ı\\u0301']
                    ].map(([a, b]) => Math.sign(collator.compare(a, b))));
                })()
                """, """
                (() => {
                    const collator = new Intl.Collator('tr', {numeric:true,sensitivity:'accent'});
                    return JSON.stringify([
                        ['İ', 'I\\u0307'], ['I\\u0307', 'i'], ['I\\u0307', 'ı'],
                        ['I\\u0323\\u0307', 'i\\u0323'], ['İ1', 'I\\u03071'], ['I1', 'ı1'],
                        ['a1I\\u0307', 'a1i'], ['I\\u0301', 'ı\\u0301']
                    ].map(([a, b]) => Math.sign(collator.compare(a, b))));
                })()
                """, """
                (() => {
                    const collator = new Intl.Collator('tr', {numeric:true,sensitivity:'case'});
                    return JSON.stringify([
                        ['İ', 'I\\u0307'], ['I\\u0307', 'i'], ['I\\u0307', 'ı'],
                        ['I\\u0323\\u0307', 'i\\u0323'], ['İ1', 'I\\u03071'], ['I1', 'ı1'],
                        ['a1I\\u0307', 'a1i'], ['I\\u0301', 'ı\\u0301']
                    ].map(([a, b]) => Math.sign(collator.compare(a, b))));
                })()
                """, """
                (() => {
                    const collator = new Intl.Collator('tr', {numeric:true});
                    return JSON.stringify([
                        ['İ', 'I\\u0307'], ['I\\u0307', 'i'], ['I\\u0307', 'ı'],
                        ['I\\u0323\\u0307', 'i\\u0323'], ['İ1', 'I\\u03071'], ['I1', 'ı1'],
                        ['a1I\\u0307', 'a1i'], ['I\\u0301', 'ı\\u0301']
                    ].map(([a, b]) => Math.sign(collator.compare(a, b))));
                })()
                """);
    }
}
