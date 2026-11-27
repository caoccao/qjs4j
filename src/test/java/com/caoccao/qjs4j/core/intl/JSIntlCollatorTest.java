package com.caoccao.qjs4j.core.intl;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JSIntlCollatorTest extends BaseJavetTest {
    private void assertComparisons(String locale, String options, String pairs) {
        assertStringWithJavet("""
                (() => {
                    const collator = new Intl.Collator('%s', %s);
                    return JSON.stringify([%s].map(([a, b]) => Math.sign(collator.compare(a, b))));
                })()
                """.formatted(locale, options, pairs));
    }

    @Test
    void caseFoldingFollowsTheLocale() {
        // The JDK has no Azeri tailoring. Lithuanian accent folding has dedicated coverage below.
        for (String locale : new String[]{"tr", "en", "de"}) {
            for (String options : new String[]{"{sensitivity:'accent'}", "{sensitivity:'base'}",
                    "{sensitivity:'variant'}", "{numeric:true,sensitivity:'accent'}", "{numeric:true}",
                    "{sensitivity:'case'}"}) {
                assertComparisons(locale, options,
                        """
                                ['I','ı'],['İ','i'],['I','i'],['ı','I'],['İ','I'],['I1','ı1'],['a1I','A1ı'],['a1I','A1i'],['ş','s'],['Iı','ıI'],['iİ','İi']
                                """);
            }
        }
    }

    @Test
    void collatorKeepsDigitVariantsApartOnlyAtVariantSensitivity() {
        for (String options : new String[]{"{}", "{sensitivity:'variant'}", "{sensitivity:'base'}",
                "{sensitivity:'accent'}", "{sensitivity:'case'}", "{numeric:true}",
                "{numeric:true,sensitivity:'variant'}", "{numeric:false,sensitivity:'variant'}"}) {
            assertComparisons("en", options, """
                    ['1','１'],['１','1'],['1','𝟏'],['１','𝟏'],['1','١'],['a1','١'],['a1','１'],['１a','1b'],
                    ['a１０','a9'],['a١٠','a9'],['01','1'],['x１y','x1z'],['１','𝟏𝟏'],['b１','a2'],['1１','１1']
                    """);
        }
    }

    @Test
    void digitWidthAndLetterCaseWeighTogetherInCharacterOrder() {
        for (String locale : new String[]{"en", "da"}) {
            for (String options : new String[]{"{}", "{sensitivity:'variant'}",
                    "{sensitivity:'variant',caseFirst:'lower'}", "{sensitivity:'variant',caseFirst:'upper'}",
                    "{sensitivity:'case'}", "{sensitivity:'base'}", "{sensitivity:'accent'}",
                    "{numeric:true,sensitivity:'variant'}", "{caseFirst:'upper'}"}) {
                assertComparisons(locale, options, """
                        ['1A','１a'],['A1','a１'],['a1','A１'],['１A','1a'],['x1y','x１Y'],['é1','e１'],['e1','é１'],
                        ['1a','１A'],['Ab１','ab1'],['a\u200B1','a１'],['1A','1a'],['１','1'],['bb1','bB１'],['B1','b１']
                        """);
            }
        }
    }

    @Test
    void forwardFrenchCollatorsShareOneCachedRuleSet() {
        JSContext context = resetContext();
        for (String tag : new String[]{"fr", "fr-FR", "fr-BE", "fr-x-a", "fr-x-b", "fr-x-c", "fr-CA"}) {
            assertThat(context.eval("new Intl.Collator('" + tag + "').compare('éa','eá')").toString())
                    .isEqualTo("fr-CA".equals(tag) ? "-1" : "1");
        }
        assertThat(JSIntlCollator.FORWARD_FRENCH_COLLATORS).hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void ignorableCharactersEndDigitRunsButCarryNoWeight() {
        for (String options : new String[]{"{numeric:true}", "{numeric:true,sensitivity:'base'}", "{numeric:false}",
                "{numeric:true,caseFirst:'upper'}"}) {
            assertComparisons("en", options,
                    """
                            ['1\u200B2','12'],['12','1\u200B2'],['1\u200B','1'],['1\u200B2','1\u200B2'],['a\u200Bb1','ab1'],
                            ['1\u200B2','1x2'],['1\u00AD2','12'],['1\u00012','12'],['\u200B1','1'],['1\u200B2','2'],
                            ['1\u200B2','1\u200B3'],['10\u200B2','9\u200B2'],['1\u200B2','1\u200B'],['a\u200B','a'],['\u200B','']
                            """);
        }
    }

    @Test
    void lithuanianAccentsSurviveCaseFolding() {
        for (String options : new String[]{"{sensitivity:'accent'}", "{sensitivity:'base'}", "{sensitivity:'variant'}",
                "{numeric:true,sensitivity:'accent'}", "{sensitivity:'case'}"}) {
            assertComparisons("lt", options,
                    """
                            ['Í','í'],['Ì','ì'],['Į','į'],['Ė','ė'],['Í','i'],['I','í'],['Į1','į1'],['Ĩ','ĩ'],['J','j'],['Ž','ž']
                            """);
        }
    }

    @Test
    void numericCaseSensitivityIgnoresAccentsAndTrailingMarks() {
        for (String locale : new String[]{"en", "de", "fr-CA"}) {
            for (String options : new String[]{"{numeric:true,sensitivity:'case',caseFirst:'upper'}",
                    "{numeric:true,sensitivity:'case'}", "{numeric:true,sensitivity:'base'}",
                    "{numeric:true,sensitivity:'accent'}", "{numeric:true,sensitivity:'variant'}",
                    "{numeric:true,caseFirst:'upper'}", "{numeric:true,sensitivity:'case',caseFirst:'lower'}"}) {
                assertComparisons(locale, options,
                        """
                                ['a1Á','A1a'],['a1á','A1a'],['A1a','a1á'],['1\u0301','1'],['1','1\u0301'],['a1\u0301','a1'],
                                ['1e','1é'],['é1','e1'],['a1','a1a'],['a1a','a1'],['1','1a'],['1\u0301a','1a'],['a1A','A1a\u0301']
                                """);
            }
        }
    }

    @Test
    void numericCollationComparesAccentsBeforeCase() {
        for (String options : new String[]{"{numeric:true,caseFirst:'upper',sensitivity:'variant'}",
                "{numeric:true,caseFirst:'lower',sensitivity:'variant'}", "{numeric:true,sensitivity:'variant'}",
                "{numeric:true,sensitivity:'base'}", "{numeric:true,sensitivity:'accent'}",
                "{numeric:true,sensitivity:'case'}", "{numeric:true,caseFirst:'upper'}",
                "{caseFirst:'upper',sensitivity:'variant'}"}) {
            assertComparisons("en", options,
                    """
                            ['a1a','A1á'],['A1á','a1a'],['a1a','A1a'],['a1á','A1a'],['a1b','A1a'],['aa','Aá'],['a1A2a','A1a2á'],
                            ['á1a','a1A'],['A1a','a1á'],['a1','A1a'],['a2','A10'],['x1y','x1Y'],['x1y','x1ý'],['a1a1a','A1A1á'],['é1a','e1A']
                            """);
        }
    }

    @Test
    void numericCollationFollowsTheLocalesAccentDirection() {
        for (String locale : new String[]{"fr-CA", "fr", "fr-FR", "de", "en"}) {
            for (String options : new String[]{"{numeric:true,sensitivity:'accent'}",
                    "{numeric:true,sensitivity:'variant'}", "{numeric:true,sensitivity:'base'}",
                    "{numeric:true,caseFirst:'upper'}", "{numeric:true}", "{}", "{sensitivity:'variant'}",
                    "{sensitivity:'accent'}"}) {
                assertComparisons(locale, options,
                        """
                                ['é1e','e1é'],['e1é','é1e'],['é1e2a','e1é2a'],['a1E','A1é'],['é1E','e1e'],['cote1','côte1'],
                                ['côte1coté','cote1côté'],['éa','eá'],['côte','coté'],['cote','côte'],['e1e','é1é'],['e10é','é9e'],
                                ['Côte2','côte10'],['é1a','e1b']
                                """);
            }
        }
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
        for (String options : new String[]{"{sensitivity:'base'}", "{sensitivity:'accent'}", "{sensitivity:'case'}",
                "{sensitivity:'variant'}", "{numeric:true,sensitivity:'base'}", "{numeric:true,sensitivity:'accent'}",
                "{numeric:true,sensitivity:'case'}", "{numeric:true}"}) {
            assertStringWithJavet("""
                    (() => {
                        const collator = new Intl.Collator('tr', %s);
                        return JSON.stringify([
                            ['İ', 'I\\u0307'], ['I\\u0307', 'i'], ['I\\u0307', 'ı'],
                            ['I\\u0323\\u0307', 'i\\u0323'], ['İ1', 'I\\u03071'], ['I1', 'ı1'],
                            ['a1I\\u0307', 'a1i'], ['I\\u0301', 'ı\\u0301']
                        ].map(([a, b]) => Math.sign(collator.compare(a, b))));
                    })()
                    """.formatted(options));
        }
    }
}
