import unittest
from workspace_evidence import normalized_spreadsheets


class WorkspaceEvidenceTest(unittest.TestCase):
    def test_default_serialization_is_equivalent_without_mutating_evidence(self):
        before = [{'id': 'sheet', 'sheets': [{'cells': [{'address': 'A1', 'rawValue': 'Confirmed'}]}]}]
        after = normalized_spreadsheets(before)
        self.assertEqual(normalized_spreadsheets(before), normalized_spreadsheets(after))
        self.assertNotIn('style', before[0]['sheets'][0]['cells'][0])
        after[0]['sheets'][0]['cells'][0]['value'] = 'Confirmed'
        self.assertEqual(normalized_spreadsheets(before), normalized_spreadsheets(after))
        after[0]['sheets'][0]['cells'][0]['value'] = 'Conflicting alias'
        self.assertNotEqual(normalized_spreadsheets(before), normalized_spreadsheets(after))

    def test_explicit_style_and_content_changes_remain_visible(self):
        before = [{'id': 'sheet', 'sheets': [{'cells': [{'address': 'A1', 'rawValue': 'Confirmed'}]}]}]
        for field, value in [('bold', True), ('alignment', 'center'), ('fill', 'blue')]:
            after = normalized_spreadsheets(before)
            after[0]['sheets'][0]['cells'][0]['style'][field] = value
            self.assertNotEqual(normalized_spreadsheets(before), normalized_spreadsheets(after))
        after = normalized_spreadsheets(before)
        after[0]['sheets'][0]['cells'][0]['rawValue'] = 'Pending'
        self.assertNotEqual(normalized_spreadsheets(before), normalized_spreadsheets(after))


if __name__ == '__main__':
    unittest.main()
