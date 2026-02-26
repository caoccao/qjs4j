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

package com.caoccao.qjs4j.unicode;

/**
 * Unicode property tables ported from QuickJS libunicode-table.h.
 * These tables use compressed run-length encoding for code point ranges.
 * Property byte arrays are split across helper classes to stay within
 * Java bytecode size limits.
 */
public final class UnicodePropertyTables {

    // Case conversion table from QuickJS libunicode-table.h
    // Each entry: code(17 bits) | len(7 bits) | type(4 bits) | data(4 bits)
    public static final int[] CASE_CONV_TABLE1 = {
            0x00209a30, 0x00309a00, 0x005a8173, 0x00601730,
            0x006c0730, 0x006f81b3, 0x00701700, 0x007c0700,
            0x007f8100, 0x00803040, 0x009801c3, 0x00988190,
            0x00990640, 0x009c9040, 0x00a481b4, 0x00a52e40,
            0x00bc0130, 0x00bc8640, 0x00bf8170, 0x00c00100,
            0x00c08130, 0x00c10440, 0x00c30130, 0x00c38240,
            0x00c48230, 0x00c58240, 0x00c70130, 0x00c78130,
            0x00c80130, 0x00c88240, 0x00c98130, 0x00ca0130,
            0x00ca8100, 0x00cb0130, 0x00cb8130, 0x00cc0240,
            0x00cd0100, 0x00cd8101, 0x00ce0130, 0x00ce8130,
            0x00cf0100, 0x00cf8130, 0x00d00640, 0x00d30130,
            0x00d38240, 0x00d48130, 0x00d60240, 0x00d70130,
            0x00d78240, 0x00d88230, 0x00d98440, 0x00db8130,
            0x00dc0240, 0x00de0240, 0x00df8100, 0x00e20350,
            0x00e38350, 0x00e50350, 0x00e69040, 0x00ee8100,
            0x00ef1240, 0x00f801b4, 0x00f88350, 0x00fa0240,
            0x00fb0130, 0x00fb8130, 0x00fc2840, 0x01100130,
            0x01111240, 0x011d0131, 0x011d8240, 0x011e8130,
            0x011f0131, 0x011f8201, 0x01208240, 0x01218130,
            0x01220130, 0x01228130, 0x01230a40, 0x01280101,
            0x01288101, 0x01290101, 0x01298100, 0x012a0100,
            0x012b0200, 0x012c8100, 0x012d8100, 0x012e0101,
            0x01300100, 0x01308101, 0x01318100, 0x01320101,
            0x01328101, 0x01330101, 0x01340100, 0x01348100,
            0x01350101, 0x01358101, 0x01360101, 0x01378100,
            0x01388101, 0x01390100, 0x013a8100, 0x013e8101,
            0x01400100, 0x01410101, 0x01418100, 0x01438101,
            0x01440100, 0x01448100, 0x01450200, 0x01460100,
            0x01490100, 0x014e8101, 0x014f0101, 0x01a28173,
            0x01b80440, 0x01bb0240, 0x01bd8300, 0x01bf8130,
            0x01c30130, 0x01c40330, 0x01c60130, 0x01c70230,
            0x01c801d0, 0x01c89130, 0x01d18930, 0x01d60100,
            0x01d68300, 0x01d801d3, 0x01d89100, 0x01e10173,
            0x01e18900, 0x01e60100, 0x01e68200, 0x01e78130,
            0x01e80173, 0x01e88173, 0x01ea8173, 0x01eb0173,
            0x01eb8100, 0x01ec1840, 0x01f80173, 0x01f88173,
            0x01f90100, 0x01f98100, 0x01fa01a0, 0x01fa8173,
            0x01fb8240, 0x01fc8130, 0x01fd0240, 0x01fe8330,
            0x02001030, 0x02082030, 0x02182000, 0x02281000,
            0x02302240, 0x02453640, 0x02600130, 0x02608e40,
            0x02678100, 0x02686040, 0x0298a630, 0x02b0a600,
            0x02c381b5, 0x08502631, 0x08638131, 0x08668131,
            0x08682b00, 0x087e8300, 0x09d05011, 0x09f80610,
            0x09fc0620, 0x0e400174, 0x0e408174, 0x0e410174,
            0x0e418174, 0x0e420174, 0x0e428174, 0x0e430174,
            0x0e438180, 0x0e440180, 0x0e448240, 0x0e482b30,
            0x0e5e8330, 0x0ebc8101, 0x0ebe8101, 0x0ec70101,
            0x0f007e40, 0x0f3f1840, 0x0f4b01b5, 0x0f4b81b6,
            0x0f4c01b6, 0x0f4c81b6, 0x0f4d01b7, 0x0f4d8180,
            0x0f4f0130, 0x0f506040, 0x0f800800, 0x0f840830,
            0x0f880600, 0x0f8c0630, 0x0f900800, 0x0f940830,
            0x0f980800, 0x0f9c0830, 0x0fa00600, 0x0fa40630,
            0x0fa801b0, 0x0fa88100, 0x0fa901d3, 0x0fa98100,
            0x0faa01d3, 0x0faa8100, 0x0fab01d3, 0x0fab8100,
            0x0fac8130, 0x0fad8130, 0x0fae8130, 0x0faf8130,
            0x0fb00800, 0x0fb40830, 0x0fb80200, 0x0fb90400,
            0x0fbb0201, 0x0fbc0201, 0x0fbd0201, 0x0fbe0201,
            0x0fc008b7, 0x0fc40867, 0x0fc808b8, 0x0fcc0868,
            0x0fd008b8, 0x0fd40868, 0x0fd80200, 0x0fd901b9,
            0x0fd981b1, 0x0fda01b9, 0x0fdb01b1, 0x0fdb81d7,
            0x0fdc0230, 0x0fdd0230, 0x0fde0161, 0x0fdf0173,
            0x0fe101b9, 0x0fe181b2, 0x0fe201ba, 0x0fe301b2,
            0x0fe381d8, 0x0fe40430, 0x0fe60162, 0x0fe80201,
            0x0fe901d0, 0x0fe981d0, 0x0feb01b0, 0x0feb81d0,
            0x0fec0230, 0x0fed0230, 0x0ff00201, 0x0ff101d3,
            0x0ff181d3, 0x0ff201ba, 0x0ff28101, 0x0ff301b0,
            0x0ff381d3, 0x0ff40231, 0x0ff50230, 0x0ff60131,
            0x0ff901ba, 0x0ff981b2, 0x0ffa01bb, 0x0ffb01b2,
            0x0ffb81d9, 0x0ffc0230, 0x0ffd0230, 0x0ffe0162,
            0x109301a0, 0x109501a0, 0x109581a0, 0x10990131,
            0x10a70101, 0x10b01031, 0x10b81001, 0x10c18240,
            0x125b1a31, 0x12681a01, 0x16003031, 0x16183001,
            0x16300240, 0x16310130, 0x16318130, 0x16320130,
            0x16328100, 0x16330100, 0x16338640, 0x16368130,
            0x16370130, 0x16378130, 0x16380130, 0x16390240,
            0x163a8240, 0x163f0230, 0x16406440, 0x16758440,
            0x16790240, 0x16802600, 0x16938100, 0x16968100,
            0x53202e40, 0x53401c40, 0x53910e40, 0x53993e40,
            0x53bc8440, 0x53be8130, 0x53bf0a40, 0x53c58240,
            0x53c68130, 0x53c80440, 0x53ca0101, 0x53cb1440,
            0x53d50130, 0x53d58130, 0x53d60130, 0x53d68130,
            0x53d70130, 0x53d80130, 0x53d88130, 0x53d90130,
            0x53d98131, 0x53da1040, 0x53e20131, 0x53e28130,
            0x53e30130, 0x53e38440, 0x53e58130, 0x53e61040,
            0x53ee0130, 0x53fa8240, 0x55a98101, 0x55b85020,
            0x7d8001b2, 0x7d8081b2, 0x7d8101b2, 0x7d8181da,
            0x7d8201da, 0x7d8281b3, 0x7d8301b3, 0x7d8981bb,
            0x7d8a01bb, 0x7d8a81bb, 0x7d8b01bc, 0x7d8b81bb,
            0x7f909a31, 0x7fa09a01, 0x82002831, 0x82142801,
            0x82582431, 0x826c2401, 0x82b80b31, 0x82be0f31,
            0x82c60731, 0x82ca0231, 0x82cb8b01, 0x82d18f01,
            0x82d98701, 0x82dd8201, 0x86403331, 0x86603301,
            0x86a81631, 0x86b81601, 0x8c502031, 0x8c602001,
            0xb7202031, 0xb7302001, 0xb7501931, 0xb75d9901,
            0xf4802231, 0xf4912201,
    };
    public static final int CASE_F = 4;
    public static final int CASE_L = 2;
    // Case mask constants
    public static final int CASE_U = 1;
    public static final int GC_C = 37;
    public static final int GC_CC = 26;
    public static final int GC_CF = 27;
    // General Category indices (matching QuickJS UnicodeGCEnum)
    public static final int GC_CN = 0;
    public static final int GC_CO = 29;
    public static final int GC_COUNT = 38;
    public static final int GC_CS = 28;
    public static final int GC_L = 31;
    public static final int GC_LC = 30;
    public static final int GC_LL = 2;
    public static final int GC_LM = 4;
    public static final int GC_LO = 5;
    public static final int GC_LT = 3;
    public static final int GC_LU = 1;
    public static final int GC_M = 32;
    public static final int GC_MC = 7;
    public static final int GC_ME = 8;
    public static final int GC_MN = 6;
    public static final int GC_N = 33;
    // General Category name table entries
    public static final String[] GC_NAME_TABLE = {
            "Cn,Unassigned",
            "Lu,Uppercase_Letter",
            "Ll,Lowercase_Letter",
            "Lt,Titlecase_Letter",
            "Lm,Modifier_Letter",
            "Lo,Other_Letter",
            "Mn,Nonspacing_Mark",
            "Mc,Spacing_Mark",
            "Me,Enclosing_Mark",
            "Nd,Decimal_Number,digit",
            "Nl,Letter_Number",
            "No,Other_Number",
            "Sm,Math_Symbol",
            "Sc,Currency_Symbol",
            "Sk,Modifier_Symbol",
            "So,Other_Symbol",
            "Pc,Connector_Punctuation",
            "Pd,Dash_Punctuation",
            "Ps,Open_Punctuation",
            "Pe,Close_Punctuation",
            "Pi,Initial_Punctuation",
            "Pf,Final_Punctuation",
            "Po,Other_Punctuation",
            "Zs,Space_Separator",
            "Zl,Line_Separator",
            "Zp,Paragraph_Separator",
            "Cc,Control,cntrl",
            "Cf,Format",
            "Cs,Surrogate",
            "Co,Private_Use",
            "LC,Cased_Letter",
            "L,Letter",
            "M,Mark,Combining_Mark",
            "N,Number",
            "S,Symbol",
            "P,Punctuation,punct",
            "Z,Separator",
            "C,Other",
    };
    public static final int GC_ND = 9;
    public static final int GC_NL = 10;
    public static final int GC_NO = 11;
    public static final int GC_P = 35;
    public static final int GC_PC = 16;
    public static final int GC_PD = 17;
    public static final int GC_PE = 19;
    public static final int GC_PF = 21;
    public static final int GC_PI = 20;
    public static final int GC_PO = 22;
    public static final int GC_PS = 18;
    public static final int GC_S = 34;
    public static final int GC_SC = 13;
    public static final int GC_SK = 14;
    public static final int GC_SM = 12;
    public static final int GC_SO = 15;
    // General Category table
    public static final byte[] GC_TABLE = UnicodePropertyTablesGC.GC_TABLE;
    public static final int GC_Z = 36;
    public static final int GC_ZL = 24;
    public static final int GC_ZP = 25;
    public static final int GC_ZS = 23;
    public static final int PROP_ASCII_HEX_DIGIT = 21;
    public static final int PROP_BASIC_EMOJI1 = 16;
    public static final int PROP_BASIC_EMOJI2 = 17;
    public static final int PROP_BIDI_CONTROL = 22;
    public static final int PROP_BIDI_MIRRORED = 47;
    public static final int PROP_CASED1 = 57;
    public static final int PROP_CASE_IGNORABLE = 56;
    public static final int PROP_CHANGES_WHEN_CASEFOLDED1 = 14;
    public static final int PROP_CHANGES_WHEN_NFKC_CASEFOLDED1 = 15;
    public static final int PROP_CHANGES_WHEN_TITLECASED1 = 13;
    public static final int PROP_DASH = 23;
    public static final int PROP_DEFAULT_IGNORABLE_CODE_POINT = 54;
    public static final int PROP_DEPRECATED = 24;
    public static final int PROP_DIACRITIC = 25;
    public static final int PROP_EMOJI = 48;
    public static final int PROP_EMOJI_COMPONENT = 49;
    public static final int PROP_EMOJI_KEYCAP_SEQUENCE = 20;
    public static final int PROP_EMOJI_MODIFIER = 50;
    public static final int PROP_EMOJI_MODIFIER_BASE = 51;
    public static final int PROP_EMOJI_PRESENTATION = 52;
    public static final int PROP_EXTENDED_PICTOGRAPHIC = 53;
    public static final int PROP_EXTENDER = 26;
    public static final int PROP_HEX_DIGIT = 27;
    // Property indices (matching QuickJS UnicodePropertyEnum)
    public static final int PROP_HYPHEN = 0;
    public static final int PROP_IDEOGRAPHIC = 31;
    public static final int PROP_IDS_BINARY_OPERATOR = 29;
    public static final int PROP_IDS_TRINARY_OPERATOR = 30;
    public static final int PROP_IDS_UNARY_OPERATOR = 28;
    public static final int PROP_ID_CONTINUE1 = 10;
    public static final int PROP_ID_START = 55;
    public static final int PROP_JOIN_CONTROL = 32;
    public static final int PROP_LOGICAL_ORDER_EXCEPTION = 33;
    public static final int PROP_MODIFIER_COMBINING_MARK = 34;
    // Property name table entries (comma-separated aliases)
    public static final String[] PROP_NAME_TABLE = {
            "ASCII_Hex_Digit,AHex",
            "Bidi_Control,Bidi_C",
            "Dash",
            "Deprecated,Dep",
            "Diacritic,Dia",
            "Extender,Ext",
            "Hex_Digit,Hex",
            "IDS_Unary_Operator,IDSU",
            "IDS_Binary_Operator,IDSB",
            "IDS_Trinary_Operator,IDST",
            "Ideographic,Ideo",
            "Join_Control,Join_C",
            "Logical_Order_Exception,LOE",
            "Modifier_Combining_Mark,MCM",
            "Noncharacter_Code_Point,NChar",
            "Pattern_Syntax,Pat_Syn",
            "Pattern_White_Space,Pat_WS",
            "Quotation_Mark,QMark",
            "Radical",
            "Regional_Indicator,RI",
            "Sentence_Terminal,STerm",
            "Soft_Dotted,SD",
            "Terminal_Punctuation,Term",
            "Unified_Ideograph,UIdeo",
            "Variation_Selector,VS",
            "White_Space,space",
            "Bidi_Mirrored,Bidi_M",
            "Emoji",
            "Emoji_Component,EComp",
            "Emoji_Modifier,EMod",
            "Emoji_Modifier_Base,EBase",
            "Emoji_Presentation,EPres",
            "Extended_Pictographic,ExtPict",
            "Default_Ignorable_Code_Point,DI",
            "ID_Start,IDS",
            "Case_Ignorable,CI",
            "ASCII",
            "Alphabetic,Alpha",
            "Any",
            "Assigned",
            "Cased",
            "Changes_When_Casefolded,CWCF",
            "Changes_When_Casemapped,CWCM",
            "Changes_When_Lowercased,CWL",
            "Changes_When_NFKC_Casefolded,CWKCF",
            "Changes_When_Titlecased,CWT",
            "Changes_When_Uppercased,CWU",
            "Grapheme_Base,Gr_Base",
            "Grapheme_Extend,Gr_Ext",
            "ID_Continue,IDC",
            "ID_Compat_Math_Start",
            "ID_Compat_Math_Continue",
            "InCB",
            "Lowercase,Lower",
            "Math",
            "Uppercase,Upper",
            "XID_Continue,XIDC",
            "XID_Start,XIDS",
    };
    public static final int PROP_NONCHARACTER_CODE_POINT = 35;
    public static final int PROP_OTHER_ALPHABETIC = 2;
    public static final int PROP_OTHER_DEFAULT_IGNORABLE_CODE_POINT = 6;
    public static final int PROP_OTHER_GRAPHEME_EXTEND = 5;
    public static final int PROP_OTHER_ID_CONTINUE = 8;
    public static final int PROP_OTHER_ID_START = 7;
    public static final int PROP_OTHER_LOWERCASE = 3;
    public static final int PROP_OTHER_MATH = 1;
    public static final int PROP_OTHER_UPPERCASE = 4;
    public static final int PROP_PATTERN_SYNTAX = 36;
    public static final int PROP_PATTERN_WHITE_SPACE = 37;
    public static final int PROP_PREPENDED_CONCATENATION_MARK = 9;
    public static final int PROP_QUOTATION_MARK = 38;
    public static final int PROP_RADICAL = 39;
    public static final int PROP_REGIONAL_INDICATOR = 40;
    public static final int PROP_RGI_EMOJI_FLAG_SEQUENCE = 19;
    public static final int PROP_RGI_EMOJI_MODIFIER_SEQUENCE = 18;
    public static final int PROP_SENTENCE_TERMINAL = 41;
    public static final int PROP_SOFT_DOTTED = 42;
    // Property tables (from helper classes)
    public static final byte[][] PROP_TABLES = {
            UnicodePropertyTablesData.PROP_Hyphen,
            UnicodePropertyTablesData.PROP_Other_Math,
            UnicodePropertyTablesData.PROP_Other_Alphabetic,
            UnicodePropertyTablesData.PROP_Other_Lowercase,
            UnicodePropertyTablesData.PROP_Other_Uppercase,
            UnicodePropertyTablesData.PROP_Other_Grapheme_Extend,
            UnicodePropertyTablesData.PROP_Other_Default_Ignorable_Code_Point,
            UnicodePropertyTablesData.PROP_Other_ID_Start,
            UnicodePropertyTablesData.PROP_Other_ID_Continue,
            UnicodePropertyTablesData.PROP_Prepended_Concatenation_Mark,
            UnicodePropertyTablesData.PROP_ID_Continue1,
            UnicodePropertyTablesData.PROP_XID_Start1,
            UnicodePropertyTablesData.PROP_XID_Continue1,
            UnicodePropertyTablesData.PROP_Changes_When_Titlecased1,
            UnicodePropertyTablesData.PROP_Changes_When_Casefolded1,
            UnicodePropertyTablesData.PROP_Changes_When_NFKC_Casefolded1,
            UnicodePropertyTablesData.PROP_Basic_Emoji1,
            UnicodePropertyTablesData.PROP_Basic_Emoji2,
            UnicodePropertyTablesData.PROP_RGI_Emoji_Modifier_Sequence,
            UnicodePropertyTablesData.PROP_RGI_Emoji_Flag_Sequence,
            UnicodePropertyTablesData.PROP_Emoji_Keycap_Sequence,
            UnicodePropertyTablesData.PROP_ASCII_Hex_Digit,
            UnicodePropertyTablesData.PROP_Bidi_Control,
            UnicodePropertyTablesData.PROP_Dash,
            UnicodePropertyTablesData.PROP_Deprecated,
            UnicodePropertyTablesData.PROP_Diacritic,
            UnicodePropertyTablesData.PROP_Extender,
            UnicodePropertyTablesData.PROP_Hex_Digit,
            UnicodePropertyTablesData.PROP_IDS_Unary_Operator,
            UnicodePropertyTablesData.PROP_IDS_Binary_Operator,
            UnicodePropertyTablesData.PROP_IDS_Trinary_Operator,
            UnicodePropertyTablesData.PROP_Ideographic,
            UnicodePropertyTablesData.PROP_Join_Control,
            UnicodePropertyTablesData.PROP_Logical_Order_Exception,
            UnicodePropertyTablesData.PROP_Modifier_Combining_Mark,
            UnicodePropertyTablesData.PROP_Noncharacter_Code_Point,
            UnicodePropertyTablesData.PROP_Pattern_Syntax,
            UnicodePropertyTablesData.PROP_Pattern_White_Space,
            UnicodePropertyTablesData.PROP_Quotation_Mark,
            UnicodePropertyTablesData.PROP_Radical,
            UnicodePropertyTablesData.PROP_Regional_Indicator,
            UnicodePropertyTablesData.PROP_Sentence_Terminal,
            UnicodePropertyTablesData.PROP_Soft_Dotted,
            UnicodePropertyTablesData.PROP_Terminal_Punctuation,
            UnicodePropertyTablesData.PROP_Unified_Ideograph,
            UnicodePropertyTablesData.PROP_Variation_Selector,
            UnicodePropertyTablesData.PROP_White_Space,
            UnicodePropertyTablesData.PROP_Bidi_Mirrored,
            UnicodePropertyTablesData.PROP_Emoji,
            UnicodePropertyTablesData.PROP_Emoji_Component,
            UnicodePropertyTablesData.PROP_Emoji_Modifier,
            UnicodePropertyTablesData.PROP_Emoji_Modifier_Base,
            UnicodePropertyTablesData.PROP_Emoji_Presentation,
            UnicodePropertyTablesData.PROP_Extended_Pictographic,
            UnicodePropertyTablesData.PROP_Default_Ignorable_Code_Point,
            UnicodePropertyTablesData.PROP_ID_Start,
            UnicodePropertyTablesData.PROP_Case_Ignorable,
    };
    public static final int PROP_TERMINAL_PUNCTUATION = 43;
    public static final int PROP_UNIFIED_IDEOGRAPH = 44;
    public static final int PROP_VARIATION_SELECTOR = 45;
    public static final int PROP_WHITE_SPACE = 46;
    public static final int PROP_XID_CONTINUE1 = 12;
    public static final int PROP_XID_START1 = 11;
    public static final int RUN_TYPE_L = 1;
    public static final int RUN_TYPE_LF = 3;
    public static final int RUN_TYPE_LSU = 5;
    // Run type constants for case conversion
    public static final int RUN_TYPE_U = 0;
    public static final int RUN_TYPE_UF = 2;
    public static final int RUN_TYPE_UL = 4;
    public static final byte[] SCRIPT_EXT_TABLE = UnicodePropertyTablesScript.SCRIPT_EXT_TABLE;
    // Script name table entries (comma-separated aliases)
    public static final String[] SCRIPT_NAME_TABLE = {
            "Unknown,Zzzz",
            "Adlam,Adlm",
            "Ahom,Ahom",
            "Anatolian_Hieroglyphs,Hluw",
            "Arabic,Arab",
            "Armenian,Armn",
            "Avestan,Avst",
            "Balinese,Bali",
            "Bamum,Bamu",
            "Bassa_Vah,Bass",
            "Batak,Batk",
            "Beria_Erfe,Berf",
            "Bengali,Beng",
            "Bhaiksuki,Bhks",
            "Bopomofo,Bopo",
            "Brahmi,Brah",
            "Braille,Brai",
            "Buginese,Bugi",
            "Buhid,Buhd",
            "Canadian_Aboriginal,Cans",
            "Carian,Cari",
            "Caucasian_Albanian,Aghb",
            "Chakma,Cakm",
            "Cham,Cham",
            "Cherokee,Cher",
            "Chorasmian,Chrs",
            "Common,Zyyy",
            "Coptic,Copt,Qaac",
            "Cuneiform,Xsux",
            "Cypriot,Cprt",
            "Cyrillic,Cyrl",
            "Cypro_Minoan,Cpmn",
            "Deseret,Dsrt",
            "Devanagari,Deva",
            "Dives_Akuru,Diak",
            "Dogra,Dogr",
            "Duployan,Dupl",
            "Egyptian_Hieroglyphs,Egyp",
            "Elbasan,Elba",
            "Elymaic,Elym",
            "Ethiopic,Ethi",
            "Garay,Gara",
            "Georgian,Geor",
            "Glagolitic,Glag",
            "Gothic,Goth",
            "Grantha,Gran",
            "Greek,Grek",
            "Gujarati,Gujr",
            "Gunjala_Gondi,Gong",
            "Gurmukhi,Guru",
            "Gurung_Khema,Gukh",
            "Han,Hani",
            "Hangul,Hang",
            "Hanifi_Rohingya,Rohg",
            "Hanunoo,Hano",
            "Hatran,Hatr",
            "Hebrew,Hebr",
            "Hiragana,Hira",
            "Imperial_Aramaic,Armi",
            "Inherited,Zinh,Qaai",
            "Inscriptional_Pahlavi,Phli",
            "Inscriptional_Parthian,Prti",
            "Javanese,Java",
            "Kaithi,Kthi",
            "Kannada,Knda",
            "Katakana,Kana",
            "Katakana_Or_Hiragana,Hrkt",
            "Kawi,Kawi",
            "Kayah_Li,Kali",
            "Kharoshthi,Khar",
            "Khmer,Khmr",
            "Khojki,Khoj",
            "Khitan_Small_Script,Kits",
            "Khudawadi,Sind",
            "Kirat_Rai,Krai",
            "Lao,Laoo",
            "Latin,Latn",
            "Lepcha,Lepc",
            "Limbu,Limb",
            "Linear_A,Lina",
            "Linear_B,Linb",
            "Lisu,Lisu",
            "Lycian,Lyci",
            "Lydian,Lydi",
            "Makasar,Maka",
            "Mahajani,Mahj",
            "Malayalam,Mlym",
            "Mandaic,Mand",
            "Manichaean,Mani",
            "Marchen,Marc",
            "Masaram_Gondi,Gonm",
            "Medefaidrin,Medf",
            "Meetei_Mayek,Mtei",
            "Mende_Kikakui,Mend",
            "Meroitic_Cursive,Merc",
            "Meroitic_Hieroglyphs,Mero",
            "Miao,Plrd",
            "Modi,Modi",
            "Mongolian,Mong",
            "Mro,Mroo",
            "Multani,Mult",
            "Myanmar,Mymr",
            "Nabataean,Nbat",
            "Nag_Mundari,Nagm",
            "Nandinagari,Nand",
            "New_Tai_Lue,Talu",
            "Newa,Newa",
            "Nko,Nkoo",
            "Nushu,Nshu",
            "Nyiakeng_Puachue_Hmong,Hmnp",
            "Ogham,Ogam",
            "Ol_Chiki,Olck",
            "Ol_Onal,Onao",
            "Old_Hungarian,Hung",
            "Old_Italic,Ital",
            "Old_North_Arabian,Narb",
            "Old_Permic,Perm",
            "Old_Persian,Xpeo",
            "Old_Sogdian,Sogo",
            "Old_South_Arabian,Sarb",
            "Old_Turkic,Orkh",
            "Old_Uyghur,Ougr",
            "Oriya,Orya",
            "Osage,Osge",
            "Osmanya,Osma",
            "Pahawh_Hmong,Hmng",
            "Palmyrene,Palm",
            "Pau_Cin_Hau,Pauc",
            "Phags_Pa,Phag",
            "Phoenician,Phnx",
            "Psalter_Pahlavi,Phlp",
            "Rejang,Rjng",
            "Runic,Runr",
            "Samaritan,Samr",
            "Saurashtra,Saur",
            "Sharada,Shrd",
            "Shavian,Shaw",
            "Siddham,Sidd",
            "Sidetic,Sidt",
            "SignWriting,Sgnw",
            "Sinhala,Sinh",
            "Sogdian,Sogd",
            "Sora_Sompeng,Sora",
            "Soyombo,Soyo",
            "Sundanese,Sund",
            "Sunuwar,Sunu",
            "Syloti_Nagri,Sylo",
            "Syriac,Syrc",
            "Tagalog,Tglg",
            "Tagbanwa,Tagb",
            "Tai_Le,Tale",
            "Tai_Tham,Lana",
            "Tai_Viet,Tavt",
            "Tai_Yo,Tayo",
            "Takri,Takr",
            "Tamil,Taml",
            "Tangut,Tang",
            "Telugu,Telu",
            "Thaana,Thaa",
            "Thai,Thai",
            "Tibetan,Tibt",
            "Tifinagh,Tfng",
            "Tirhuta,Tirh",
            "Tangsa,Tnsa",
            "Todhri,Todr",
            "Tolong_Siki,Tols",
            "Toto,Toto",
            "Tulu_Tigalari,Tutg",
            "Ugaritic,Ugar",
            "Vai,Vaii",
            "Vithkuqi,Vith",
            "Wancho,Wcho",
            "Warang_Citi,Wara",
            "Yezidi,Yezi",
            "Yi,Yiii",
            "Zanabazar_Square,Zanb",
    };
    // Script tables
    public static final byte[] SCRIPT_TABLE = UnicodePropertyTablesScript.SCRIPT_TABLE;

    private UnicodePropertyTables() {
    }

}
