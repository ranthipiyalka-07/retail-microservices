package com.retail.common.outbox;

import com.retail.common.crypto.AesAttributeConverter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AesAttributeConverterTest {
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="; // 32 bytes, test only

    @Test
    void encryptAndDecryptReturnsOriginal(){
        var c =new AesAttributeConverter(KEY);
        byte[] enc = c.convertToDatabaseColumn("+7734212233");
        Assertions.assertThat(enc).isNotEqualTo("+7734212233".getBytes());
        Assertions.assertThat(c.convertToEntityAttribute(enc)).isEqualTo("+7734212233");
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime(){
        var c =new AesAttributeConverter(KEY);
        Assertions.assertThat (c.convertToDatabaseColumn("x")).isNotEqualTo(c.convertToDatabaseColumn("x"));
    }
}
