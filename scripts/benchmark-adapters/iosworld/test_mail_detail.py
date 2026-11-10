"""Controls for the pinned Mail app's detail toolbar and readable content."""
import ast
import os
from pathlib import Path
from types import SimpleNamespace
import unittest


class MailDetailTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ['IOSWORLD_SOURCE']) / 'mcps/mail.py'
        functions = [n for n in ast.parse(path.read_text()).body if isinstance(n, ast.FunctionDef)
                     and n.name in ('_in_message_detail', '_is_mail_foreground', 'read_open_message')]
        for n in functions: n.decorator_list = []
        cls.namespace = {'BUNDLE_ID': 'com.iosworld.benchmark.mail'}
        exec(compile(ast.Module(body=functions, type_ignores=[]), str(path), 'exec'), cls.namespace)

    def test_detail_body_survives_verbose_xml_and_pagination(self):
        # Source Swift toolbar uses explicit IDs, not the archivebox/trash image names.
        xml = '<AppiumAUT><XCUIElementTypeApplication name="Mail" bundleId="com.iosworld.benchmark.mail">'
        xml += '<XCUIElementTypeOther/>' * 200
        xml += '<XCUIElementTypeStaticText label="Subject &amp; context"/>'
        xml += '<XCUIElementTypeStaticText label="Saved body with an action item."/>'
        xml += '<XCUIElementTypeButton name="detail_archive_button"/><XCUIElementTypeButton name="detail_delete_button"/>'
        xml += '</XCUIElementTypeApplication></AppiumAUT>'
        self.namespace['SimulatorBridge'] = SimpleNamespace(get=lambda: SimpleNamespace(observe_text=lambda: xml))
        self.assertTrue(self.namespace['_in_message_detail'](xml))
        first = self.namespace['read_open_message'](0, 19)
        second = self.namespace['read_open_message'](first['next_offset'], 1500)
        self.assertEqual(first['text'] + second['text'], 'Subject & context\nSaved body with an action item.')
        self.assertIsNone(second['next_offset'])

    def test_list_screen_and_invalid_bounds_do_not_read_as_a_message(self):
        xml = '<AppiumAUT><XCUIElementTypeApplication name="Mail"><XCUIElementTypeButton name="bulk_archive_button"/></XCUIElementTypeApplication></AppiumAUT>'
        self.namespace['SimulatorBridge'] = SimpleNamespace(get=lambda: SimpleNamespace(observe_text=lambda: xml))
        self.assertFalse(self.namespace['_in_message_detail'](xml))
        self.assertFalse(self.namespace['read_open_message']()['ok'])
        self.assertFalse(self.namespace['read_open_message'](-1)['ok'])
        self.assertFalse(self.namespace['read_open_message'](0, 1501)['ok'])


if __name__ == '__main__':
    unittest.main()
