import json
import os
from typing import List, Optional, Tuple

from .prompts import build_messages
from .schemas import SEVERITIES, Finding, ReviewRequest, ReviewResponse, Usage

DEFAULT_BASE_URL = "https://api.openai.com/v1"
DEFAULT_MODEL = "gpt-4o-mini"


def get_client():
    api_key = os.environ.get("EVOREVIEW_LLM_API_KEY", "").strip()
    if not api_key:
        from fastapi import HTTPException

        raise HTTPException(
            status_code=503,
            detail="EVOREVIEW_LLM_API_KEY is not set; the review endpoint is unavailable.",
        )
    from openai import OpenAI

    return OpenAI(
        api_key=api_key,
        base_url=os.environ.get("EVOREVIEW_LLM_BASE_URL") or DEFAULT_BASE_URL,
    )


def model_name() -> str:
    return os.environ.get("EVOREVIEW_LLM_MODEL", DEFAULT_MODEL)


def run_review(request: ReviewRequest, client) -> ReviewResponse:
    model = model_name()
    completion = client.chat.completions.create(
        model=model,
        messages=build_messages(request),
        response_format={"type": "json_object"},
        temperature=0.2,
    )
    raw = completion.choices[0].message.content or ""
    usage = Usage()
    completion_usage = getattr(completion, "usage", None)
    if completion_usage is not None:
        usage = Usage(
            promptTokens=getattr(completion_usage, "prompt_tokens", 0) or 0,
            completionTokens=getattr(completion_usage, "completion_tokens", 0) or 0,
        )
    findings, dropped = parse_findings(raw)
    return ReviewResponse(findings=findings, model=model, usage=usage, droppedFindings=dropped)


def parse_findings(raw: str) -> Tuple[List[Finding], int]:
    payload = _load_json_object(raw)
    if payload is None:
        return [], 1
    raw_findings = payload.get("findings")
    if not isinstance(raw_findings, list):
        return [], 0
    findings: List[Finding] = []
    dropped = 0
    for entry in raw_findings:
        finding = _coerce_finding(entry)
        if finding is None:
            dropped += 1
        else:
            findings.append(finding)
    return findings, dropped


def _load_json_object(raw: str) -> Optional[dict]:
    text = raw.strip()
    if text.startswith("```"):
        text = text.strip("`").lstrip()
        if text[:5].lower() == "json":
            text = text[5:]
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        data = json.loads(text[start : end + 1])
    except json.JSONDecodeError:
        return None
    return data if isinstance(data, dict) else None


def _coerce_finding(entry) -> Optional[Finding]:
    if not isinstance(entry, dict):
        return None
    path = entry.get("path")
    title = entry.get("title")
    message = entry.get("message")
    line = entry.get("line")
    if not isinstance(path, str) or not path.strip():
        return None
    if not isinstance(title, str) or not title.strip():
        return None
    if not isinstance(message, str) or not message.strip():
        return None
    if not isinstance(line, int) or isinstance(line, bool) or line <= 0:
        return None
    severity = entry.get("severity")
    if severity not in SEVERITIES:
        severity = "warning"
    cited = entry.get("citedItemIds")
    cited_ids = [str(c) for c in cited] if isinstance(cited, list) else []
    suggestion = entry.get("suggestion")
    return Finding(
        path=path,
        line=line,
        severity=severity,
        title=title,
        message=message,
        suggestion=suggestion if isinstance(suggestion, str) and suggestion.strip() else None,
        citedItemIds=cited_ids,
    )
