package com.qingzhou.modules.credential.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.credential.entity.Credential;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class MysqlCredentialSupport {

    public static final String TYPE = "MYSQL";
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9._:-]+$");
    private static final Pattern DB_NAME = Pattern.compile("^[A-Za-z0-9_$-]+$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MysqlCredentialSupport() {
    }

    public static boolean isMysql(Credential credential) {
        return credential != null && TYPE.equalsIgnoreCase(credential.getCredentialType());
    }

    public static boolean isMysql(String type) {
        return TYPE.equalsIgnoreCase(type);
    }

    public static MysqlEndpoint from(Credential credential) {
        Map<String, Object> extra = readMap(credential == null ? null : credential.getExtraConfig());
        return from(extra);
    }

    public static MysqlEndpoint from(Map<String, Object> extra) {
        Map<String, Object> map = extra == null ? Map.of() : extra;
        String host = trim(map.get("dbHost"));
        Integer port = intValue(map.get("dbPort"), 3306);
        String dbName = trim(map.get("dbName"));
        String username = trim(map.get("dbUsername"));
        return new MysqlEndpoint(host, port, dbName, username);
    }

    public static Map<String, Object> toExtra(String host, Integer port, String dbName, String username) {
        MysqlEndpoint endpoint = validate(host, port, dbName, username);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("dbHost", endpoint.host());
        extra.put("dbPort", endpoint.port());
        extra.put("dbName", endpoint.dbName());
        extra.put("dbUsername", endpoint.username());
        return extra;
    }

    public static MysqlEndpoint validate(String host, Integer port, String dbName, String username) {
        String safeHost = trim(host);
        String safeDb = trim(dbName);
        String safeUser = trim(username);
        int safePort = port == null ? 3306 : port;
        if (!StringUtils.hasText(safeHost)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写数据库主机");
        }
        if (!HOST.matcher(safeHost).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST, "数据库主机格式不正确");
        }
        if (safePort < 1 || safePort > 65535) {
            throw new BizException(ResultCode.BAD_REQUEST, "数据库端口不合法");
        }
        if (!StringUtils.hasText(safeDb)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写数据库名");
        }
        if (!DB_NAME.matcher(safeDb).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST, "数据库名仅支持字母、数字、下划线");
        }
        if (!StringUtils.hasText(safeUser)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写数据库用户名");
        }
        return new MysqlEndpoint(safeHost, safePort, safeDb, safeUser);
    }

    public static String jdbcUrl(MysqlEndpoint endpoint) {
        return "jdbc:mysql://" + endpoint.host() + ":" + endpoint.port() + "/" + endpoint.dbName()
                + "?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
                + "&allowMultiQueries=false&autoReconnect=false";
    }

    public static String displayUrl(MysqlEndpoint endpoint) {
        if (endpoint == null || !StringUtils.hasText(endpoint.host())) {
            return "mysql://";
        }
        String db = StringUtils.hasText(endpoint.dbName()) ? "/" + endpoint.dbName() : "";
        return "mysql://" + endpoint.host() + ":" + endpoint.port() + db;
    }

    public static Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private static String trim(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer intValue(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null || !StringUtils.hasText(String.valueOf(raw))) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public record MysqlEndpoint(String host, int port, String dbName, String username) {
        public String display() {
            return displayUrl(this);
        }
    }
}
