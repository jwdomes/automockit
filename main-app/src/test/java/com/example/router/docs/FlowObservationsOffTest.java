package com.example.router.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.router.partner.PartnerAService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FlowObservationsOffTest {

    @Autowired
    PartnerAService partnerAService;

    @Test
    void stepsAreNotProxiedByDefault() {
        assertThat(AopUtils.isAopProxy(partnerAService)).isFalse();
    }
}
