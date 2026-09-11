package com.qingzhou.modules.credential.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CredentialVO {

    private Long id;
    private String credentialName;
    private String credentialType;
    private String scope;
    private Long workflowId;
    private String corpId;
    private String agentId;
    private boolean hasSecret;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
