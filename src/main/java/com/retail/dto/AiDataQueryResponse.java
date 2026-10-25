package com.retail.dto;

import java.util.List;
import java.util.Map;

public record AiDataQueryResponse(List<Map<String, Object>> data, String dataSource) {
}
