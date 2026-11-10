import json
import pathlib
import tempfile
import unittest
from unittest.mock import patch
import records


class OwnedActionsTest(unittest.TestCase):
    def test_spreadsheet_numbers_remain_numeric_for_totals(self):
        from workspace import spreadsheet_value
        self.assertEqual(spreadsheet_value('249.45'),249.45)
        self.assertEqual(spreadsheet_value('981'),981)
        self.assertEqual(spreadsheet_value('=SUM(B2:B8)'),'=SUM(B2:B8)')
        self.assertEqual(spreadsheet_value('Pending'),'Pending')

    def test_order_preserves_history_and_uses_source_fees(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory);(root/'work').mkdir()
            data={'addresses':[{'id':'home','detail':'410 Brannan St'}],'pastOrders':[{'id':'old'}],'restaurants':[{'id':'r','name':'Chipotle','distanceMiles':2.1,'menu':[{'items':[{'id':'bowl','name':'Chicken Bowl','price':11.75}]}]}]}
            p=root/'work/quickbite.json';p.write_text(json.dumps(data))
            with patch.object(records,'__file__',str(root/'records.py')):
                with self.assertRaises(ValueError): records.records('place_food_order',{'file':p.name,'restaurant_id':'r','address_id':'home','items':{'foreign':1}})
                self.assertEqual(json.loads(p.read_text()),data)
                result=records.records('place_food_order',{'file':p.name,'restaurant_id':'r','address_id':'home','items':{'bowl':1}})
                self.assertEqual(result['totals']['total'],20.70)
                after=json.loads(p.read_text());self.assertEqual(after.pop('activeOrders')[0]['items'][0]['quantity'],1);self.assertEqual(after,data)

    def test_document_refuses_overwrite_and_survives_readback(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory);(root/'work').mkdir();(root/'work/documents.json').write_text('{"documents":[]}')
            with patch.object(records,'__file__',str(root/'records.py')):
                result=records.records('create_document',{'file':'documents.json','title':'Interview Prep','text':'Evidence'})
                self.assertEqual((root/'work/Interview Prep.txt').read_text(),'Evidence')
                self.assertTrue(result['savedDocument']['shareLink'].startswith('fixture://documents/'))
                for title in ['Interview Prep','../escaped']:
                    with self.assertRaises(ValueError):records.records('create_document',{'file':'documents.json','title':title,'text':'overwrite'})
                self.assertEqual((root/'work/Interview Prep.txt').read_text(),'Evidence')

    def test_thread_marks_other_person_without_changing_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory);(root/'work').mkdir()
            data={'currentUserID':'me','chats':[{'id':'chat','messages':[{'senderID':'friend','content':{'text':'I write poetry'}},{'senderID':'me','content':{'text':'I would like to read it'}}]}]}
            p=root/'work/quickchat.json';p.write_text(json.dumps(data))
            with patch.object(records,'__file__',str(root/'records.py')):
                result=records.records('read_thread',{'file':p.name,'thread_id':'chat'})
                self.assertEqual([x['isFromCurrentUser'] for x in result['messages']],[False,True]);self.assertEqual(json.loads(p.read_text()),data)

if __name__=='__main__':unittest.main()
