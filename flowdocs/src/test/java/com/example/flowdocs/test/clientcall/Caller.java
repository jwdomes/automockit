package com.example.flowdocs.test.clientcall;

import com.example.flowdocs.FlowStep;
import com.example.flowdocs.test.clientcall.client.Api;
import com.example.flowdocs.test.clientcall.client.Data;

public class Caller {

    private final Api api;

    public Caller(Api api) {
        this.api = api;
    }

    @FlowStep(action = "Fetch", participant = "APP")
    public String fetch() {
        return api.fetch();
    }

    public String fetchUnannotated() {
        return api.fetch();
    }

    public String read(Data data) {
        return data.value();
    }
}
