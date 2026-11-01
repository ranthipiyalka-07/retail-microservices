package com.retail.common.config;

import com.retail.common.crypto.AesAttributeConverter;
import com.retail.common.error.GlobalExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class CommonLibAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(){
        return new GlobalExceptionHandler();
    }

    //Only created when the service defines retail.security.aes-key
    @Bean
    @ConditionalOnProperty(name="retail.security.aes-key")
    @ConditionalOnMissingBean
    public AesAttributeConverter aesAttributeConverter(@Value("${retail.security.aes-key}") String key){
        return new AesAttributeConverter(key);
    }
}
