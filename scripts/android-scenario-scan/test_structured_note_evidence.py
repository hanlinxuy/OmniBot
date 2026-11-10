import unittest
from structured_note_evidence import verify_added_bullet


class StructuredNoteEvidenceTest(unittest.TestCase):
    def test_insertion_in_section_preserves_later_content(self):
        before = 'Today:\n- Existing task\n\nBlockers:\n- Waiting'
        after = 'Today:\n- Existing task\n- Completed a bug fix today\n\nBlockers:\n- Waiting'
        result = verify_added_bullet(before, after, 'Today')
        self.assertTrue(result['effectPassed'])
        self.assertTrue(result['sectionMatches'])

    def test_append_under_wrong_section_is_not_a_placement_pass(self):
        before = 'Today:\n- Existing task\n\nBlockers:\n- Waiting'
        result = verify_added_bullet(before, before+'\n- Completed a bug fix today', 'Today')
        self.assertTrue(result['effectPassed'])
        self.assertFalse(result['sectionMatches'])
        self.assertEqual('Blockers', result['actualSection'])

    def test_replacement_or_duplicate_additions_do_not_pass(self):
        self.assertFalse(verify_added_bullet('Today:\n- Original', 'Today:\n- Completed fix')['effectPassed'])
        self.assertFalse(verify_added_bullet('Today:', 'Today:\n- Fix\n- Fix')['effectPassed'])
