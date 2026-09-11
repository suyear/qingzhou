package com.qingzhou.modules.execution.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 节点 HTTP 调用。每次请求用组件自己的 timeoutMs，避免共享 RestClient 超时互相干扰。
 */
@Slf4j
@Component
public class NodeHttpInvoker {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HttpCallResult invoke(String method, URI uri, Map<String, String> headers, String body, int timeoutMs) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(Math.max(1000, timeoutMs)));
            if (headers != null) {
                headers.forEach((k, v) -> {
                    if (StringUtils.hasText(k) && v != null) {
                        builder.header(k, v);
                    }
                });
            }
            String verb = method == null ? "GET" : method.toUpperCase();
            if ("GET".equals(verb) || "DELETE".equals(verb)) {
                builder.method(verb, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", headers != null && headers.containsKey("Content-Type")
                        ? headers.get("Content-Type") : "application/json");
                builder.method(verb, HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpCallResult(response.statusCode(), response.body(), false, null);
        } catch (java.net.http.HttpTimeoutException timeout) {
            log.warn("节点 HTTP 超时 uri={}", uri);
            return new HttpCallResult(0, null, true, "第三方接口超时");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new HttpCallResult(0, null, true, "调用被中断");
        } catch (Exception ex) {
            log.warn("节点 HTTP 失败 uri={} msg={}", uri, ex.getMessage());
            return new HttpCallResult(0, null, false, ex.getMessage());
        }
    }
}
