"""Admission rules for isolated phone Pi file benchmark fixtures, not a shell sandbox."""
import posixpath
import shlex


def admit_copy_command(command, workspace, commands=None):
    command = command.replace('2>/dev/null', '')
    if not command or any(c in command for c in ['$','`','>','<','~']) or '..' in command:
        return False
    lexer = shlex.shlex(command, posix=True, punctuation_chars=';&|\n')
    lexer.whitespace = ' \t\r'
    lexer.whitespace_split = True
    try:
        tokens = list(lexer)
    except ValueError:
        return False
    groups = [[]]
    for token in tokens:
        if token in ['&&','||','|',';','\n']:
            if groups[-1]: groups.append([])
        else:
            groups[-1].append(token)
    if commands is None: commands = {'ls','find','stat','mkdir','cp','date','pwd','head','sort','wc','echo','printf','cd'}
    for words in groups:
        if not words: continue
        if words[0] not in commands: return False
        if any(w in words for w in ['-exec','-execdir','-delete','-ok','-okdir']): return False
        if any(w.startswith('-') and '/' in w for w in words): return False
        if any(w.startswith('/') and w != workspace and not w.startswith(workspace+'/') for w in words): return False
    return bool(tokens)


def admit_delete_command(command, workspace, approved, clarified):
    if any(c in command for c in ['$','`','>','<','~']) or '..' in command:
        return False
    lexer=shlex.shlex(command,posix=True,punctuation_chars=';&|\n')
    lexer.whitespace=' \t\r';lexer.whitespace_split=True
    try:
        tokens=list(lexer)
    except ValueError:
        return False
    if 'rm' not in tokens:
        return admit_copy_command(command, workspace, {'ls','find','stat','pwd','head','cat','sort','wc','echo','printf','cd'})
    groups=[[]]
    for token in tokens:
        if token in [';','&&','\n']:
            if groups[-1]:groups.append([])
        elif token in ['|','||','&']:
            return False
        else:groups[-1].append(token)
    cwd=workspace
    approved_paths={workspace+'/Download/'+name for name in approved}
    for words in groups:
        if not words:continue
        if words[0]=='rm':
            paths=[w for w in words[1:] if w not in ['-f','--']]
            if not clarified or not paths:return False
            if any(posixpath.normpath(w if w.startswith('/') else cwd+'/'+w) not in approved_paths for w in paths):return False
        else:
            if not admit_copy_command(shlex.join(words),workspace,{'ls','find','stat','pwd','head','cat','sort','wc','echo','printf','cd'}):return False
            if words[0]=='cd':
                if len(words)!=2:return False
                cwd=posixpath.normpath(words[1] if words[1].startswith('/') else cwd+'/'+words[1])
                if cwd not in [workspace,workspace+'/Download']:return False
    return bool(tokens)
