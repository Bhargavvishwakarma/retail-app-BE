package com.retail.controller;

import com.retail.dto.AiDataQueryRequest;
import com.retail.dto.AiDataQueryResponse;
import com.retail.service.AiRetailDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/ai-data")
@RequiredArgsConstructor
public class InternalAiDataController {
    private final AiRetailDataService dataService;

    @PostMapping("/query")
    public AiDataQueryResponse query(@Valid @RequestBody AiDataQueryRequest request,
                                     @RequestHeader("X-Actor-Assertion") String actorToken) {
        return new AiDataQueryResponse(dataService.query(request, actorToken), request.operation().name().toLowerCase());
    }
}
