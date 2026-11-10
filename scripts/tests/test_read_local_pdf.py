import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from pypdf import PdfWriter
from pypdf.generic import DictionaryObject, NameObject, DecodedStreamObject

SCRIPT = Path(__file__).resolve().parents[2] / 'app/src/main/assets/builtin_skills/read-local-pdf/scripts/read_pdf.py'


class ReadLocalPdfTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.pdf = Path(self.temp.name) / 'fixture.pdf'
        writer = PdfWriter()
        font = writer._add_object(DictionaryObject({NameObject('/Type'): NameObject('/Font'), NameObject('/Subtype'): NameObject('/Type1'), NameObject('/BaseFont'): NameObject('/Helvetica')}))
        for text in ['First page invoice', 'Interest applies monthly', 'Third page', 'Appendix fourth page']:
            page = writer.add_blank_page(width=600, height=800)
            stream = DecodedStreamObject()
            stream.set_data(f'BT /F1 12 Tf 40 700 Td ({text}) Tj ET'.encode())
            page[NameObject('/Resources')] = DictionaryObject({NameObject('/Font'): DictionaryObject({NameObject('/F1'): font})})
            page[NameObject('/Contents')] = writer._add_object(stream)
        writer.write(self.pdf)
        self.original = hashlib.sha256(self.pdf.read_bytes()).hexdigest()

    def invoke(self, *args):
        result = subprocess.run([sys.executable, str(SCRIPT), str(self.pdf), *args], cwd=self.temp.name, text=True, capture_output=True)
        self.assertEqual(self.original, hashlib.sha256(self.pdf.read_bytes()).hexdigest())
        return result

    def test_default_is_explicitly_partial_and_read_only(self):
        result = self.invoke()
        self.assertEqual(0, result.returncode, result.stderr)
        output = json.loads(result.stdout)
        self.assertEqual(4, output['pageCount'])
        self.assertEqual([1, 2, 3], [page['page'] for page in output['pages']])
        self.assertTrue(output['defaultFirstThreePages'])

    def test_search_locates_and_complete_page_reads_original_text(self):
        found = json.loads(self.invoke('--find', 'INTEREST').stdout)
        self.assertEqual([2], [page['page'] for page in found['pages']])
        self.assertTrue(found['pages'][0]['excerpt'])
        page = json.loads(self.invoke('--pages', '2').stdout)['pages'][0]
        self.assertIn('Interest applies monthly', page['text'])
        self.assertFalse(page['excerpt'])

    def test_budget_marks_truncation_and_omitted_pages(self):
        output = json.loads(self.invoke('--pages', '1-4', '--max-chars', '5').stdout)
        self.assertEqual(5, sum(len(page['text']) for page in output['pages']))
        self.assertTrue(output['pages'][0]['truncated'])
        self.assertEqual(3, output['omittedByBudget'])

    def test_invalid_ranges_and_selection_are_rejected(self):
        for args in [('--pages', '0'), ('--pages', '5'), ('--pages', '3-2'), ('--pages', 'a'), ('--find', ''), ('--max-chars', '0'), ('--pages','1','--find','x')]:
            with self.subTest(args=args):
                self.assertEqual(2, self.invoke(*args).returncode)


if __name__ == '__main__':
    unittest.main()
