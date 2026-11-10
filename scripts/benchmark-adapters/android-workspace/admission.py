"""Admission for one fixed workspace capability, not arbitrary shell commands."""
import re
import shlex


def admit_workspace_command(command, script):
    # Quoted newlines are text, while unquoted newlines start another shell command.
    word = r'''(?:[A-Za-z0-9_./:=+-]+|'[^']*'|"[^"$`\\]*")'''
    if not re.fullmatch(word + r'(?:[ \t]+' + word + r')*', command):
        return False
    tokens = shlex.split(command)
    if len(tokens) < 3 or tokens[:2] != ['python3', script]:
        return False
    return len(tokens) == {'list': 3, 'read': 4, 'set': 7, 'append': 5}.get(tokens[2], 0)
