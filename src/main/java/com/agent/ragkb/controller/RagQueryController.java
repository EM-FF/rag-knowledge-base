package com.agent.ragkb.controller;

import com.agent.ragkb.dto.ApiResponse;
import com.agent.ragkb.dto.RagQueryRequest;
import com.agent.ragkb.service.RagQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagQueryController {

    private final RagQueryService ragQueryService;

    @PostMapping("/query")
    public ApiResponse<String> query(@RequestBody RagQueryRequest req) {
        return ApiResponse.ok(ragQueryService.query(req.getQuestion(), req.getKbIds()));
    }
}