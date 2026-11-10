"""Focused controls against a patched iOSWorld checkout; no simulator needed."""
import ast
import os
from pathlib import Path
import re
import unittest
from types import SimpleNamespace


class ClockPickerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source = Path(os.environ['IOSWORLD_SOURCE']) / 'mcps/clock.py'
        tree = ast.parse(source.read_text())
        selected = [node for node in tree.body if isinstance(node, ast.FunctionDef)
                    and node.name in ('_set_alarm_time_picker', 'add_alarm')]
        for node in selected:
            node.decorator_list = []
        cls.namespace = {'re': re}
        exec(compile(ast.Module(body=selected, type_ignores=[]), str(source), 'exec'), cls.namespace)

    def test_twenty_four_hour_and_twelve_hour_boundaries(self):
        for count, hour, minute, meridiem, expected in [
            (2, 6, 45, 'AM', ['06', '45']),
            (2, 12, 0, 'AM', ['00', '00']),
            (2, 12, 0, 'PM', ['12', '00']),
            (2, 6, 15, 'PM', ['18', '15']),
            (3, 6, 45, 'AM', ['6', '45', 'AM']),
        ]:
            with self.subTest(count=count, hour=hour, meridiem=meridiem):
                wheels = [Wheel() for _ in range(count)]
                sim = SimpleNamespace(driver=SimpleNamespace(find_elements=lambda *_: wheels), wait=lambda _: None)
                ok, _ = self.namespace['_set_alarm_time_picker'](sim, hour, minute, meridiem)
                self.assertTrue(ok)
                self.assertEqual(expected, [w.value for w in wheels])

    def test_failed_wheel_write_cannot_save_an_alarm(self):
        wheels = [Wheel(accept=False), Wheel()]
        dismissed = []
        sim = SimpleNamespace(driver=SimpleNamespace(find_elements=lambda *_: wheels),
                              wait=lambda _: None, observe_text=lambda: '<root/>')
        self.namespace.update(SimulatorBridge=SimpleNamespace(get=lambda: sim),
                              _ensure_tab=lambda *_: None, _open_alarm_sheet=lambda _: True,
                              _dismiss_overlays=lambda _: dismissed.append(True),
                              _tap_save=lambda _: self.fail('A failed picker must not save'))
        result = self.namespace['add_alarm']('6:45 AM', 'Fixture')
        self.assertTrue(result.startswith('Could not add alarm:'))
        self.assertEqual([True], dismissed)


class Wheel:
    def __init__(self, accept=True):
        self.accept = accept
        self.value = '13'

    def send_keys(self, value):
        if self.accept:
            self.value = value

    def get_attribute(self, name):
        return self.value


if __name__ == '__main__':
    unittest.main()
