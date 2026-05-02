package com.miso.blog.publish.dto;

import java.util.List;

public record PublishStrategyResponse(
        String primaryChannel,
        String exposureChannel,
        String markdownPolicy,
        List<PublishTargetResponse> targets
) {
}
