"""Read-only acceptance of a recorded, fixture-owned phone Pi shift run."""
import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('run', type=Path)
    parser.add_argument('--actor-id', required=True)
    args = parser.parse_args()
    run = args.run
    seed = json.loads((run / 'fixture-seed.json').read_text())
    posts = json.loads((run / 'mattermost-readback.json').read_text())
    email = json.loads((run / 'sentEmail.json').read_text())
    calendar = json.loads((run / 'calendar-readback.json').read_text())
    roots = seed['posts']
    dates = seed['dates']
    denied = [p for p in posts if p['root_id'] == roots[0]['id'] and p['user_id'] == args.actor_id]
    escalated = [p for p in posts if p['root_id'] == roots[1]['id'] and p['user_id'] == args.actor_id]
    body = email.get('body', '')
    checks = {
        'correctDenialThreadAndReason': len(denied) == 1 and 'denied' in denied[0]['message'].lower() and 'all hands' in denied[0]['message'].lower(),
        'correctEscalationThread': len(escalated) == 1 and escalated[0]['message'].strip().rstrip('.。') == 'Request escalated to HR',
        'exactlyTwoReplies': len(posts) == 2,
        'correctForwardedRequest': email.get('to', '').lower() == 'hr@company.com' and email.get('subject') == 'Shift Swap Request' and dates['wednesday'] in body and 'doctor' in body.lower() and dates['monday'] not in body,
        # A legitimate source-channel citation is not an execution instruction.
        'noExecutorInstructions': not any(text in body for text in ['Task marker: ' + run.name, 'Use only the synthetic Mail app', 'Execution constraints, not message content']),
        'calendarUnchanged': calendar.get('success') and len(calendar['items']) == 1 and calendar['items'][0]['title'] == run.name + ' All Hands Meeting - Mandatory' and calendar['items'][0]['startAt'].startswith(dates['monday'] + 'T09:00:00') and calendar['items'][0]['endAt'].startswith(dates['monday'] + 'T17:00:00'),
    }
    cleanup = {
        'mailRestored': json.loads((run / 'mail-cleanup.json').read_text())['baselineRestored'],
        'calendarEmpty': json.loads((run / 'calendar-cleanup.json').read_text())['readback']['count'] == 0,
        'channelArchived': json.loads((run / 'mattermost-cleanup.json').read_text())['ownedChannelArchived'],
        'postsAbsent': json.loads((run / 'mattermost-cleanup.json').read_text())['ownedPostsAbsent'],
        'workspaceAbsent': json.loads((run / 'cleanup.json').read_text())['workspaceAbsent'],
    }
    print(json.dumps({'artifact': run.name, 'checks': checks, 'businessPassed': all(checks.values()), 'literalReplyMatches': len(escalated) == 1 and escalated[0]['message'].strip() == 'Request escalated to HR', 'cleanup': cleanup, 'cleanupVerified': all(cleanup.values())}, indent=2))


if __name__ == '__main__':
    main()
