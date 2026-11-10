"""Compare persisted note bodies without confusing insertion with replacement."""
from difflib import SequenceMatcher


def verify_added_bullet(before, after, expected_section=None):
    old_lines, new_lines = before.splitlines(), after.splitlines()
    changes = [op for op in SequenceMatcher(a=old_lines, b=new_lines, autojunk=False).get_opcodes() if op[0] != 'equal']
    preserved = bool(changes) and all(op[0] == 'insert' for op in changes)
    additions = [(i, new_lines[i]) for _, _, _, start, end in changes for i in range(start, end) if new_lines[i].strip()]
    one_bullet = len(additions) == 1 and additions[0][1].lstrip().startswith(('- ', '* ', '• '))
    section = None
    if one_bullet:
        for line in new_lines[:additions[0][0]]:
            text = line.strip()
            if text.endswith(':') or text.startswith('#'):
                section = text.lstrip('#').strip().rstrip(':')
    return {
        'originalContentPreserved': preserved,
        'oneBulletAdded': one_bullet,
        'addedText': additions[0][1] if one_bullet else None,
        'actualSection': section,
        'sectionMatches': None if expected_section is None else section == expected_section,
        'effectPassed': preserved and one_bullet,
        'note': 'Text/placement evidence only; factual accuracy requires the supplied task/source facts.',
    }
