package com.agent.ragkb.controller;

import com.agent.ragkb.dto.ApiResponse;
import com.agent.ragkb.dto.RagQueryRequest;
import com.agent.ragkb.dto.RagResponse;
import com.agent.ragkb.service.FullRagPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagQueryController {

    private final FullRagPipeline fullRagPipeline;


    @PostMapping("/query")
    public ApiResponse<RagResponse> query(@RequestBody RagQueryRequest req) {
        return ApiResponse.ok(fullRagPipeline.query(req.getQuestion(), req.getKbIds()));
    }
}