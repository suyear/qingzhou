package com.qingzhou.modules.execution.dto;

import com.qingzhou.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionQuery extends PageQuery {

    private Long workflowId;
    private String triggerType;
    private Long triggerAppId;
    private String status;
}
