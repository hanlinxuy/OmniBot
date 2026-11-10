"""Build traceable Android variants; never transfer a pass between source tasks."""
import argparse
import hashlib
import json
import pathlib
from collections import Counter


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ios-source', type=pathlib.Path, required=True)
    parser.add_argument('--coverage', type=pathlib.Path, required=True)
    parser.add_argument('--results', type=pathlib.Path)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    ios_bytes = (args.ios_source / 'tasks.json').read_bytes()
    ios = json.loads(ios_bytes)
    previous = json.loads(args.coverage.read_text())
    assert len(ios) == 133 and len({t['name'] for t in ios}) == 133
    # These are capability mappings, not assertions that the fixtures already exist.
    routes = {
        'clock': ('Android Clock Intent / scoped clock controls', 'owned alarms/timers; baseline and independent clock readback'),
        'notes': ('vivo Notes controls or isolated note store', 'source note bodies, folders, append-preservation and deletion verifier'),
        'clouddocs': ('phone Pi document files', 'source document bodies; saved-file diff; GUI rubric explicitly substituted'),
        'cloudsheets': ('phone Pi XLSX files', 'source cells/formulas; persisted workbook and untouched-cell verifier'),
        'cloudslides': ('phone Pi PPTX files', 'source slides; real slide structure and content verifier'),
        'clouddrive': ('scoped document metadata service', 'source hierarchy, modified times, stars and links; filesystem alone cannot represent stars'),
        'mail': ('Android mock Mail / scoped mail fixture', 'source threads, categories, contacts and owned send/archive verifier'),
        'teamchat': ('isolated Mattermost fixture', 'source channels, unread state, threads and owned message cleanup'),
        'quickchat': ('isolated messaging fixture', 'source contacts/conversations; no personal recipients'),
        'weather': ('fixed-date weather service or Android weather controls', 'source city/date forecasts; do not replace historical fixture with current weather'),
        'caltrack': ('isolated nutrition store', 'source food/serving/macros history and log mutation verifier'),
        'trailblaze': ('isolated activity store', 'source routes/times/laps/social history and owned interaction verifier'),
        'cinephile': ('isolated movie catalog', 'source films, ratings, wishlist and persisted wishlist verifier'),
        'scorezone': ('isolated sports feed', 'source favorites, headlines and dated game schedule'),
        'cityride': ('isolated ride service', 'source locations/history/fares; sandbox booking only, no real rides'),
        'dinespot': ('isolated restaurant service', 'source availability/history/seat types; sandbox reservations only'),
        'freshcart': ('isolated grocery catalog/cart', 'source products/prices/history and reversible cart; no checkout'),
        'megamart': ('isolated shopping catalog/cart', 'source products/prices/history and reversible cart; no checkout'),
        'quickbite': ('isolated food-order service', 'source menus/order history; sandbox orders only, no purchase'),
        'skytrip': ('isolated flight search/history', 'source dated flights/prices/bookings; no live purchase'),
        'stayfinder': ('isolated lodging search/history', 'source trips/listing details; no live purchase'),
        'ticketbox': ('isolated event catalog/history', 'source dates/prices/tickets; no live purchase'),
        'tasterank': ('isolated restaurant list', 'source reviews/cuisines/scores and saved-list verifier'),
        'lockedin': ('isolated professional-network fixture', 'source posts/profile/DM/jobs; no external contact or posting'),
        'mybank': ('EXCLUDED', 'bank app excluded by user'),
        'splitpay': ('EXCLUDED', 'payment app excluded by user'),
    }
    variants = []
    for t in previous['tasks']:
        if t['benchmark'] != 'MobileWorld':
            continue
        variants.append({**t, 'key': 'MobileWorld/' + t['id'], 'canonicalScenario': 'MobileWorld/' + t['id'],
                         'sourceCommit': previous['pins']['MobileWorld'], 'resultScope': 'historical Android adaptations; see individual evidence',
                         'originalJudge': 'pinned MobileWorld task definition; sourceSha256 retained'})
    assert len(variants) == 201
    for t in ios:
        excluded = any(a in ('mybank', 'splitpay') for a in t['apps'])
        native = set(t['apps']) <= {'clock', 'notes', 'clouddocs', 'cloudsheets', 'cloudslides'}
        status = 'excluded_user_scope' if excluded else ('native_adapter_candidate' if native else 'requires_isolated_source_fixtures')
        rubric = [{'criterion': r['criterion'], 'androidCheck': r['criterion'],
                   'executionContract': 'Preserve business outcome and values; original app-navigation steps are not scored as passed when using an API/file adapter.'} for r in t['rubric']]
        variants.append({'key': 'iOSWorld/' + t['name'], 'id': t['name'], 'benchmark': 'iOSWorld',
            'sourceCommit': previous['pins']['iOSWorld'], 'tasksFileSha256': hashlib.sha256(ios_bytes).hexdigest(),
            'sourceSha256': hashlib.sha256(json.dumps(t, sort_keys=True).encode()).hexdigest(),
            'prompt': t['goal'], 'apps': t['apps'], 'category': t['category'], 'difficulty': t['difficulty'],
            'rubric': rubric, 'androidMapping': [{'sourceApp': a, 'targetCapability': routes[a][0], 'requiredFixtureAndVerifier': routes[a][1]} for a in t['apps']],
            'status': status, 'canonicalScenario': 'MobileWorld/SetAlarmTask' if t['name'] == 'clock-001' else 'iOSWorld/' + t['name'],
            'relationship': 'parameterized_variant' if t['name'] == 'clock-001' else 'distinct_source_variant',
            'acceptance': 'All source business criteria, preserved inputs, independent persisted-state readback; partial components do not pass the full task.',
            'cleanup': 'Remove only run-owned fixture objects and verify their absence; restore any recorded system baseline.',
            'results': [], 'officialBenchmarkScore': None})
    if args.results:
        runs = json.loads(args.results.read_text())['runs']
        for variant in variants:
            if variant['benchmark'] == 'iOSWorld':
                variant['results'] = [r for r in runs if r['sourceTask'] == variant['id']]
                if any(r.get('acceptance', {}).get('passed') and r.get('cleanup', {}).get('workspaceAbsent') for r in variant['results']):
                    variant['status'] = 'android_file_adaptation_passed'
    # Identical goals can hide a different required branch in the source fixture.
    not_merged = [{'left': 'MobileWorld/SayHelloRoommatesAskUserTask1', 'right': 'MobileWorld/SayHelloRoommatesAskUserTask2',
                   'reason': 'Same prompt, but Task2 requires creating a missing contact before favorites and messaging; Task1 has all contacts.'}]
    # Only reviewed full-workflow equivalence is merged. Shared apps/components are not duplicates.
    result = {'schemaVersion': 1, 'sourceCounts': {'MobileWorld': 201, 'iOSWorld': 133},
        'sourcePins': previous['pins'], 'variantCount': len(variants),
        'canonicalScenarioCount': len({v['canonicalScenario'] for v in variants}),
        'deduplication': {'exactDuplicates': 0, 'samePromptNotMerged': not_merged, 'reviewedParameterizedRelations': [{'left': 'iOSWorld/clock-001', 'right': 'MobileWorld/SetAlarmTask', 'shared': 'create and verify an alarm', 'differences': 'time, label, recurrence, ringtone and vibration remain separate variant assertions; never share results'}],
                          'rule': 'Unreviewed candidates remain distinct; shared components do not imply equivalent complete tasks.'},
        'iosStatusCounts': dict(Counter(v['status'] for v in variants if v['benchmark'] == 'iOSWorld')), 'variants': variants}
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'android-unified-scenarios.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    lines = ['# iOSWorld → Android 逐项映射', '', '完整 prompt、逐条 rubric、固定源版本、历史结果及差异见同目录 `android-unified-scenarios.json`。', '',
             '334 个来源变体，333 个保守归并场景；仅闹钟的一对共用模板，各自验收。没有把相似应用或局部动作当成重复完整任务。', '',
             '状态来自迁移依赖判定及独立读回结果，不是 133 项真机通过。银行/支付排除整项；原 iOS 界面条款不会因 Android 文件/API 操作而算通过。', '',
             '| 来源 ID | Android 能力对应 | 当前迁移状态 |', '|---|---|---|']
    for v in variants:
        if v['benchmark'] == 'iOSWorld':
            lines.append('| ' + v['id'] + ' | ' + '; '.join(a['sourceApp'] + ' → ' + a['targetCapability'] for a in v['androidMapping']) + ' | ' + v['status'] + ' |')
    (args.output / 'iosworld-android-mapping.md').write_text('\n'.join(lines) + '\n')
    print(json.dumps({k: v for k, v in result.items() if k not in ('variants',)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
