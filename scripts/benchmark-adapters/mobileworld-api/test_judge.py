import unittest
from unittest.mock import patch
import datetime
from judge import Database, judge_error


class JudgeTransportTest(unittest.TestCase):
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
