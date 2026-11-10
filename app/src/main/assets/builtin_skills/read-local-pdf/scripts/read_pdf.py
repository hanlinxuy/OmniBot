#!/usr/bin/env python3
"""Bounded, read-only PDF extraction for a conversation workspace."""
import argparse
import json
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('path', type=Path)
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument('--pages', help='One-based pages, e.g. 1,3-5')
    selection.add_argument('--find', help='Case-insensitive literal phrase to locate across pages')
    parser.add_argument('--max-chars', type=int, default=20000, help='Total extracted-text character budget')
    args = parser.parse_args()
    if not 1 <= args.max_chars <= 100000:
        parser.error('--max-chars must be between 1 and 100000')
    if not args.path.is_file():
        parser.error('PDF file does not exist')
    sys.path.insert(0, str(Path.cwd() / '.pdf-tools'))
    try:
        from pypdf import PdfReader
    except ImportError:
        parser.exit(2, "pypdf is unavailable. Install in this workspace: python3 -m pip install --only-binary=:all: --target .pdf-tools 'pypdf==7.0.0'\n")
    reader = PdfReader(args.path)
    count = len(reader.pages)
    pages = list(range(min(3, count)))
    if args.pages:
        pages = []
        for part in args.pages.split(','):
            bounds = part.strip().split('-')
            if len(bounds) > 2 or not all(s.isdigit() for s in bounds):
                parser.error('--pages must contain page numbers or ranges')
            start, end = int(bounds[0]), int(bounds[-1])
            if not 1 <= start <= end <= count:
                parser.error('page range is outside the PDF')
            pages.extend(range(start - 1, end))
        pages = list(dict.fromkeys(pages))
    if args.find is not None and not args.find.strip():
        parser.error('--find must not be empty')
    results = []
    budget = args.max_chars
    omitted = 0
    for index in (range(count) if args.find is not None else pages):
        text = reader.pages[index].extract_text() or ''
        if args.find is not None:
            match = text.casefold().find(args.find.casefold())
            if match < 0:
                continue
            text = text[max(0, match - 250):match + len(args.find) + 500]
        if budget <= 0:
            omitted += 1
            continue
        result = text[:budget]
        results.append({'page': index + 1, 'text': result, 'truncated': len(result) < len(text), 'excerpt': args.find is not None})
        budget -= len(result)
    print(json.dumps({'file': args.path.name, 'pageCount': count, 'mode': 'search' if args.find is not None else 'pages', 'pages': results, 'omittedByBudget': omitted, 'defaultFirstThreePages': args.find is None and args.pages is None}, ensure_ascii=False))


if __name__ == '__main__':
    main()
