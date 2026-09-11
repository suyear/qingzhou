package com.qingzhou.modules.execution.engine;

public record HttpCallResult(int status, String body, boolean timeout, String error) {

    public boolean success() {
        return !timeout && error == null && status >= 200 && status < 300;
    }
}
