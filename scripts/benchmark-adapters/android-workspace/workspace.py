"""Small file capability for phone Pi; root is fixed beside this adapter.

Usage: python3 workspace.py list|read FILE|set FILE SHEET CELL VALUE|append FILE TEXT
Only owned work/ files are exposed. This is a benchmark capability, not a shell sandbox.
"""
import json
import pathlib
import sys
import re


def spreadsheet_value(raw):
    if re.fullmatch(r"[+-]?(?:\d+(?:\.\d*)?|\.\d+)", raw):
        return float(raw) if "." in raw else int(raw)
    return raw


def main():
    root = pathlib.Path(__file__).resolve().parent / 'work'
    action, *args = sys.argv[1:]
    if action == 'list' and not args:
        print(json.dumps(sorted(p.name for p in root.iterdir() if p.is_file())))
        return
    if action not in ('read', 'set', 'append', 'bullet', 'add-slide', 'star') or len(args) != {'read': 1, 'set': 4, 'append': 2, 'bullet': 3, 'add-slide': 3, 'star': 2}[action]:
        raise ValueError('Usage: list | read FILE | set FILE SHEET CELL VALUE | append FILE TEXT')
    path = (root / args[0]).resolve()
    if path.parent != root.resolve() or not path.is_file():
        raise ValueError('File must be an existing direct child of the owned work directory')
    if action == 'star' and path.name != 'drive.json':
        raise ValueError('Star requires file=drive.json and the selected metadata file_id; query drive.json collection files for IDs')
    if path.suffix == '.xlsx':
        import openpyxl
        book = openpyxl.load_workbook(path)
        if action == 'read':
            print(json.dumps({'file':path.name,'sheetNames':book.sheetnames,'sheets':[{'name':s.title,'cells':{c.coordinate:c.value for row in s for c in row if c.value is not None}} for s in book]},ensure_ascii=False))
        elif action == 'set':
            sheet, cell, value = args[1:]
            if sheet not in book.sheetnames:
                print(json.dumps({'error':'worksheet_not_found','requestedSheet':sheet,'availableSheetNames':book.sheetnames,'retryHint':'Use an exact availableSheetNames value; do not guess or create a sheet.'}))
                sys.exit(1)
            book[sheet][cell] = spreadsheet_value(value)
            book.save(path)
            saved = openpyxl.load_workbook(path)
            print(json.dumps({'file': path.name, 'sheet': sheet, 'cell': cell, 'savedValue': saved[sheet][cell].value}))
        else:
            raise ValueError('Use set for a spreadsheet')
    elif path.name == 'drive.json' and action in ('read', 'star'):
        data=json.loads(path.read_text())
        if action == 'star':
            item=next(x for x in data['files'] if x['id']==args[1])
            item['starred']=True
            path.write_text(json.dumps(data,ensure_ascii=False,indent=2))
        if action == 'star':
            print(json.dumps({'savedFile':item},ensure_ascii=False))
        else:
            print(json.dumps({'folders':data['folders'],'fileCount':len(data['files']),'hint':'Use query_records collection files to search or paginate file metadata.'},ensure_ascii=False))
    elif path.suffix == '.json' and action == 'read':
        from records import records
        print(json.dumps(records('query_records',{'file':path.name}),ensure_ascii=False))
    elif path.suffix == '.pptx':
        from presentations import presentation
        print(json.dumps(presentation(path, action, args[1:]), ensure_ascii=False))
    elif (path.suffix == '.txt' and action in ('read', 'append')) or (path.suffix == '.csv' and action == 'read'):
        if action == 'append':
            with path.open('a') as out:
                out.write('\n' + args[1] + '\n')
        print(path.read_text())
    else:
        raise ValueError('Supported formats: xlsx, pptx and txt')


if __name__ == '__main__':
    main()
