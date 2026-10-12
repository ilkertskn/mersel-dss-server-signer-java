package io.mersel.dss.signer.api.e2e.level;

import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.MimeTypeEnum;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.reports.Reports;
import io.mersel.dss.signer.api.exceptions.TimestampException;
import io.mersel.dss.signer.api.models.SignResponse;
import io.mersel.dss.signer.api.models.enums.PadesSignatureLevel;
import io.mersel.dss.signer.api.services.crypto.CryptoSignerService;
import io.mersel.dss.signer.api.services.crypto.DigestAlgorithmResolverService;
import io.mersel.dss.signer.api.services.crypto.SignatureAlgorithmResolverService;
import io.mersel.dss.signer.api.services.signature.pades.PAdESSignatureService;
import io.mersel.dss.signer.api.services.timestamp.TimestampConfigurationService;
import io.mersel.dss.signer.api.e2e.verifier.E2eFixtures;
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
 * PAdES T/LT/A seviyeleri için <b>deterministik, ağsız</b> sign → validate
 * roundtrip testi (DSS PAdES / PDFBox 3 akışı).
 *
 * <p>Bkz. {@link CAdESSignatureLevelRoundtripTest} — aynı yerel PKI + TSA +
 * tam güven zinciri stratejisini kullanır. PAdES'in iText/BouncyCastle'dan
 * DSS'e geçişiyle birlikte B/T/LT/LTA seviyelerinin gerçekten üretildiğini ve
 * doğrulanabildiğini kanıtlar.</p>
 */
@Epic("Signature Levels")
@Feature("PAdES T/LT/A Deterministic Roundtrip")
@Severity(SeverityLevel.BLOCKER)
class PAdESSignatureLevelRoundtripTest {

    private static E2eLocalPki pki;

    @BeforeAll
    static void initPki() {
        pki = new E2eLocalPki();
    }

    private static PAdESSignatureService serviceWithLocalTsa() {
        CertificateVerifier verifier = pki.newCertificateVerifier();
        PAdESService padesService = new PAdESService(verifier);
        CryptoSignerService crypto = new CryptoSignerService(new SignatureAlgorithmResolverService());
        return new PAdESSignatureService(
                padesService, crypto, new DigestAlgorithmResolverService(),
                pki.timestampConfigurationService(), new Semaphore(2));
    }

    @ParameterizedTest(name = "PAdES {0} → {1}")
    @CsvSource({
            "PADES_BES, PAdES_BASELINE_B",
            "PADES_T,   PAdES_BASELINE_T",
            "PADES_LT,  PAdES_BASELINE_LT",
            "PADES_A,   PAdES_BASELINE_LTA"
    })
    @DisplayName("İstenen PAdES seviyesi üretilir ve tam güven altında TOTAL_PASSED doğrulanır")
    void signsAndValidatesAtRequestedLevel(PadesSignatureLevel requested,
                                           SignatureLevel expectedDssLevel) {
        PAdESSignatureService service = serviceWithLocalTsa();
        byte[] pdf = E2eFixtures.padesPdf();

        SignResponse signed = service.signPdf(
                new ByteArrayInputStream(pdf), /*attachment*/ null, /*attachmentName*/ null,
                /*appendMode*/ false, pki.signingMaterial(), requested);

        assertTrue(signed != null && signed.getSignedDocument() != null
                && signed.getSignedDocument().length > 0, "imzalı PDF üretilmeli");

        DSSDocument signedDoc = new InMemoryDocument(
                signed.getSignedDocument(), "signed.pdf", MimeTypeEnum.PDF);
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

    @ParameterizedTest(name = "PAdES {0} TSA yokken fail-fast")
    @CsvSource({"PADES_T", "PADES_LT", "PADES_A"})
    @DisplayName("TSA yapılandırılmamışken T/LT/A talebi fail-fast TimestampException fırlatır")
    void tsaRequiredLevelsFailFastWhenTsaMissing(PadesSignatureLevel requested) {
        CertificateVerifier verifier = pki.newCertificateVerifier();
        PAdESService padesService = new PAdESService(verifier);
        CryptoSignerService crypto = new CryptoSignerService(new SignatureAlgorithmResolverService());
        PAdESSignatureService service = new PAdESSignatureService(
                padesService, crypto, new DigestAlgorithmResolverService(),
                new TimestampConfigurationService("", "", "", false), new Semaphore(2));

        assertThrows(TimestampException.class,
                () -> service.signPdf(new ByteArrayInputStream(E2eFixtures.padesPdf()),
                        null, null, false, pki.signingMaterial(), requested));
    }

    @Test
    @DisplayName("PADES_BES TSA olmadan da üretilir (zaman damgası gerektirmez)")
    void besDoesNotRequireTsa() {
        CertificateVerifier verifier = pki.newCertificateVerifier();
        PAdESService padesService = new PAdESService(verifier);
        CryptoSignerService crypto = new CryptoSignerService(new SignatureAlgorithmResolverService());
        PAdESSignatureService service = new PAdESSignatureService(
                padesService, crypto, new DigestAlgorithmResolverService(),
                new TimestampConfigurationService("", "", "", false), new Semaphore(2));

        SignResponse signed = service.signPdf(
                new ByteArrayInputStream(E2eFixtures.padesPdf()), null, null, false,
                pki.signingMaterial(), PadesSignatureLevel.PADES_BES);
        assertTrue(signed.getSignedDocument().length > 0);
    }
}
