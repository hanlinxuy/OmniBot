#!/usr/bin/env python3
"""Restore only the disposable lab fixture and wait for its actual ready state."""
import argparse
import subprocess
import time


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb', required=True)
    p.add_argument('--case', choices=['calculator', 'notes', 'alarm'], required=True)
    args = p.parse_args()
    adb = [args.adb, '-s', 'emulator-5580']
    if subprocess.check_output(adb + ['emu', 'avd', 'name'], text=True).splitlines()[0] != 'omnibot-lab':
        p.error('Only the disposable omnibot-lab AVD is allowed')
    snapshot = 'visual-loop-installed-apps' if args.case == 'alarm' else f'visual-loop-{args.case}-ready'
    subprocess.run(adb + ['emu', 'avd', 'snapshot', 'load', snapshot], check=True, timeout=30)
    subprocess.run(adb + ['wait-for-device'], check=True, timeout=45)
    component = {'calculator': 'org.fossify.math/.activities.SplashActivity.Green',
                 'notes': 'org.fossify.notes/.activities.SplashActivity.Green',
                 'alarm': 'com.android.deskclock/.DeskClock'}[args.case]
    for setting, value in [('enabled_accessibility_services', 'null'), ('accessibility_enabled', '0')]:
        subprocess.run(adb + ['shell', 'settings', 'put', 'secure', setting, value], check=True, timeout=10)
    subprocess.run(adb + ['shell', 'am', 'start', '-n', component], check=True, timeout=15)
    for _ in range(30):
        focus = subprocess.check_output(adb + ['shell', 'dumpsys', 'window'], text=True, timeout=10)
        if any('mCurrentFocus=' in line and component.split('/')[0] + '/' in line for line in focus.splitlines()):
            print('Fixture ready:', args.case)
            return
        time.sleep(1)
    raise RuntimeError('Fixture did not reach its expected foreground app')


if __name__ == '__main__':
    main()
