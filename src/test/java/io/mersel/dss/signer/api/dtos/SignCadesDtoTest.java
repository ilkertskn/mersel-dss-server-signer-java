package io.mersel.dss.signer.api.dtos;

import io.mersel.dss.signer.api.models.enums.CadesSignatureLevel;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SignCadesDto} DTO katmanı kontrat testleri.
 *
 * <p>{@link SignXadesDtoTest} ile aynı sözleşme: <em>SignatureLevel asla null
 * olamaz</em>. Multipart form binding, JSON deserialize veya manuel setter
 * çağrılarından gelen null değerler {@link CadesSignatureLevel#CADES_BES}
 * fallback'ine düşürülür.</p>
 */
@Epic("API Contract")
@Feature("SignCadesDto")
@Severity(SeverityLevel.NORMAL)
class SignCadesDtoTest {

    @Test
    @DisplayName("Default ctor sonrası SignatureLevel non-null ve CADES_BES'tir")
    void defaultConstructorYieldsCADES_BES() {
        SignCadesDto dto = new SignCadesDto();

        assertEquals(CadesSignatureLevel.CADES_BES, dto.getSignatureLevel(),
                "Field initializer CADES_BES atamalı — non-null kontratının ilk garantisi");
    }

    @Test
    @DisplayName("Setter'a null geçirildiğinde alan CADES_BES'e düşürülür (multipart binding savunması)")
    void setterNullFallsBackToCADES_BES() {
        SignCadesDto dto = new SignCadesDto();
        dto.setSignatureLevel(null);

        assertEquals(CadesSignatureLevel.CADES_BES, dto.getSignatureLevel(),
                "Setter null-guard kontratı: alan asla null kalmamalı");
    }

    @Test
    @DisplayName("Setter'a explicit CADES_LT geçirildiğinde değer korunur")
    void setterExplicitCADES_LTIsPreserved() {
        SignCadesDto dto = new SignCadesDto();
        dto.setSignatureLevel(CadesSignatureLevel.CADES_LT);

        assertEquals(CadesSignatureLevel.CADES_LT, dto.getSignatureLevel(),
                "Setter null-guard fonksiyonel değerleri override etmemeli");
    }

    @Test
    @DisplayName("Setter'a explicit CADES_BES geçirildiğinde değer korunur")
    void setterExplicitCADES_BESIsPreserved() {
        SignCadesDto dto = new SignCadesDto();
        dto.setSignatureLevel(CadesSignatureLevel.CADES_BES);

        assertEquals(CadesSignatureLevel.CADES_BES, dto.getSignatureLevel());
    }
}
