"""Small file capability for phone Pi; root is fixed beside this adapter.

Usage: python3 workspace.py list|read FILE|set FILE SHEET CELL VALUE|append FILE TEXT
Only owned work/ files are exposed. This is a benchmark capability, not a shell sandbox.
"""
import json
import pathlib
import sys


def main():
    root = pathlib.Path(__file__).resolve().parent / 'work'
    action, *args = sys.argv[1:]
    if action == 'list' and not args:
        print(json.dumps(sorted(p.name for p in root.iterdir() if p.is_file())))
        return
    if action not in ('read', 'set', 'append') or len(args) != {'read': 1, 'set': 4, 'append': 2}[action]:
        raise ValueError('Usage: list | read FILE | set FILE SHEET CELL VALUE | append FILE TEXT')
    path = (root / args[0]).resolve()
    if path.parent != root.resolve() or not path.is_file():
        raise ValueError('File must be an existing direct child of the owned work directory')
    if path.suffix == '.xlsx':
        import openpyxl
        book = openpyxl.load_workbook(path)
        if action == 'read':
            print(json.dumps({s.title: {c.coordinate: c.value for row in s for c in row if c.value is not None} for s in book}, ensure_ascii=False))
        elif action == 'set':
            sheet, cell, value = args[1:]
            book[sheet][cell] = value
            book.save(path)
            saved = openpyxl.load_workbook(path)
            print(json.dumps({'file': path.name, 'sheet': sheet, 'cell': cell, 'savedValue': saved[sheet][cell].value}))
        else:
            raise ValueError('Use set for a spreadsheet')
    elif path.suffix == '.txt' and action in ('read', 'append'):
        if action == 'append':
            with path.open('a') as out:
                out.write('\n' + args[1] + '\n')
        print(path.read_text())
    else:
        raise ValueError('Supported formats: xlsx and txt')


if __name__ == '__main__':
    main()
