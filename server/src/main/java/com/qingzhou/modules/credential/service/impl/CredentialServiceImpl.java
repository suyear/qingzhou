package com.qingzhou.modules.credential.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.crypto.AesEncryptor;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.common.json.Jsons;
import com.qingzhou.infra.wecom.TokenManager;
import com.qingzhou.modules.credential.dto.CredentialSaveRequest;
import com.qingzhou.modules.credential.dto.CredentialTestVO;
import com.qingzhou.modules.credential.dto.CredentialVO;
import com.qingzhou.modules.credential.entity.Credential;
import com.qingzhou.modules.credential.mapper.CredentialMapper;
import com.qingzhou.modules.credential.service.CredentialService;
import com.qingzhou.modules.credential.support.MysqlCredentialSupport;
import com.qingzhou.modules.execution.engine.DatasourcePoolManager;
import com.qingzhou.modules.workflow.entity.Workflow;
import com.qingzhou.modules.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CredentialServiceImpl extends ServiceImpl<CredentialMapper, Credential> implements CredentialService {

    private static final String TYPE_WECOM = "WECOM";
    private static final String TYPE_CUSTOM = "CUSTOM";
    private static final String TYPE_MYSQL = "MYSQL";
    private static final String SCOPE_GLOBAL = "GLOBAL";
    private static final String SCOPE_WORKFLOW = "WORKFLOW";

    private final AesEncryptor aesEncryptor;
    private final Jsons jsons;
    private final WorkflowService workflowService;
    private final ObjectProvider<TokenManager> tokenManager;
    private final ObjectProvider<DatasourcePoolManager> datasourcePoolManager;

    @Override
    public IPage<CredentialVO> pageVo(PageQuery query) {
        LambdaQueryWrapper<Credential> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(Credential::getCredentialName, query.getKeyword())
                        .or()
                        .like(Credential::getCorpId, query.getKeyword())
                        .or()
                        .like(Credential::getExtraConfig, query.getKeyword()))
                .orderByDesc(Credential::getUpdateTime);
        return page(new Page<>(query.getCurrent(), query.getSize()), wrapper).convert(this::toVo);
    }

    @Override
    public CredentialVO detail(Long id) {
        return toVo(require(id));
    }

    @Override
    @Transactional
    public CredentialVO create(CredentialSaveRequest request) {
        if (!StringUtils.hasText(request.getSecret())) {
            throw new BizException(ResultCode.BAD_REQUEST, "Secret 不能为空");
        }
        Credential entity = new Credential();
        fill(entity, request, true);
        save(entity);
        return toVo(entity);
    }

    @Override
    @Transactional
    public CredentialVO update(Long id, CredentialSaveRequest request) {
        Credential entity = require(id);
        boolean rotateSecret = StringUtils.hasText(request.getSecret());
        fill(entity, request, false);
        updateById(entity);
        if (rotateSecret || MysqlCredentialSupport.isMysql(entity)) {
            evict(id);
        }
        return detail(id);
    }

    @Override
    @Transactional
    public void removeCredential(Long id) {
        require(id);
        evict(id);
        removeById(id);
    }

    @Override
    @Transactional
    public CredentialVO changeStatus(Long id, int status) {
        Credential entity = require(id);
        entity.setStatus(status == 1 ? 1 : 0);
        updateById(entity);
        if (entity.getStatus() == 0) {
            evict(id);
        }
        return toVo(entity);
    }

    @Override
    public CredentialTestVO test(Long id) {
        Credential entity = require(id);
        if (MysqlCredentialSupport.isMysql(entity)) {
            return testMysql(entity);
        }
        if (TYPE_CUSTOM.equalsIgnoreCase(entity.getCredentialType())) {
            return new CredentialTestVO(true, "自定义凭证已保存，可在 HTTP 组件鉴权中引用");
        }
        try {
            tokenManager.getObject().getAccessToken(id);
            return new CredentialTestVO(true, "已拿到 AccessToken，凭证可用");
        } catch (BizException ex) {
            return new CredentialTestVO(false, ex.getMessage());
        }
    }

    private CredentialTestVO testMysql(Credential entity) {
        DatasourcePoolManager pool = datasourcePoolManager.getIfAvailable();
        if (pool == null) {
            return new CredentialTestVO(false, "数据源连接池未就绪");
        }
        try (var connection = pool.getConnection(entity.getId());
             var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            statement.execute("SELECT 1");
            MysqlCredentialSupport.MysqlEndpoint endpoint = MysqlCredentialSupport.from(entity);
            return new CredentialTestVO(true, "已连通 " + endpoint.display());
        } catch (BizException ex) {
            return new CredentialTestVO(false, ex.getMessage());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "数据库连接失败" : ex.getMessage();
            return new CredentialTestVO(false, message);
        }
    }

    private void fill(Credential entity, CredentialSaveRequest request, boolean creating) {
        String type = StringUtils.hasText(request.getCredentialType())
                ? request.getCredentialType().trim().toUpperCase(Locale.ROOT)
                : TYPE_WECOM;
        String scope = StringUtils.hasText(request.getScope())
                ? request.getScope().trim().toUpperCase(Locale.ROOT)
                : SCOPE_GLOBAL;
        if (!TYPE_WECOM.equals(type) && !TYPE_CUSTOM.equals(type) && !TYPE_MYSQL.equals(type)) {
            throw new BizException(ResultCode.BAD_REQUEST, "凭证类型仅支持 WECOM / CUSTOM / MYSQL");
        }
        if (!SCOPE_GLOBAL.equals(scope) && !SCOPE_WORKFLOW.equals(scope)) {
            throw new BizException(ResultCode.BAD_REQUEST, "作用域仅支持 GLOBAL / WORKFLOW");
        }
        if (SCOPE_WORKFLOW.equals(scope)) {
            if (request.getWorkflowId() == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "工作流独立凭证必须绑定工作流");
            }
            Workflow workflow = workflowService.getById(request.getWorkflowId());
            if (workflow == null) {
                throw new BizException(ResultCode.NOT_FOUND, "绑定的工作流不存在");
            }
            entity.setWorkflowId(request.getWorkflowId());
        } else {
            entity.setWorkflowId(null);
        }
        if (TYPE_WECOM.equals(type) && !StringUtils.hasText(request.getCorpId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "企业微信凭证必须填写 CorpId");
        }
        if (TYPE_MYSQL.equals(type)) {
            Map<String, Object> extra = MysqlCredentialSupport.toExtra(
                    request.getDbHost(), request.getDbPort(), request.getDbName(), request.getDbUsername());
            entity.setExtraConfig(jsons.toJson(extra));
            entity.setCorpId(null);
            entity.setAgentId(null);
        } else {
            entity.setCorpId(trimToNull(request.getCorpId()));
            entity.setAgentId(trimToNull(request.getAgentId()));
        }
        entity.setCredentialName(request.getCredentialName().trim());
        entity.setCredentialType(type);
        entity.setScope(scope);
        entity.setRemark(request.getRemark());
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus() == 1 ? 1 : 0);
        } else if (creating) {
            entity.setStatus(1);
        }
        if (TYPE_MYSQL.equals(type) && creating && !StringUtils.hasText(request.getSecret())) {
            throw new BizException(ResultCode.BAD_REQUEST, "数据库密码不能为空");
        }
        if (StringUtils.hasText(request.getSecret())) {
            entity.setSecretCipher(aesEncryptor.encrypt(request.getSecret().trim()));
        }
    }

    private Credential require(Long id) {
        Credential entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "凭证不存在");
        }
        return entity;
    }

    private void evict(Long id) {
        TokenManager manager = tokenManager.getIfAvailable();
        if (manager != null) {
            manager.evict(id);
        }
        DatasourcePoolManager pool = datasourcePoolManager.getIfAvailable();
        if (pool != null) {
            pool.evict(id);
        }
    }

    private CredentialVO toVo(Credential entity) {
        CredentialVO vo = new CredentialVO();
        vo.setId(entity.getId());
        vo.setCredentialName(entity.getCredentialName());
        vo.setCredentialType(entity.getCredentialType());
        vo.setScope(entity.getScope());
        vo.setWorkflowId(entity.getWorkflowId());
        vo.setCorpId(entity.getCorpId());
        vo.setAgentId(entity.getAgentId());
        vo.setHasSecret(StringUtils.hasText(entity.getSecretCipher()));
        if (MysqlCredentialSupport.isMysql(entity)) {
            MysqlCredentialSupport.MysqlEndpoint endpoint = MysqlCredentialSupport.from(entity);
            vo.setDbHost(endpoint.host());
            vo.setDbPort(endpoint.port());
            vo.setDbName(endpoint.dbName());
            vo.setDbUsername(endpoint.username());
        }
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
