package io.mersel.dss.signer.api.e2e.level;

import eu.europa.esig.dss.cades.signature.CAdESService;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.reports.Reports;
import io.mersel.dss.signer.api.exceptions.TimestampException;
import io.mersel.dss.signer.api.models.SignResponse;
import io.mersel.dss.signer.api.services.crypto.CryptoSignerService;
import io.mersel.dss.signer.api.services.crypto.DigestAlgorithmResolverService;
import io.mersel.dss.signer.api.services.crypto.SignatureAlgorithmResolverService;
import io.mersel.dss.signer.api.services.signature.cades.CAdESSignatureService;
import io.mersel.dss.signer.api.services.timestamp.TimestampConfigurationService;
import io.mersel.dss.signer.api.models.enums.CadesSignatureLevel;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAdES T/LT/A seviyeleri için <b>deterministik, ağsız</b> sign → validate
 * roundtrip testi.
 *
 * <h3>Neden verifier-e2e değil?</h3>
 * <p>Bu test harici verifier-api konteynerine veya ağa bağlı değildir. Bunun
 * yerine {@link E2eLocalPki} ile tamamen yerel bir PKI + TSA kurar ve üretilen
 * imzayı DSS'in kendi {@link eu.europa.esig.dss.validation.SignedDocumentValidator}
 * 'ı ile <b>tam güven zinciri</b> altında doğrular. Böylece her ortamda
 * (CI dahil, Docker/ağ olmadan) gerçekten çalışır ve seviyenin kriptografik
 * olarak elde edildiğini kanıtlar.</p>
 *
 * <h3>Ne doğrulanıyor?</h3>
 * <ul>
 *   <li>İstenen {@link CadesSignatureLevel} → beklenen DSS {@link SignatureLevel}
 *       (B/T/LT/LTA) gerçekten üretiliyor (yapısal kanıt).</li>
 *   <li>Validation indication {@code TOTAL_PASSED} — imza bütünlüğü, zincir,
 *       süre, iptal ve zaman damgası doğrulamalarının tümü geçiyor (güvenlik
 *       kontrolleri atlanmıyor).</li>
 *   <li>TSA yapılandırılmamışken T/LT/A talebi fail-fast
 *       {@link TimestampException} fırlatıyor.</li>
 * </ul>
 */
@Epic("Signature Levels")
@Feature("CAdES T/LT/A Deterministic Roundtrip")
@Severity(SeverityLevel.BLOCKER)
class CAdESSignatureLevelRoundtripTest {

    private static E2eLocalPki pki;

    @BeforeAll
    static void initPki() {
        pki = new E2eLocalPki();
    }

    private static CAdESSignatureService serviceWithLocalTsa() {
        CertificateVerifier verifier = pki.newCertificateVerifier();
        CAdESService cadesService = new CAdESService(verifier);
        CryptoSignerService crypto = new CryptoSignerService(new SignatureAlgorithmResolverService());
        return new CAdESSignatureService(
                cadesService, crypto, new DigestAlgorithmResolverService(),
                pki.timestampConfigurationService(), new Semaphore(2));
    }

    @ParameterizedTest(name = "CAdES {0} → {1}")
    @CsvSource({
            "CADES_BES, CAdES_BASELINE_B",
            "CADES_T,   CAdES_BASELINE_T",
            "CADES_LT,  CAdES_BASELINE_LT",
            "CADES_A,   CAdES_BASELINE_LTA"
    })
    @DisplayName("İstenen CAdES seviyesi üretilir ve tam güven altında TOTAL_PASSED doğrulanır")
    void signsAndValidatesAtRequestedLevel(CadesSignatureLevel requested,
                                           SignatureLevel expectedDssLevel) {
        CAdESSignatureService service = serviceWithLocalTsa();
        byte[] data = ("Mersel CAdES level roundtrip — " + requested).getBytes();

        SignResponse signed = service.signData(
                new ByteArrayInputStream(data), /*detached*/ false,
                pki.signingMaterial(), requested);

        assertTrue(signed != null && signed.getSignedDocument() != null
                && signed.getSignedDocument().length > 0, "imzalı CMS üretilmeli");

        DSSDocument signedDoc = new InMemoryDocument(signed.getSignedDocument(), "signed.p7s");
        Reports reports = pki.validatorFor(signedDoc).validateDocument();
        SimpleReport report = reports.getSimpleReport();
        String sigId = report.getFirstSignatureId();

        assertEquals(expectedDssLevel, report.getSignatureFormat(sigId),
                "İstenen seviye DSS tarafından tespit edilen formatla eşleşmeli");
        assertEquals(Indication.TOTAL_PASSED, report.getIndication(sigId),
                "Tam güven zinciri altında doğrulama TOTAL_PASSED olmalı — "
                        + "subInd=" + report.getSubIndication(sigId)
                        + " errors=" + report.getAdESValidationErrors(sigId));
    }

    @ParameterizedTest(name = "CAdES {0} TSA yokken fail-fast")
    @CsvSource({"CADES_T", "CADES_LT", "CADES_A"})
    @DisplayName("TSA yapılandırılmamışken T/LT/A talebi fail-fast TimestampException fırlatır")
    void tsaRequiredLevelsFailFastWhenTsaMissing(CadesSignatureLevel requested) {
        CertificateVerifier verifier = pki.newCertificateVerifier();
        CAdESService cadesService = new CAdESService(verifier);
        CryptoSignerService crypto = new CryptoSignerService(new SignatureAlgorithmResolverService());
        // TSA yapılandırılmamış (boş) servis — fail-fast beklenir.
        CAdESSignatureService service = new CAdESSignatureService(
                cadesService, crypto, new DigestAlgorithmResolverService(),
                new TimestampConfigurationService("", "", "", false), new Semaphore(2));

        TimestampException ex = assertThrows(TimestampException.class,
                () -> service.signData(new ByteArrayInputStream("x".getBytes()),
                        false, pki.signingMaterial(), requested));
        assertEquals("TIMESTAMP_ERROR", ex.getErrorCode());
    }

    @Test
    @DisplayName("CADES_BES TSA olmadan da üretilir (zaman damgası gerektirmez)")
    void besDoesNotRequireTsa() {
        CertificateVerifier verifier = pki.newCertificateVerifier();
        CAdESService cadesService = new CAdESService(verifier);
        CryptoSignerService crypto = new CryptoSignerService(new SignatureAlgorithmResolverService());
        CAdESSignatureService service = new CAdESSignatureService(
                cadesService, crypto, new DigestAlgorithmResolverService(),
                new TimestampConfigurationService("", "", "", false), new Semaphore(2));

        SignResponse signed = service.signData(
                new ByteArrayInputStream("bes".getBytes()), false,
                pki.signingMaterial(), CadesSignatureLevel.CADES_BES);
        assertTrue(signed.getSignedDocument().length > 0);
    }
}
