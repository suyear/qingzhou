package com.qingzhou.modules.credential.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CredentialSaveRequest {

    @NotBlank(message = "凭证名称不能为空")
    @Size(max = 128)
    private String credentialName;

    /** WECOM / CUSTOM / MYSQL */
    private String credentialType;

    /** GLOBAL / WORKFLOW */
    private String scope;

    private Long workflowId;
    private String corpId;
    private String agentId;
    /** 明文 Secret，仅写入；更新时留空表示不改 */
    private String secret;

    /** MYSQL 数据源连接（密码走 secret） */
    private String dbHost;
    private Integer dbPort;
    private String dbName;
    private String dbUsername;

    private String remark;
    private Integer status;
}
