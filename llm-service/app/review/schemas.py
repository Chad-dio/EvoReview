from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field


class ReviewItem(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    item_id: str = Field(alias="itemId")
    kind: str
    path: Optional[str] = None
    revision: Optional[str] = None
    symbol_id: Optional[str] = Field(default=None, alias="symbolId")
    start_line: Optional[int] = Field(default=None, alias="startLine")
    end_line: Optional[int] = Field(default=None, alias="endLine")
    required: bool = False
    content: str


class ReviewRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    context_id: str = Field(alias="contextId")
    slice_id: str = Field(alias="sliceId")
    repo: str
    pr_number: int = Field(alias="prNumber")
    items: List[ReviewItem] = Field(default_factory=list)


SEVERITIES = ("critical", "warning", "suggestion")


class Finding(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    path: str
    line: int
    severity: str = "warning"
    title: str
    message: str
    suggestion: Optional[str] = None
    cited_item_ids: List[str] = Field(default_factory=list, alias="citedItemIds")


class Usage(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    prompt_tokens: int = Field(default=0, alias="promptTokens")
    completion_tokens: int = Field(default=0, alias="completionTokens")


class ReviewResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    findings: List[Finding]
    model: str
    usage: Usage = Field(default_factory=Usage)
    dropped_findings: int = Field(default=0, alias="droppedFindings")
