"""Readback controls against the patched source; no simulator required."""
import ast
import copy
import os
from pathlib import Path
from types import SimpleNamespace
import unittest


class DocumentReadTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ['IOSWORLD_SOURCE']) / 'mcps/clouddocs.py'
        node = next(n for n in ast.parse(path.read_text()).body
                    if isinstance(n, ast.FunctionDef) and n.name == 'read_document')
        node.decorator_list = []
        cls.namespace = {}
        exec(compile(ast.Module(body=[node], type_ignores=[]), str(path), 'exec'), cls.namespace)

    def setUp(self):
        self.doc = {'id': 'owned', 'title': 'Owned', 'body': '开头\n' + 'x' * 1800 + '\n尾部'}
        self.original = copy.deepcopy(self.doc)
        self.namespace.update(dl=SimpleNamespace(read_envelope=lambda: {'documents': [self.doc]}),
                              _find_document=lambda env, title: self.doc if title == 'Owned' else None,
                              _doc_not_found=lambda *_: {'ok': False, 'message': 'not found'})

    def test_pages_reconstruct_saved_body_without_writing(self):
        offset, chunks = 0, []
        while offset is not None:
            result = self.namespace['read_document']('Owned', offset, 700)
            self.assertTrue(result['ok'])
            self.assertLessEqual(len(result['text']), 700)
            chunks.append(result['text'])
            offset = result['next_offset']
        self.assertEqual(''.join(chunks), self.doc['body'])
        self.assertEqual(self.original, self.doc)

    def test_invalid_bounds_and_missing_document_do_not_create(self):
        for title, offset, limit in [('Owned', -1, 100), ('Owned', 0, 1501),
                                     ('Owned', 9999, 100), ('Missing', 0, 100)]:
            self.assertFalse(self.namespace['read_document'](title, offset, limit)['ok'])
        self.assertEqual(self.original, self.doc)


if __name__ == '__main__':
    unittest.main()
