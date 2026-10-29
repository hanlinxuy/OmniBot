import importlib.util
import pathlib
import sys
import unittest
import zipfile

ROOT = pathlib.Path.cwd()
def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module

class ContextTests(unittest.TestCase):
    def test_calculator_original_and_candidate(self):
        fixture = ROOT / '.session-history/full-scenario-scan/BA19-before-1.xml'
        if not fixture.exists(): self.skipTest('private real-device fixture unavailable')
        import tempfile
        with zipfile.ZipFile(ROOT / 'artifacts/omniflow-gui-runtime-2.1.8.zip') as z, tempfile.TemporaryDirectory() as d:
            source = pathlib.Path(d) / 'context.py'
            source.write_bytes(z.read('scripts/runtime/python/omniflow/vlm/context.py'))
            original = load('context_original', source)
            candidate = load('context_candidate', pathlib.Path(__file__).with_name('context.py'))
            state = {'xml': fixture.read_text(), 'display': {'width': 1260, 'height': 2800}}
            before = original.analyze_page_context(state).evidence
            after = candidate.analyze_page_context(state).evidence
            for label in ('1', '2', '3', 'AC'):
                self.assertNotIn(f'text="{label}"', before)
                self.assertIn(f'text="{label}"', after)
            self.assertLessEqual(len(after.splitlines()) - 1, 48)
            self.assertIn('copy the exact text or desc label', after)

    def test_priority_visibility_and_limit(self):
        module = load('context_synthetic', pathlib.Path(__file__).with_name('context.py'))
        nodes = ''.join(f'<node resource-id="container{i}"/>' for i in range(50))
        nodes += '<node text="hidden" focusable="true" visible-to-user="false"/><node text="disabled" focusable="true" enabled="false"/>'
        nodes += '<node text="1" focusable="true"/><node text="label"/>'
        evidence = module.analyze_page_context({'xml': f'<root>{nodes}</root>'}, limit=100).evidence
        self.assertLessEqual(len(evidence.splitlines()) - 1, 48)
        self.assertLess(evidence.index('text="1"'), evidence.index('text="label"'))
        self.assertLess(evidence.index('text="label"'), evidence.index('container0'))
        self.assertNotIn('hidden', evidence)
        self.assertNotIn('disabled', evidence)

if __name__ == '__main__': unittest.main()
