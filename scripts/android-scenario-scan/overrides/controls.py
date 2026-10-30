"""Bounded local UI controls. Primitive dispatch and logging remain engine-owned.

Inspired by Mika's WheelDriver, BatchRunner and UntilWait (Apache-2.0),
revision d68159366abaea523c5e4e6fd4dc83aad78f9698; independent implementation.
"""
from __future__ import annotations

from datetime import date, timedelta
import asyncio
import inspect
import re
import time
import unicodedata
import xml.etree.ElementTree as ET
from omniflow.core.model import Action, Observation

SELECTOR = {"type": "object", "properties": {
    key: {"type": "string"} for key in ("resource_id", "text", "description", "class_name")
}, "additionalProperties": False, "minProperties": 1}
STEP = {"type": "object", "properties": {
    "operation": {"type": "string", "enum": ["click", "input_text", "set_wheel", "wait_until"]},
    "selector": SELECTOR, "value": {"type": "string"},
    "condition": {"type": "string", "enum": ["appears", "gone", "stable"]},
    "timeout_ms": {"type": "integer", "minimum": 200, "maximum": 10000},
}, "required": ["operation"], "additionalProperties": False}
CONTROL_TOOLS = [{"type": "function", "function": {"name": name, "description": description,
    "parameters": {"type": "object", "properties": properties, "required": required, "additionalProperties": False}}}
    for name, description, properties, required in [
        ("ui_steps", "Execute 1-8 known UI steps without model round trips. Exact unique accessibility selectors are re-resolved before EVERY step. Same app only; stop on failure, missing/ambiguous target or package change. No coordinate guesses, no nested batches. Prefer this for calculator keys or known form fields. Dispatch is not task completion; inspect final screen.",
         {"package_name": {"type": "string"}, "steps": {"type": "array", "items": STEP, "minItems": 1, "maxItems": 8}}, ["package_name", "steps"]),
        ("set_wheel", "Set one visible numeric or month/day wheel locally with bounded adjustments and stable readback. Copy its unique resource_id/class from current controls. Prefer editable value; otherwise use wheel geometry, never guessed coordinates. Unreadable wheels return to the planner for visual fallback. Prefer native system tools for creating alarms/timers.",
         {"package_name": {"type": "string"}, "selector": SELECTOR, "value": {"type": "string"}}, ["package_name", "selector", "value"]),
        ("wait_until", "Wait locally for exact accessibility target to appear/disappear, or for a nonempty same-app tree to stabilize. Bounded timeout; empty trees never prove disappearance. No model calls or repeated taps.",
         {"package_name": {"type": "string"}, "selector": SELECTOR, "condition": STEP["properties"]["condition"], "timeout_ms": STEP["properties"]["timeout_ms"]}, ["package_name", "condition"]),
    ]]
CONTROL_NAMES = frozenset(t["function"]["name"] for t in CONTROL_TOOLS)
ATTRS = {"resource_id": "resource-id", "text": "text", "description": "content-desc", "class_name": "class"}


def validate_control(name, args):
    """Validate the model boundary once, including nested steps (runtime schema is shallow)."""
    if name not in CONTROL_NAMES or not isinstance(args, dict):
        raise ValueError("control_arguments_invalid")
    allowed = {"package_name", "steps"} if name == "ui_steps" else {"package_name", "selector", "value"} if name == "set_wheel" else {"package_name", "selector", "condition", "timeout_ms"}
    if set(args) - allowed or not isinstance(args.get("package_name"), str) or not args["package_name"].strip():
        raise ValueError("control_package_required_or_unknown_field")
    steps = args.get("steps") if name == "ui_steps" else [dict(args, operation=name)]
    if not isinstance(steps, list) or not 1 <= len(steps) <= 8:
        raise ValueError("control_steps_limit")
    for step in steps:
        if not isinstance(step, dict) or step.get("operation") not in STEP["properties"]["operation"]["enum"]:
            raise ValueError("control_operation_invalid")
        op = step["operation"]
        fields = {"operation", "selector"} | ({"value"} if op in {"input_text", "set_wheel"} else {"condition", "timeout_ms"} if op == "wait_until" else set())
        if set(step) - fields - ({"package_name"} if name != "ui_steps" else set()):
            raise ValueError("control_step_unknown_field")
        selector = step.get("selector", {})
        if not isinstance(selector, dict) or set(selector) - ATTRS.keys() or any(not isinstance(v, str) or not v.strip() for v in selector.values()):
            raise ValueError("control_selector_invalid")
        if not selector and not (op == "wait_until" and step.get("condition") == "stable"):
            raise ValueError("control_selector_required")
        if op in {"input_text", "set_wheel"} and (not isinstance(step.get("value"), str) or len(step["value"]) > 4096):
            raise ValueError("control_value_invalid")
        if op == "set_wheel" and not step["value"].strip():
            raise ValueError("control_wheel_value_required")
        if op == "wait_until":
            if step.get("condition") not in ("appears", "gone", "stable"):
                raise ValueError("control_condition_invalid")
            timeout = step.get("timeout_ms", 3000)
            if type(timeout) is not int or not 200 <= timeout <= 10000:
                raise ValueError("control_timeout_invalid")


def visible_nodes(observation, package):
    if observation.package_name != package:
        raise ValueError("control_package_changed")
    try:
        root = ET.fromstring(observation.xml or "")
    except ET.ParseError as error:
        raise ValueError("control_tree_unavailable") from error
    nodes = [n for n in root.iter("node") if n.get("visible-to-user") != "false" and n.get("package", package) == package]
    if not nodes:
        raise ValueError("control_tree_unavailable")
    return nodes


def matches(nodes, selector):
    return [n for n in nodes if all(n.get(ATTRS[k], "") == v for k, v in selector.items())]


def bounds(node):
    match = re.fullmatch(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", node.get("bounds", ""))
    if not match:
        raise ValueError("control_bounds_invalid")
    box = tuple(map(int, match.groups()))
    if box[2] <= box[0] or box[3] <= box[1]:
        raise ValueError("control_bounds_empty")
    return box


def wheel_value(text, local_date=None):
    normalized = unicodedata.normalize("NFKC", text).strip()
    numeric = re.fullmatch(r"(\d{1,4})\s*(?:年|月|日|时|分|秒)?", normalized)
    if numeric:
        return int(numeric[1])
    today = date.fromisoformat(local_date) if local_date else None
    relative = re.fullmatch(r"(今天|明天|后天)(?:周[一二三四五六日天])?", normalized)
    if relative:
        if today is None:
            raise ValueError("control_device_date_required")
        return (today + timedelta(days={"今天": 0, "明天": 1, "后天": 2}[relative[1]])).toordinal()
    dated = re.fullmatch(r"(\d{4})[-年](\d{1,2})[-月](\d{1,2})日?", normalized)
    if dated:
        if today is None or int(dated[1]) != today.year:
            raise ValueError("control_wheel_year_not_exposed")
        return date(int(dated[1]), int(dated[2]), int(dated[3])).toordinal()
    month_day = re.fullmatch(r"(\d{1,2})月(\d{1,2})日(?:周[一二三四五六日天])?", normalized)
    if month_day:
        # Only compare displayed month/day, never claim an unexposed year.
        return date(today.year if today else 2000, int(month_day[1]), int(month_day[2])).toordinal()
    raise ValueError("control_wheel_value_unreadable")


def wheel_read(node, local_date=None):
    box = bounds(node)
    rows = []
    for child in node.iter("node"):
        if child.get("visible-to-user") == "false":
            continue
        text = unicodedata.normalize("NFKC", child.get("text") or child.get("content-desc") or "").strip()
        if re.fullmatch(r"\d{1,4}\s*(?:年|月|日|时|分|秒)?|(?:\d{1,2}月\d{1,2}日|今天|明天|后天)(?:周[一二三四五六日天])?", text):
            b = bounds(child)
            if b[0] >= box[0] and b[2] <= box[2]:
                row = (wheel_value(text, local_date), (b[1] + b[3]) / 2, child)
                if not any(r[:2] == row[:2] for r in rows):
                    rows.append(row)
    rows.sort(key=lambda r: r[1])
    selected = [r for r in rows if r[2].get("selected") == "true" or r[2].get("editable") == "true"]
    if len(selected) == 1:
        return selected[0], rows
    mid = (box[1] + box[3]) / 2
    selected = [r for r in rows if bounds(r[2])[1] <= mid < bounds(r[2])[3]]
    if len(selected) != 1:
        raise ValueError("control_wheel_value_unreadable")
    return selected[0], rows


async def execute_control(name, args, *, observation, observe, dispatch):
    """Args already validated by planner. Dispatch invokes engine's guarded primitive path."""
    current = observation
    receipts = []
    started = time.monotonic()
    refresh_seconds = 0.0
    refresh_count = 0
    dispatch_seconds = 0.0
    local_wait_seconds = 0.0
    needs_refresh = True
    package = args["package_name"]
    steps = args["steps"] if name == "ui_steps" else [dict(args, operation=name)]

    async def refresh():
        nonlocal refresh_seconds, refresh_count
        before = time.monotonic()
        value = observe()
        result = Observation.from_value(await value if inspect.isawaitable(value) else value)
        refresh_seconds += time.monotonic() - before
        refresh_count += 1
        return result

    async def act(action):
        nonlocal current, dispatch_seconds, needs_refresh
        before = time.monotonic()
        result = await dispatch(action, current)
        dispatch_seconds += time.monotonic() - before
        current = result.after or current
        needs_refresh = result.after is None
        if not result.success:
            raise ValueError(result.error or "control_dispatch_failed")
        visible_nodes(current, package)

    try:
        for index, step in enumerate(steps):
            if time.monotonic() - started > 45:
                raise ValueError("control_time_budget")
            # The previous guarded action already returned a fresh post-action observation.
            if needs_refresh:
                current = await refresh()
                needs_refresh = False
            nodes = visible_nodes(current, package)
            op = step["operation"]
            selector = step.get("selector", {})
            if op == "wait_until":
                deadline = time.monotonic() + step.get("timeout_ms", 3000) / 1000
                last = None
                stable_since = time.monotonic()
                while True:
                    nodes = visible_nodes(current, package)
                    found = matches(nodes, selector) if selector else []
                    condition = step["condition"]
                    signature = current.xml
                    now = time.monotonic()
                    if signature != last:
                        stable_since, last = now, signature
                    satisfied = (condition == "appears" and bool(found)) or (condition == "gone" and not found) or (condition == "stable" and now - stable_since >= .4)
                    if satisfied:
                        break
                    if now >= deadline:
                        raise ValueError("control_wait_timeout")
                    before = time.monotonic()
                    await asyncio.sleep(min(.2, deadline - now))
                    local_wait_seconds += time.monotonic() - before
                    current = await refresh()
                receipts.append({"index": index, "operation": op, "condition_met": True})
                continue
            found = matches(nodes, selector)
            if len(found) != 1:
                raise ValueError("control_target_missing" if not found else "control_target_ambiguous")
            target = found[0]
            if target.get("enabled") == "false" or target.get("password") == "true":
                raise ValueError("control_target_unavailable")
            display = current.extra.get("display", {})
            width, height = display.get("width", 0), display.get("height", 0)
            if width <= 0 or height <= 0:
                raise ValueError("control_display_unavailable")
            def point(node):
                left, top, right, bottom = bounds(node)
                if left < 0 or top < 0 or right > width or bottom > height:
                    raise ValueError("control_target_outside_display")
                return {"x": (left + right) * 500 / width, "y": (top + bottom) * 500 / height,
                        "node_resource_id": node.get("resource-id", "")}
            if op in {"click", "input_text"}:
                if op == "input_text" and target.get("editable") != "true":
                    raise ValueError("control_target_not_editable")
                action_args = point(target)
                if op == "input_text":
                    action_args["text"] = step["value"]
                await act(Action(op, action_args))
                if op == "input_text":
                    readback = matches(visible_nodes(current, package), selector)
                    if len(readback) != 1 or readback[0].get("text", "") != step["value"]:
                        raise ValueError("control_text_readback_mismatch")
                receipts.append({"index": index, "operation": op, "dispatched": True})
                continue
            local_date = current.extra.get("device_time", {}).get("local_date")
            wanted = wheel_value(step["value"], local_date)
            previous = None
            for attempt in range(33):
                if time.monotonic() - started > 45:
                    raise ValueError("control_time_budget")
                found = matches(visible_nodes(current, package), selector)
                if len(found) != 1:
                    raise ValueError("control_wheel_target_changed")
                target = found[0]
                reading, rows = wheel_read(target, local_date)
                value, _, selected = reading
                if value == wanted:
                    before = time.monotonic()
                    await asyncio.sleep(.35)
                    local_wait_seconds += time.monotonic() - before
                    current = await refresh()
                    again = matches(visible_nodes(current, package), selector)
                    if len(again) == 1 and wheel_read(again[0], local_date)[0][0] == wanted:
                        receipts.append({"index": index, "operation": op, "value": step["value"], "adjustments": attempt, "verified": True})
                        break
                    continue
                if attempt == 32 or value == previous:
                    raise ValueError("control_wheel_not_moving" if value == previous else "control_wheel_limit")
                previous = value
                if selected.get("editable") == "true" and attempt == 0:
                    await act(Action("input_text", dict(point(selected), text=step["value"])))
                    # Commit an editable NumberPicker by clearing focus through the native input path.
                    continue
                native_steps = {"4096", "8192"}.issubset(set(target.get("actions", "").split(",")))
                if len(rows) < 2 and not native_steps:
                    raise ValueError("control_wheel_geometry_unreadable")
                values = [r[0] for r in rows]
                increasing = native_steps or sum(b > a for a, b in zip(values, values[1:])) >= len(values) / 2
                delta = wanted - value
                # Use wrap only when visible adjacent values actually establish its boundary.
                drops = [(a, b) for a, b in zip(values, values[1:]) if a > b and increasing]
                if drops:
                    low, high = drops[0][1], drops[0][0]
                    if low <= wanted <= high:
                        modulus = high - low + 1
                        delta = min((delta, delta - modulus, delta + modulus), key=abs)
                direction = 1 if (delta > 0) == increasing else -1
                gaps = sorted(b[1] - a[1] for a, b in zip(rows, rows[1:]) if b[1] > a[1])
                if not gaps and not native_steps:
                    raise ValueError("control_wheel_geometry_unreadable")
                row_height = gaps[len(gaps) // 2] if gaps else (bounds(target)[3] - bounds(target)[1]) * .2
                left, top, right, bottom = bounds(target)
                length = min(row_height, (bottom - top) * .4)
                x, mid = (left + right) * 500 / width, (top + bottom) / 2
                await act(Action("swipe", {"direction": "up" if direction > 0 else "down",
                    "x1": x, "x2": x, "y1": (mid + direction * length / 2) * 1000 / height,
                    "y2": (mid - direction * length / 2) * 1000 / height, "duration_ms": 500}))
            else:
                raise ValueError("control_wheel_limit")
        return True, current, {"completed": len(receipts), "total": len(steps), "steps": receipts,
                               "timing_ms": {"refresh": round(refresh_seconds * 1000), "refresh_count": refresh_count,
                                             "dispatch": round(dispatch_seconds * 1000), "local_wait": round(local_wait_seconds * 1000)}}
    except ValueError as error:
        return False, current, {"completed": len(receipts), "total": len(steps), "steps": receipts, "error": str(error), "stopped_index": len(receipts),
                                "timing_ms": {"refresh": round(refresh_seconds * 1000), "refresh_count": refresh_count,
                                              "dispatch": round(dispatch_seconds * 1000), "local_wait": round(local_wait_seconds * 1000)}}
