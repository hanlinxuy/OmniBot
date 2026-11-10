import unittest
from admission import admit_workspace_command


class AdmissionTest(unittest.TestCase):
    def test_multiline_text_is_one_argument(self):
        script = '/tmp/owned/workspace.py'
        self.assertTrue(admit_workspace_command('python3 '+script+' append "Mobile Launch Plan.txt" "Q2 Planning:\n- Review feedback\n- Plan release"', script))
        self.assertTrue(admit_workspace_command("python3 "+script+" append 'x.txt' 'Price $4; literal `text`'", script))

    def test_shell_execution_and_wrong_capability_rejected(self):
        script = '/tmp/owned/workspace.py'
        for command in ['python3 '+script+' list\nwhoami', 'python3 '+script+' list; whoami',
                        'python3 '+script+' append x.txt "$(whoami)"', 'python3 '+script+' append x.txt "`whoami`"',
                        'python3 /tmp/other.py list', 'python3 '+script+' list > x.txt',
                        'python3 '+script+' read x.txt extra']:
            with self.subTest(command=command):
                self.assertFalse(admit_workspace_command(command, script))


if __name__ == '__main__':
    unittest.main()
