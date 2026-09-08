import json
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from app.api.review import get_client
from app.main import app
from app.review.client import parse_findings

GOOD_PAYLOAD = json.dumps(
    {
        "findings": [
            {
                "path": "src/A.java",
                "line": 4,
                "severity": "warning",
                "title": "Unused variable",
                "message": "x is assigned but never read.",
                "suggestion": "Remove it.",
                "citedItemIds": ["DIFF#src%2FA.java#0"],
            }
        ]
    }
)


def _request_payload():
    return {
        "contextId": "ctx-1",
        "sliceId": "slice-1",
        "repo": "o/r",
        "prNumber": 7,
        "items": [
            {
                "itemId": "DIFF#src%2FA.java#0",
                "kind": "DIFF_HUNK",
                "path": "src/A.java",
                "revision": "HEAD",
                "symbolId": None,
                "startLine": 3,
                "endLine": 5,
                "required": True,
                "content": "@@ -3,2 +3,3 @@\n   3 | class A {\n+  4 |     int x = 1;\n   5 | }",
            }
        ],
    }


def _fake_client(payload, error=None):
    if error is not None:
        def _raise(**kwargs):
            raise error

        completions = SimpleNamespace(create=_raise)
    else:
        message = SimpleNamespace(content=payload)
        completion = SimpleNamespace(
            choices=[SimpleNamespace(message=message)],
            usage=SimpleNamespace(prompt_tokens=10, completion_tokens=5),
        )
        completions = SimpleNamespace(create=lambda **kwargs: completion)
    return SimpleNamespace(chat=SimpleNamespace(completions=completions))


@pytest.fixture()
def client():
    yield TestClient(app)
    app.dependency_overrides.clear()


def test_review_happy_path(client):
    app.dependency_overrides[get_client] = lambda: _fake_client(GOOD_PAYLOAD)
    response = client.post("/review", json=_request_payload())
    assert response.status_code == 200
    body = response.json()
    assert len(body["findings"]) == 1
    finding = body["findings"][0]
    assert finding["path"] == "src/A.java"
    assert finding["line"] == 4
    assert finding["severity"] == "warning"
    assert finding["citedItemIds"] == ["DIFF#src%2FA.java#0"]
    assert body["usage"] == {"promptTokens": 10, "completionTokens": 5}
    assert body["droppedFindings"] == 0
    assert body["model"]


def test_review_without_api_key_returns_503(client, monkeypatch):
    monkeypatch.delenv("EVOREVIEW_LLM_API_KEY", raising=False)
    response = client.post("/review", json=_request_payload())
    assert response.status_code == 503
    assert "EVOREVIEW_LLM_API_KEY" in response.json()["detail"]


def test_review_upstream_error_returns_502(client):
    app.dependency_overrides[get_client] = lambda: _fake_client(None, error=RuntimeError("boom"))
    response = client.post("/review", json=_request_payload())
    assert response.status_code == 502
    assert "boom" in response.json()["detail"]


def test_parse_findings_drops_junk_entries():
    raw = json.dumps(
        {
            "findings": [
                {
                    "path": "src/A.java",
                    "line": 4,
                    "severity": "not-a-severity",
                    "title": "ok",
                    "message": "fine",
                },
                {"path": "src/A.java", "title": "no line", "message": "x"},
                {"path": "src/A.java", "line": -3, "title": "bad line", "message": "x"},
                "not-a-dict",
            ]
        }
    )
    findings, dropped = parse_findings(raw)
    assert len(findings) == 1
    assert dropped == 3
    assert findings[0].severity == "warning"  # unknown severity clamped
    assert findings[0].cited_item_ids == []


def test_parse_findings_tolerates_fenced_json():
    findings, dropped = parse_findings("```json\n" + GOOD_PAYLOAD + "\n```")
    assert len(findings) == 1
    assert dropped == 0


def test_parse_findings_non_json_counts_one_drop():
    findings, dropped = parse_findings("I cannot review this.")
    assert findings == []
    assert dropped == 1
