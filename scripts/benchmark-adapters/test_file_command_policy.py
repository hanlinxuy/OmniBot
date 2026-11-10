import unittest
from file_command_policy import admit_copy_command, admit_delete_command


class FileCommandPolicyTest(unittest.TestCase):
    def test_delete_sequences_and_scope(self):
        cases = [
            ('rm Download/a.txt; ls -la Download', True, True),
            ('rm Download/a.txt', False, False),
            ('rm Download/b.txt', True, False),
            ('rm -rf Download', True, False),
            ('cd Download && rm a.txt && ls', True, True),
            ('cat Download/a.txt', False, True),
            ('sort Download/a.txt | wc -l', False, True),
            ('rm Download/a.txt | wc -l', True, False),
            ('rm Download/a.txt; rm Download/b.txt', True, False),
            ('rm /tmp/outside/a.txt', True, False),
            ('rm Download/*.txt', True, False),
            ('ls "unterminated', True, False),
            ('cd Download && cd Download && rm a.txt', True, False),
            ('find Download -delete', True, False),
        ]
        for command, clarified, expected in cases:
            with self.subTest(command=command, clarified=clarified):
                self.assertEqual(admit_delete_command(command, '/tmp/test/work', ['a.txt'], clarified), expected)

    def test_copy_quoted_format_and_mutation_exclusions(self):
        cases = [
            ("stat -c '%y %n' Download/*.pdf", True),
            ('ls Download | sort', True),
            ('mkdir -p Finance/invoice && cp Download/a.pdf Finance/invoice/', True),
            ('rm Download/a.pdf', False),
            ('find Download -exec rm {} \;', False),
            ('ls /sdcard/Download', False),
            ('ls ../../Download', False),
            ('echo $(cat secret)', False),
            ('ls "unterminated', False),
        ]
        for command, expected in cases:
            with self.subTest(command=command):
                self.assertEqual(admit_copy_command(command, '/tmp/test/work'), expected)


if __name__ == '__main__':
    unittest.main()
