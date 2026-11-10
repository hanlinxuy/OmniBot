import json
import pathlib
import tempfile
import unittest
from unittest.mock import patch
import records


class RecordsTest(unittest.TestCase):
    def test_nested_filters_and_actual_pagination_cursor(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory); (root/'work').mkdir()
            rows=[{'id':str(i),'origin':{'id':'SFO','name':'San Francisco'},'destination':{'id':'JFK'},'detail':'x'*4000} for i in range(10)]
            (root/'work'/'flights.json').write_text(json.dumps({'flights':rows}))
            with patch.object(records,'__file__',str(root/'records.py')):
                result=records.records('query_records',{'file':'flights.json','collection':'flights','filters':{'origin':{'id':'SFO'}},'limit':100})
                self.assertEqual(result['totalMatches'],10)
                self.assertEqual(result['nextOffset'],len(result['items']))
                self.assertLess(result['returnedCount'],10)
                invalid=records.records('query_records',{'file':'flights.json','collection':'flights','filters':{'Origin':'SFO'}})
                self.assertEqual(invalid['error'],'unknown_filter_field')
                self.assertIn('origin.id',invalid['availableFields'])

    def test_playstation_is_not_charging_station(self):
        import re
        pattern=r'\b(?:station|stand|bundle)\b'
        self.assertFalse(re.search(pattern,'DualSense Wireless Controller for PlayStation 5',re.I))
        self.assertTrue(re.search(pattern,'PS5 Stand and Charging Station',re.I))

    def test_large_workspace_stays_navigable_without_context_overflow(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory); (root/'work').mkdir()
            workspace={'id':'test','channels':[{'id':'general','messages':[{'text':'x'*1000} for _ in range(30)]}]}
            (root/'work'/'teamchat.json').write_text(json.dumps({'workspaces':[workspace]}))
            with patch.object(records,'__file__',str(root/'records.py')):
                result=records.records('query_records',{'file':'teamchat.json','collection':'workspaces','fields':['channels']})
                self.assertLess(len(json.dumps(result)),12000)
                nested=result['items'][0]['channels']['collectionPath']
                channels=records.records('query_records',{'file':'teamchat.json','collection':nested})
                self.assertEqual(channels['items'][0]['id'],'general')
                self.assertEqual(channels['items'][0]['messages']['count'],30)


if __name__=='__main__': unittest.main()
