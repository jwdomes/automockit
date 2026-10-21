package com.example.router.docs;

import com.example.flowdocs.test.StaticFlowDocs;
import org.junit.jupiter.api.Test;

class FlowDocsTest {

    @Test
    void flowDocsAreCurrent() throws Exception {
        StaticFlowDocs.verify(RouterFlowDocs.SETTINGS);
    }
}
