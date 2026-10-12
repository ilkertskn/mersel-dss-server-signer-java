package io.mersel.dss.signer.api.dtos;

import io.mersel.dss.signer.api.models.enums.PadesSignatureLevel;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SignPadesDto} DTO katmanı kontrat testleri.
 *
 * <p>{@link SignXadesDtoTest} ile aynı sözleşme: <em>SignatureLevel asla null
 * olamaz</em>. Multipart form binding, JSON deserialize veya manuel setter
 * çağrılarından gelen null değerler {@link PadesSignatureLevel#PADES_BES}
 * fallback'ine düşürülür.</p>
 */
@Epic("API Contract")
@Feature("SignPadesDto")
@Severity(SeverityLevel.NORMAL)
class SignPadesDtoTest {

    @Test
    @DisplayName("Default ctor sonrası SignatureLevel non-null ve PADES_BES'tir")
    void defaultConstructorYieldsPADES_BES() {
        SignPadesDto dto = new SignPadesDto();

        assertEquals(PadesSignatureLevel.PADES_BES, dto.getSignatureLevel(),
                "Field initializer PADES_BES atamalı — non-null kontratının ilk garantisi");
    }

    @Test
    @DisplayName("Setter'a null geçirildiğinde alan PADES_BES'e düşürülür (multipart binding savunması)")
    void setterNullFallsBackToPADES_BES() {
        SignPadesDto dto = new SignPadesDto();
        dto.setSignatureLevel(null);

        assertEquals(PadesSignatureLevel.PADES_BES, dto.getSignatureLevel(),
                "Setter null-guard kontratı: alan asla null kalmamalı");
    }

    @Test
    @DisplayName("Setter'a explicit PADES_LT geçirildiğinde değer korunur")
    void setterExplicitPADES_LTIsPreserved() {
        SignPadesDto dto = new SignPadesDto();
        dto.setSignatureLevel(PadesSignatureLevel.PADES_LT);

        assertEquals(PadesSignatureLevel.PADES_LT, dto.getSignatureLevel(),
                "Setter null-guard fonksiyonel değerleri override etmemeli");
    }

    @Test
    @DisplayName("Setter'a explicit PADES_BES geçirildiğinde değer korunur")
    void setterExplicitPADES_BESIsPreserved() {
        SignPadesDto dto = new SignPadesDto();
        dto.setSignatureLevel(PadesSignatureLevel.PADES_BES);

        assertEquals(PadesSignatureLevel.PADES_BES, dto.getSignatureLevel());
    }
}
