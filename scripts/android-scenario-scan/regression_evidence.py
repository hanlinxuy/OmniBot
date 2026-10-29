"""Independent, offline evidence checks for Android regression runs.

These checks report observations; they do not perform actions or infer task success
from a model's claim. Calendar inputs use normalized event/reminder dictionaries.
"""
import re
import unicodedata
import xml.etree.ElementTree as ET


def parse_screen(xml, package):
    root = ET.fromstring(xml)
    return [dict(node.attrib) for node in root.iter('node') if node.get('package') == package]


def verify_clock_timer(xml, marker, duration_seconds=None, elapsed_seconds=None, tolerance_seconds=5):
    """Check the observed vivo Clock timer row and its own active controls."""
    if not marker.strip():
        raise ValueError('marker required')
    root = ET.fromstring(xml)
    active_count = 0
    target_count = 0
    remaining_seconds = None
    if (duration_seconds is None) != (elapsed_seconds is None):
        raise ValueError('duration and elapsed must be supplied together')
    if duration_seconds is not None and (duration_seconds <= 0 or elapsed_seconds < 0 or tolerance_seconds < 0):
        raise ValueError('invalid timer timing')
    for node in root.iter('node'):
        if node.get('package') != 'com.android.BBKClock' or node.get('resource-id') != 'com.android.BBKClock:id/cl_content_layout':
            continue
        children = list(node.iter('node'))
        is_target = any(child.get('resource-id') == 'com.android.BBKClock:id/tv_label' and child.get('text') == marker for child in children)
        if not is_target:
            continue
        target_count += 1
        pause = any(child.get('resource-id') == 'com.android.BBKClock:id/fl_timer_count' and child.get('content-desc') == '暂停' for child in children)
        close = any(child.get('resource-id') == 'com.android.BBKClock:id/iv_delete' and child.get('content-desc') == '关闭' for child in children)
        descriptions = [child.get('content-desc', '') for child in children if child.get('resource-id') == 'com.android.BBKClock:id/view_start_pause']
        remaining = len(descriptions) == 1 and descriptions[0].startswith('剩余')
        if remaining:
            match = re.fullmatch(r'剩余(?:(\d+)小时)?(?:(\d+)分钟)?(?:(\d+)秒)?', descriptions[0])
            if match and any(value is not None for value in match.groups()):
                hours, minutes, seconds = (int(value or 0) for value in match.groups())
                if minutes < 60 and seconds < 60:
                    remaining_seconds = hours * 3600 + minutes * 60 + seconds
        if pause and close and remaining:
            active_count += 1
    timing_matches = None if duration_seconds is None else (
        target_count == 1 and remaining_seconds is not None and remaining_seconds > 0 and
        abs(remaining_seconds - (duration_seconds - elapsed_seconds)) <= tolerance_seconds
    )
    result = {'target_present': target_count > 0, 'target_count': target_count, 'active_count': active_count,
              'passed': target_count == 1 and active_count == 1 and timing_matches is not False}
    if duration_seconds is not None:
        result.update(remaining_seconds=remaining_seconds, timing_matches=timing_matches)
    return result


def verify_alarm(xml, marker, hour=7, minute=0):
    if not marker.strip():
        raise ValueError('marker required')
    root = ET.fromstring(xml)
    expected = f'{hour:02d}:{minute:02d}'
    matches = []
    for node in root.iter('node'):
        if node.get('package') != 'com.android.BBKClock':
            continue
        desc = node.get('content-desc', '')
        fields = [field.strip() for field in re.split(r'[,，]', desc)]
        if marker in fields and expected in fields and '开关' in fields:
            matches.append(fields)
    enabled = len(matches) == 1 and '已开启' in matches[0]
    daily = len(matches) == 1 and '每天' in matches[0]
    return {'exact_count': len(matches), 'enabled': enabled, 'daily': daily,
            'passed': len(matches) == 1 and enabled and daily}


def verify_calendar(events, reminders, marker, start_ms, end_ms, calendar_id, expected_timezone=None):
    if not marker.strip():
        raise ValueError('marker required')
    matches = [event for event in events if event.get('title') == marker]
    exact_count = len(matches)
    event = matches[0] if exact_count == 1 else None
    time_matches = bool(event and event.get('start_ms') == start_ms and event.get('end_ms') == end_ms and event.get('calendar_id') == calendar_id)
    target_reminders = [row for row in reminders if event and event.get('id') is not None and row.get('event_id') == event['id']]
    reminder_60 = len(target_reminders) == 1 and target_reminders[0].get('minutes') == 60
    timezone_matches = None if expected_timezone is None else bool(event and event.get('timezone') == expected_timezone)
    result = {'exact_count': exact_count, 'time_matches': time_matches, 'reminder_60': reminder_60,
              'passed': exact_count == 1 and time_matches and reminder_60 and timezone_matches is not False}
    if expected_timezone is not None:
        result['timezone_matches'] = timezone_matches
    return result


def verify_note_lines(ocrrows, body_rect=None):
    """Require a caller-verified normalized body rectangle (left, top, right, bottom)."""
    if body_rect is None:
        return {'passed': False, 'reason': 'body_region_unverified'}
    left, top, right, bottom = body_rect
    if not (0 <= left < right <= 1 and 0 <= top < bottom <= 1):
        raise ValueError('invalid body rectangle')
    rows = [row for row in ocrrows if left <= row['x'] and top <= row['y'] and
            row['x'] + row['width'] <= right and row['y'] + row['height'] <= bottom]
    rows.sort(key=lambda row: (row['y'], row['x']))
    lines = [''.join(unicodedata.normalize('NFKC', row['text']).split()) for row in rows]
    return {'passed': lines == ['番茄', '鸡蛋'], 'body_line_count': len(lines)}


def verify_ocr_lines(text, required_lines):
    def normalize(line):
        return ''.join(unicodedata.normalize('NFKC', line).split())
    observed = {normalize(line) for line in text.splitlines() if normalize(line)}
    required = [normalize(line) for line in required_lines]
    missing = [line for line in required if line not in observed]
    return {'passed': bool(required) and all(required) and not missing, 'missing_count': len(missing)}


def verify_runlog(run, marker, seen_run_ids=()):
    if not marker.strip():
        raise ValueError('marker required')
    run_id = run.get('run_id')
    marker_present = isinstance(run.get('goal'), str) and marker in run['goal']
    unique_id = isinstance(run_id, str) and bool(run_id.strip()) and run_id not in seen_run_ids
    terminal = run.get('status') in {'succeeded', 'failed', 'cancelled', 'canceled'}
    model_success = run.get('status') == 'succeeded' and run.get('success') is True
    return {'marker_present': marker_present, 'unique_id': unique_id, 'terminal': terminal,
            'model_success': model_success, 'passed': marker_present and unique_id and terminal and model_success}


def verify_todo(rows, title):
    matches = [description for text, description in rows if text == title]
    return {'passed': len(matches) == 1 and matches[0] == '未完成,' + title,
            'exact_title_unique': len(matches) == 1,
            'incomplete': len(matches) == 1 and matches[0] == '未完成,' + title}
