import unittest
from unittest.mock import patch
import datetime
import pathlib
import tempfile
from judge import Database, judge_error, judge


class JudgeTransportTest(unittest.TestCase):
    def test_compiled_source_helper_receives_timezone_dependency(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory)
            helper=root/'src/mobile_world/runtime/app_helpers/mastodon.py'
            task=root/'src/mobile_world/tasks/definitions/mastodon/example.py'
            helper.parent.mkdir(parents=True);task.parent.mkdir(parents=True)
            helper.write_text('def read_offset():\n    conn, cursor = connect_to_postgres()\n    cursor.execute("SELECT 1")\n    return pytz.timezone("Europe/London").localize(datetime(2025, 7, 1)).utcoffset().total_seconds()\n')
            task.write_text('class Example:\n    def is_successful(self, controller):\n        self._check_is_initialized()\n        return 1.0 if mastodon.read_offset() == 3600 else 0.0\n')
            with patch('judge.subprocess.check_output',return_value='[{"value":1}]'):
                self.assertEqual(judge(root,'example.py',['ssh','fixture'],'/owned/compose.json'),1.0)

    def test_duplicate_column_names_preserve_positional_sql_rows(self):
        queries=[];db=Database(['ssh','fixture'],'/owned/compose.json',queries)
        with patch('judge.subprocess.check_output',return_value='[{"id":1,"created_at":"2025-10-12T06:11:09.73076","id":2}]'):
            db.execute('SELECT id, created_at, id FROM fixture')
        row=db.fetchone()
        self.assertEqual(len(row),3)
        self.assertEqual((row[0],row[2]),(1,2))
        self.assertEqual(row[1],datetime.datetime(2025,10,12,6,11,9,730760))
        self.assertEqual(queries,[{'completed':True}])

    def test_helper_error_cannot_be_scored_as_model_failure(self):
        with self.assertRaisesRegex(RuntimeError,'Source SQL helper failed'):
            judge_error('transport failure')

if __name__=='__main__':unittest.main()
