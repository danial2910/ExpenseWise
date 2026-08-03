package com.expensewise.config;

import com.expensewise.entitlement.FeatureEntitlementInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FeatureEntitlementInterceptor featureEntitlementInterceptor;

    public WebConfig(FeatureEntitlementInterceptor featureEntitlementInterceptor) {
        this.featureEntitlementInterceptor = featureEntitlementInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(featureEntitlementInterceptor);
    }
}
