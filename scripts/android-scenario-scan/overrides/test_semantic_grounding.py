import importlib.util
import pathlib
import sys
import types
import unittest
import xml.etree.ElementTree as ET
from dataclasses import dataclass

@dataclass
class Action:
    tool: str
    args: dict

@dataclass
class Observation:
    xml: str
    extra: dict

model = types.ModuleType('omniflow.core.model')
model.Action, model.Observation = Action, Observation
sys.modules['omniflow'] = types.ModuleType('omniflow')
sys.modules['omniflow.core'] = types.ModuleType('omniflow.core')
sys.modules['omniflow.core.model'] = model
path = pathlib.Path(__file__).with_name('semantic_grounding.py')
spec = importlib.util.spec_from_file_location('semantic_grounding_override', path)
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)

def resolve(xml, target):
    return module.resolve_semantic_action(Action('click', {'target_description': target, 'x': 10, 'y': 10}), Observation(xml, {'display': {'width': 1260, 'height': 2800}}))

class GroundingTests(unittest.TestCase):
    def test_real_calculator_xml(self):
        fixture = pathlib.Path('.session-history/full-scenario-scan/BA19-before-1.xml')
        if not fixture.exists(): self.skipTest('private real-device fixture unavailable')
        xml = fixture.read_text()
        for label in ('1 button', '数字键 1', 'AC button'):
            result = resolve(xml, label)
            self.assertEqual(result.detail['status'], 'resolved', label)
        result = resolve(xml, '1 button')
        self.assertEqual(result.detail['bounds'], [95.0, 2254.0, 300.0, 2464.0])

    def test_combined_labels_require_same_unique_node(self):
        xml = '<root><node text="AC" content-desc="清除" focusable="true" bounds="[0,0][10,10]"/></root>'
        self.assertEqual(resolve(xml, 'AC 清除').detail['status'], 'resolved')
        self.assertEqual(resolve(xml, '清除 AC').detail['status'], 'resolved')
        self.assertEqual(resolve(xml, 'AC 清除全部').detail['status'], 'fallback')
        split = '<root><node text="AC" focusable="true" bounds="[0,0][10,10]"/><node content-desc="清除" focusable="true" bounds="[20,0][30,10]"/></root>'
        self.assertEqual(resolve(split, 'AC 清除').detail['status'], 'fallback')

    def test_focusable_button_role_resolves(self):
        xml = '<root><node text="1" focusable="true" clickable="false" bounds="[95,2254][300,2464]"/></root>'
        self.assertEqual(resolve(xml, '1 button').detail['status'], 'resolved')

    def test_disabled_invisible_and_ambiguous_fall_back(self):
        node = '<node text="1" focusable="true" clickable="false" bounds="[0,0][10,10]" {}/>'
        for nodes in (node.format('enabled="false"'), node.format('visible-to-user="false"'), node.format('') * 2):
            # Different bounds are needed for two distinct candidates.
            if nodes.count('<node') == 2:
                nodes = node.format('') + node.format('').replace('[0,0][10,10]', '[20,0][30,10]')
            self.assertEqual(resolve('<root>' + nodes + '</root>', '1 button').detail['status'], 'fallback')

    def test_long_description_does_not_match_short_label(self):
        xml = '<root><node text="1" focusable="true" bounds="[0,0][10,10]"/></root>'
        self.assertEqual(resolve(xml, 'press the 1 button').detail['status'], 'fallback')

if __name__ == '__main__': unittest.main()

class AmbiguousRowTests(unittest.TestCase):
    def test_ambiguity_includes_only_each_actionable_row_labels_and_bounds(self):
        xml = pathlib.Path(__file__).with_name('fixtures').joinpath('ambiguous-rows.xml').read_text()
        result = resolve(xml, 'Focus')
        self.assertEqual(result.detail['reason'], 'target_ambiguous')
        self.assertEqual(result.action.args['x'], 10)
        self.assertEqual(result.detail['bounds_coordinate_space'], 'screen_pixels')
        self.assertEqual([candidate['labels'] for candidate in result.detail['candidates']],
                         [['Focus', 'Sound'], ['Focus', 'Status icons'], ['Focus', 'Notifications']])
        self.assertEqual([candidate['bounds'] for candidate in result.detail['candidates']],
                         [[0.0, 0.0, 100.0, 100.0], [0.0, 100.0, 100.0, 200.0], [0.0, 200.0, 100.0, 300.0]])
