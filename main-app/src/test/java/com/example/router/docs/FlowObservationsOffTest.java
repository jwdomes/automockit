package com.example.router.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.router.client.PartnerAClient;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FlowObservationsOffTest {

    @Autowired
    PartnerAClient partnerAClient;

    @Test
    void stepsAreNotProxiedByDefault() {
        assertThat(AopUtils.isAopProxy(partnerAClient)).isFalse();
    }
}
