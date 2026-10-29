from __future__ import annotations

from dataclasses import dataclass
import re
import unicodedata
import xml.etree.ElementTree as ET

from omniflow.core.model import Action, Observation

_BOUNDS = re.compile(
    r"^\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\]"
    r"\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\]$"
)
_SUPPORTED_TOOLS = {"click", "input_text"}


@dataclass(frozen=True)
class SemanticGroundingResult:
    action: Action
    detail: dict[str, object] | None = None


def resolve_semantic_action(
    action: Action,
    observation: Observation,
) -> SemanticGroundingResult:
    target = str(action.args.get("target_description") or "").strip()
    if action.tool not in _SUPPORTED_TOOLS or not target:
        return SemanticGroundingResult(action)
    display = observation.extra.get("display")
    if not isinstance(display, dict):
        return _unresolved(action, target, "display_missing")
    try:
        width = float(display.get("width") or 0)
        height = float(display.get("height") or 0)
    except (TypeError, ValueError):
        return _unresolved(action, target, "display_invalid")
    if width <= 0 or height <= 0:
        return _unresolved(action, target, "display_invalid")
    try:
        root = ET.fromstring(str(observation.xml or ""))
    except ET.ParseError:
        return _unresolved(action, target, "xml_invalid")

    parent_by_child = {
        child: parent for parent in root.iter() for child in parent
    }
    normalized_target = _normalize(target)
    # Only strip an explicit role around a single short label. Longer free-form
    # descriptions are not reliable selectors and must use normal fallback.
    role_match = re.fullmatch(r"(?:数字键\s*([^\s]{1,4})|([^\s]{1,4})\s+(?:button|key)|([^\s]{1,4})\s*按钮)", normalized_target)
    label_target = next((part for part in role_match.groups() if part), "") if role_match else ""
    matches: dict[tuple[float, float, float, float], ET.Element] = {}
    for element in root.iter():
        text_label = _normalize(element.attrib.get("text", ""))
        description_label = _normalize(element.attrib.get("content-desc", ""))
        labels = {text_label, description_label}
        # A model may quote both labels from the same node. Match their exact
        # combination, never a substring or labels from separate controls.
        if text_label and description_label:
            labels.add(f"{text_label} {description_label}")
            labels.add(f"{description_label} {text_label}")
        if normalized_target not in labels and (not label_target or label_target not in labels):
            continue
        actionable = _nearest_actionable(
            element,
            parent_by_child,
            tool=action.tool,
        )
        # Some virtual controls expose an actionable text node as focusable
        # without marking it or any parent clickable. Use its own bounds only.
        if actionable is None and action.tool == "click" and _eligible_focusable(element):
            actionable = element
        if actionable is None:
            continue
        bounds = _parse_bounds(actionable.attrib.get("bounds"))
        if bounds is None:
            continue
        matches[bounds] = actionable

    if len(matches) != 1:
        reason = "target_missing" if not matches else "target_ambiguous"
        return _unresolved(
            action,
            target,
            reason,
            candidate_count=len(matches),
        )

    bounds, element = next(iter(matches.items()))
    center_x = (bounds[0] + bounds[2]) / 2.0
    center_y = (bounds[1] + bounds[3]) / 2.0
    args = {
        **action.args,
        "x": center_x / width * 1000.0,
        "y": center_y / height * 1000.0,
    }
    node_id = str(element.attrib.get("id") or "").strip()
    resource_id = str(element.attrib.get("resource-id") or "").strip()
    if node_id:
        args["node_id"] = node_id
    if resource_id:
        args["node_resource_id"] = resource_id
    return SemanticGroundingResult(
        Action(action.tool, args),
        {
            "schema_version": "omniflow.semantic-grounding.v1",
            "status": "resolved",
            "target_description": target,
            "match_source": "accessibility_text_or_content_description",
            "bounds": list(bounds),
            "node_id": node_id or None,
            "node_resource_id": resource_id or None,
        },
    )


def semantic_target_at_point(
    xml: str,
    x: float,
    y: float,
) -> str:
    try:
        root = ET.fromstring(str(xml or ""))
    except ET.ParseError:
        return ""
    candidates: list[tuple[float, ET.Element]] = []
    for element in root.iter():
        if not _eligible(element, "click"):
            continue
        bounds = _parse_bounds(element.attrib.get("bounds"))
        if bounds is None or not (
            bounds[0] <= x <= bounds[2] and bounds[1] <= y <= bounds[3]
        ):
            continue
        area = (bounds[2] - bounds[0]) * (bounds[3] - bounds[1])
        candidates.append((area, element))
    for _, element in sorted(candidates, key=lambda item: item[0]):
        labels = _element_labels(element)
        if len(labels) == 1:
            return labels[0]
    return ""


def _element_labels(element: ET.Element) -> list[str]:
    labels: list[str] = []
    normalized: set[str] = set()
    for candidate in element.iter():
        for name in ("text", "content-desc"):
            value = str(candidate.attrib.get(name) or "").strip()
            key = _normalize(value)
            if value and key not in normalized:
                normalized.add(key)
                labels.append(value)
    return labels


def _nearest_actionable(
    element: ET.Element,
    parent_by_child: dict[ET.Element, ET.Element],
    *,
    tool: str,
) -> ET.Element | None:
    current: ET.Element | None = element
    while current is not None:
        if _eligible(current, tool):
            return current
        current = parent_by_child.get(current)
    return None


def _eligible(element: ET.Element, tool: str) -> bool:
    attributes = element.attrib
    if attributes.get("visible", "true").lower() == "false" or attributes.get("visible-to-user", "true").lower() == "false":
        return False
    if attributes.get("enabled", "true").lower() == "false":
        return False
    if tool == "input_text":
        return attributes.get("editable", "false").lower() == "true"
    return attributes.get("clickable", "false").lower() == "true"


def _eligible_focusable(element: ET.Element) -> bool:
    attributes = element.attrib
    return (
        attributes.get("visible", "true").lower() != "false"
        and attributes.get("visible-to-user", "true").lower() != "false"
        and attributes.get("enabled", "true").lower() != "false"
        and attributes.get("focusable", "false").lower() == "true"
    )


def _parse_bounds(value: str | None) -> tuple[float, float, float, float] | None:
    match = _BOUNDS.fullmatch(str(value or "").strip())
    if match is None:
        return None
    bounds = tuple(float(item) for item in match.groups())
    if bounds[2] <= bounds[0] or bounds[3] <= bounds[1]:
        return None
    return bounds


def _normalize(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", str(value or "")).casefold()
    return " ".join(normalized.split())


def _unresolved(
    action: Action,
    target: str,
    reason: str,
    *,
    candidate_count: int = 0,
) -> SemanticGroundingResult:
    return SemanticGroundingResult(
        action,
        {
            "schema_version": "omniflow.semantic-grounding.v1",
            "status": "fallback",
            "target_description": target,
            "reason": reason,
            "candidate_count": candidate_count,
        },
    )


__all__ = [
    "SemanticGroundingResult",
    "resolve_semantic_action",
    "semantic_target_at_point",
]
