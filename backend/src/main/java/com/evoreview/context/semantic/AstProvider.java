package com.evoreview.context.semantic;

import java.util.List;

public interface AstProvider {

    boolean supports(String path);

    ParseResult parse(List<SourceInput> sources);
}
