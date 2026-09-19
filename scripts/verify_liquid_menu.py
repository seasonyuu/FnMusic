#!/usr/bin/env python3
"""Build and verify LiquidMenu on connected API 26/31/33/36 devices.

Reports and artifacts stay in build/reports/liquid-menu. Never updates baselines.
Use --apis 36 for a focused run; omitted devices make the full matrix incomplete.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/reports/liquid-menu'
CORE = 'com.seasonyuu.fnmusic.core.designsystem'
FEATURE = 'com.seasonyuu.fnmusic.feature.music'


def run(args, *, log=None, timeout=900):
    result = subprocess.run(args, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=timeout)
    if log:
        log.parent.mkdir(parents=True, exist_ok=True)
        log.write_text(result.stdout)
    return result


def main():
    global OUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apis', default='26,31,33,36')
    parser.add_argument('--output', type=Path, default=OUT, help='Report directory (default: build/reports/liquid-menu)')
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--regressions', action='store_true', help='Also run existing glass/app-bar suites; their failures remain failures')
    parser.add_argument('--performance', action='store_true', help='Collect original/liquid frame distributions on each device')
    args = parser.parse_args()
    OUT = args.output.resolve()
    apis = [int(x) for x in args.apis.split(',')]
    OUT.mkdir(parents=True, exist_ok=True)
    summary = {'requested_apis': apis, 'devices': [], 'status': 'incomplete',
               'known_pending_review': ['API 33/36 contour-1.03 highlight baseline; failures remain failures']}
    def save():
        (OUT/'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2)+'\n')
    save()
    check = run(['git', 'diff', '--check'], log=OUT/'diff-check.log')
    if check.returncode:
        summary['error'] = 'git diff --check failed'; save(); return 1
    if not args.skip_build:
        build = run(['./gradlew', ':core:designsystem:testDebugUnitTest', ':feature:music:testDebugUnitTest',
                     ':core:designsystem:assembleDebugAndroidTest', ':feature:music:assembleDebugAndroidTest',
                     '--console=plain'], log=OUT/'gradle.log')
        if build.returncode:
            summary['error'] = 'Gradle checks failed'; save(); return 1
    listing = run(['adb', 'devices']).stdout.splitlines()[1:]
    devices = {}
    for line in listing:
        fields = line.split()
        if len(fields) >= 2 and fields[1] == 'device':
            value = run(['adb', '-s', fields[0], 'shell', 'getprop', 'ro.build.version.sdk']).stdout.strip()
            if value.isdigit():
                devices.setdefault(int(value), fields[0])
    for api in apis:
        record = {'api': api, 'status': 'incomplete'}
        summary['devices'].append(record)
        if api not in devices:
            record['reason'] = 'No connected device'; save(); continue
        serial = devices[api]
        record['serial'] = serial
        record['screen'] = run(['adb', '-s', serial, 'shell', 'wm', 'size']).stdout.strip()
        record['density'] = run(['adb', '-s', serial, 'shell', 'wm', 'density']).stdout.strip()
        dest = OUT/f'api-{api}'
        dest.mkdir(exist_ok=True)
        print(f'API {api}: {serial}', flush=True)
        runs = [
            ('core/designsystem', 'designsystem', CORE, [f'{CORE}.LiquidMenu{x}Test' for x in ('Rendering', 'Interaction', 'Compatibility', 'ReducedMotion', 'Anchor')]),
            ('feature/music', 'music', FEATURE, [f'{FEATURE}.TrackSortLiquidMenuTest', f'{FEATURE}.AppBarLiquidMenuTest',
                f'{FEATURE}.TrackMoreLiquidMenuTest', f'{FEATURE}.AlbumDetailScreenTest', f'{FEATURE}.SearchScreenTest',
                *[f'{FEATURE}.MusicShellTest#{method}' for method in (
                    'trackMoreMenuDispatchesPlayNextAndUsesLiquidMenu',
                    'playerMoreMorphsFromTransientSurfaceAndRestoresIcon',
                    'trackLiquidMenuDeliversQueueAndFavoriteActionsOnce',
                    'playerMenuContainsOnlyFourActionsInRequestedOrder',
                    'lyricsHeaderMoreActionUsesLiquidMenuAndStaysVisible',
                    'playerAlbumNavigationCollapsesAndQualityIsCentered',
                    'searchMoreUsesExistingFavoriteMenu',
                    'trackCanBeAddedToAnExistingPlaylistFromLiquidMenu',
                    'creatingAPlaylistFromTrackActionsKeepsThePendingTrack',
                    'playlistTrackMenuUsesExplicitSourceAndBusyState',
                    'playerMenuDoesNotInheritBackgroundPlaylist',
                    'queueModeSelectsTracksAndDispatchesLocalReordering',
                    'queueSwipeRevealsRemovalWithoutPlayingAndClosesOnScroll',
                    'duplicateQueueEntriesRetainIdentityAfterDraggingAndRemoval',
                )]]),
        ]
        if args.regressions:
            runs[1][3].extend([f'{FEATURE}.LiquidGlassRenderingTest', f'{FEATURE}.MusicAppBarTest'])
        if args.performance:
            runs[0][3].append(f'{CORE}.LiquidMenuPerformanceTest')
        record['suites'] = []
        for module, name, package, classes in runs:
            apk = ROOT/module/f'build/outputs/apk/androidTest/debug/{name}-debug-androidTest.apk'
            installed = run(['adb', '-s', serial, 'install', '-r', '-t', str(apk)], log=dest/f'{name}-install.log')
            if installed.returncode:
                record['suites'].append({'name': name, 'status': 'failed', 'reason': 'APK install failed'}); continue
            run(['adb', '-s', serial, 'shell', 'rm', '-rf',
                 f'/sdcard/Android/data/{package}.test/files/liquid-menu'])
            # Direct instrumentation preserves external artifacts for adb pull; Gradle UTP uninstalls them.
            result = run(['adb', '-s', serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', ','.join(classes), '-e', 'requireLiquidMenuBaselines', 'true',
                          package+'.test/androidx.test.runner.AndroidJUnitRunner'], log=dest/f'{name}-tests.log', timeout=1800)
            success = result.returncode == 0 and bool(re.search(r'OK \(\d+ tests?\)', result.stdout)) and 'FAILURES!!!' not in result.stdout
            skipped = sum(result.stdout.count(f'INSTRUMENTATION_STATUS_CODE: {code}') for code in (-3, -4))
            expected_skips = (5 if name == 'designsystem' else 1) if api < 33 else 0
            record['suites'].append({'name': name, 'status': 'passed' if success and skipped == expected_skips else 'failed',
                                      'skipped': skipped, 'expected_shader_only_skips': expected_skips,
                                      'result': re.search(r'(OK \(\d+ tests?\)|Tests run:.*)', result.stdout).group(0) if re.search(r'(OK \(\d+ tests?\)|Tests run:.*)', result.stdout) else 'No result'})
            shutil.rmtree(dest/name, ignore_errors=True)
            run(['adb', '-s', serial, 'pull', f'/sdcard/Android/data/{package}.test/files/liquid-menu', str(dest/name)],
                log=dest/f'{name}-artifacts.log')
            if name == 'music':
                run(['adb', '-s', serial, 'pull', f'/sdcard/Android/data/{package}.test/files/queue-screenshots',
                     str(dest/'player-screenshots')], log=dest/'player-artifacts.log')
        artifacts = dest/'designsystem'
        record['artifacts_complete'] = api < 33 or (
            len(list(artifacts.glob('contour-*-diff.png'))) >= 8 and
            len(list(artifacts.glob('anchor-*.png'))) >= 36 and
            (artifacts/'updated-foreground-return.png').exists())
        if args.performance:
            record['artifacts_complete'] &= (artifacts/'performance.json').exists()
        record['status'] = 'passed' if record['artifacts_complete'] and all(s['status']=='passed' for s in record['suites']) else 'failed'
        save()
    summary['status'] = 'passed' if all(d['status']=='passed' for d in summary['devices']) else 'incomplete'
    save()
    print(f"{summary['status']}: {OUT/'summary.json'}", flush=True)
    return 0 if summary['status']=='passed' else 1


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (subprocess.TimeoutExpired, OSError) as error:
        print(f'Verification incomplete: {error}', file=sys.stderr)
        sys.exit(1)
