package com.example.partnerclients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/** Registers a RestClient-backed client per partner, at partners.{a,b,c}.base-url. */
@AutoConfiguration
public class PartnerClientsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    PartnerAClient partnerAClient(RestClient.Builder builder, @Value("${partners.a.base-url}") String baseUrl) {
        return new RestPartnerAClient(builder.baseUrl(baseUrl).build());
    }

    @Bean
    @ConditionalOnMissingBean
    PartnerBClient partnerBClient(RestClient.Builder builder, @Value("${partners.b.base-url}") String baseUrl) {
        return new RestPartnerBClient(builder.baseUrl(baseUrl).build());
    }

    @Bean
    @ConditionalOnMissingBean
    PartnerCClient partnerCClient(RestClient.Builder builder, @Value("${partners.c.base-url}") String baseUrl) {
        return new RestPartnerCClient(builder.baseUrl(baseUrl).build());
    }
}
