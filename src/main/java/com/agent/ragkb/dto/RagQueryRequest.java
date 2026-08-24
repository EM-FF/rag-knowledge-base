package com.agent.ragkb.dto;

import lombok.Data;

import java.util.List;

@Data
public class RagQueryRequest {
    private String question;
    private List<Long> kbIds;
}
