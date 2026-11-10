---
name: read-local-pdf
description: Read and search text in downloaded PDF documents on the phone, including reports, invoices and papers, using local file tools before GUI. Use for content questions about an existing PDF.
---

# Read a local PDF

Resolve the actual file the user supplied. Do not replace reading it with web search or recalled facts. Work in the current conversation workspace; preserve the source PDF.

Android's Alpine terminal may lack PDF packages. Prefer a pure Python parser for text PDFs. Compiling PyMuPDF or installing a desktop PDF stack can take minutes and is unnecessary for ordinary text extraction. If `pypdf` is absent, install it into the current workspace, not the global Python environment:

```sh
python3 -m pip install --only-binary=:all: --target .pdf-tools 'pypdf==7.0.0'
```

Use the bundled helper by its absolute skill path. It also loads `.pdf-tools` from the current working directory. It does not install dependencies itself:

```sh
python3 <skill-directory>/scripts/read_pdf.py ./Download/report.pdf
python3 <skill-directory>/scripts/read_pdf.py ./Download/report.pdf --find 'evaluation'
python3 <skill-directory>/scripts/read_pdf.py ./Download/report.pdf --pages 4-6
```

The default reads only the first three pages. Search locates relevant pages and, when a literal match is absent, tolerates PDF whitespace, apostrophes and hyphenation. `matchMode` distinguishes literal and typography matches while excerpts preserve the original text; read those complete pages to check tables, captions and footnotes. Output reports omitted characters/pages. A truncated extract is not the whole document. For arithmetic, use the actual extracted values and a calculation tool, then follow the user's requested answer format.

Text extraction can lose table column alignment. Check labels and surrounding rows before attributing numbers. If the page has no extractable text, or the layout remains ambiguous, use an available PDF renderer/OCR or GUI on the original file; report the limitation if those capabilities are unavailable. Do not guess from an empty extract.

Give shell commands a finite timeout. If dependency installation fails, retain its error and choose a supported parser; do not start an unbounded source build. This workflow does not grant access to unrelated phone files or change authorization for external actions.
