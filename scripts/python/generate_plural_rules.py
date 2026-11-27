"""Generate pinned CLDR 48 plural rules; no runtime library or network dependency.

Usage: python3 scripts/python/generate_plural_rules.py
Source: https://github.com/unicode-org/cldr/tree/release-48/common/supplemental
Data license: Unicode-3.0 (see LICENSE-UNICODE).
"""
from pathlib import Path
from urllib.request import urlopen
import xml.etree.ElementTree as ET
import json

ROOT = Path(__file__).resolve().parents[2]
BASE = 'https://raw.githubusercontent.com/unicode-org/cldr/release-48/common/supplemental/'

def quote(value):
    return json.dumps(value, ensure_ascii=False)

lines = []
for filename, kind in [('plurals.xml', 'CARDINAL'), ('ordinals.xml', 'ORDINAL')]:
    tree = ET.fromstring(urlopen(BASE + filename).read())
    for group in tree.findall('.//pluralRules'):
        rules = [r.attrib['count'] + ':' + r.text.split('@')[0].strip() for r in group.findall('pluralRule')]
        lines.append('        addRules(' + kind + ', ' + quote(group.attrib['locales']) + ', '
                     + ', '.join(map(quote, rules)) + ');')
tree = ET.fromstring(urlopen(BASE + 'pluralRanges.xml').read())
for group in tree.findall('.//pluralRanges'):
    rules = [r.attrib['start'] + ':' + r.attrib['end'] + ':' + r.attrib['result'] for r in group.findall('pluralRange')]
    lines.append('        addRanges(' + quote(group.attrib['locales']) + ', ' + ', '.join(map(quote, rules)) + ');')
template = (ROOT / 'scripts/python/plural_rules_data.java.template').read_text()
(ROOT / 'src/main/java/com/caoccao/qjs4j/core/intl/PluralRulesData.java').write_text(template.replace('@@DATA@@', '\n'.join(lines)))
