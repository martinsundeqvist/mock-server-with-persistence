package com.example;

public class IncomingHttpRequest {

    private String method;
    private String endpoint;
    private String body;

    public IncomingHttpRequest(String method, String endpoint, String body) {
        this.method = method;
        this.endpoint = endpoint;
        this.body = body;
    }

    public String getMethod() {
        return method;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getBody() {
        return body;
    }
    
}
