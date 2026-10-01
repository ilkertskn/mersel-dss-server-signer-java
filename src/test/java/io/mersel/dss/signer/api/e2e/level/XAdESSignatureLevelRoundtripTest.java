package io.mersel.dss.signer.api.e2e.level;

import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.reports.Reports;
import eu.europa.esig.dss.xades.signature.XAdESService;
import io.mersel.dss.signer.api.exceptions.TimestampException;
import io.mersel.dss.signer.api.models.SignResponse;
import io.mersel.dss.signer.api.models.enums.DocumentType;
import io.mersel.dss.signer.api.models.enums.XadesSignatureLevel;
import io.mersel.dss.signer.api.services.crypto.CryptoSignerService;
import io.mersel.dss.signer.api.services.crypto.DigestAlgorithmResolverService;
import io.mersel.dss.signer.api.services.crypto.SignatureAlgorithmResolverService;
import io.mersel.dss.signer.api.services.signature.xades.XAdESDocumentPlacementService;
import io.mersel.dss.signer.api.services.signature.xades.XAdESLevelUpgradeService;
import io.mersel.dss.signer.api.services.signature.xades.XAdESParametersBuilderService;
import io.mersel.dss.signer.api.services.signature.xades.XAdESSignatureService;
import io.mersel.dss.signer.api.services.signature.xades.XmlProcessingService;
import io.mersel.dss.signer.api.services.timestamp.TimestampConfigurationService;
import io.mersel.dss.signer.api.services.util.CompressionService;
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
import java.util.UUID;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XAdES T/LT/A seviyeleri için <b>deterministik, ağsız</b> sign → validate
 * roundtrip testi.
 *
 * <p>Bkz. {@link CAdESSignatureLevelRoundtripTest} — aynı yerel PKI + TSA +
 * tam güven zinciri stratejisini kullanır.</p>
 *
 * <h3>Seviye eşlemesi notu</h3>
 * <p>Proje, XADES_LT ve XADES_A için bilinçli olarak <em>legacy</em> DSS
 * extension'larını ({@code XAdESLevelXL} / {@code XAdESLevelA}) kullanır
 * (OCSP/CRL cache override'ı ile tutarlılık için). Bu nedenle DSS doğrulaması
 * bu profilleri baseline değil legacy seviye adlarıyla ({@code XAdES_XL},
 * {@code XAdES_A}) raporlar. Test bu kontratı birebir doğrular.</p>
 */
@Epic("Signature Levels")
@Feature("XAdES T/LT/A Deterministic Roundtrip")
@Severity(SeverityLevel.BLOCKER)
class XAdESSignatureLevelRoundtripTest {

    private static E2eLocalPki pki;

    @BeforeAll
    static void initPki() {
        pki = new E2eLocalPki();
    }

    private static XAdESSignatureService serviceWith(TimestampConfigurationService tsConfig) {
        CertificateVerifier verifier = pki.newCertificateVerifier();
        XAdESService xadesService = new XAdESService(verifier);

        DigestAlgorithmResolverService digestResolver = new DigestAlgorithmResolverService();
        SignatureAlgorithmResolverService sigAlgResolver = new SignatureAlgorithmResolverService();
        CryptoSignerService crypto = new CryptoSignerService(sigAlgResolver);

        XAdESParametersBuilderService paramsBuilder = new XAdESParametersBuilderService(digestResolver);
        XmlProcessingService xmlProcessor = new XmlProcessingService();
        XAdESDocumentPlacementService placement = new XAdESDocumentPlacementService();
        XAdESLevelUpgradeService upgrade = new XAdESLevelUpgradeService(verifier, tsConfig);
        CompressionService compression = new CompressionService();

        return new XAdESSignatureService(
                xadesService, paramsBuilder, xmlProcessor, placement, upgrade,
                crypto, verifier, compression, new Semaphore(2));
    }

    @ParameterizedTest(name = "XAdES {0} → {1}")
    @CsvSource({
            "XADES_BES, XAdES_BASELINE_B",
            "XADES_T,   XAdES_BASELINE_T",
            "XADES_LT,  XAdES_XL",
            "XADES_A,   XAdES_A"
    })
    @DisplayName("İstenen XAdES seviyesi üretilir ve tam güven altında TOTAL_PASSED doğrulanır")
    void signsAndValidatesAtRequestedLevel(XadesSignatureLevel requested,
                                           SignatureLevel expectedDssLevel) {
        XAdESSignatureService service = serviceWith(pki.timestampConfigurationService());
        byte[] xml = E2eFixtures.genericXml();
        String signatureId = "id-" + UUID.randomUUID().toString().replace("-", "");

        SignResponse signed = service.signXml(
                new ByteArrayInputStream(xml), DocumentType.OtherXmlDocument,
                signatureId, /*zipped*/ false, pki.signingMaterial(), requested);

        assertTrue(signed != null && signed.getSignedDocument() != null
                && signed.getSignedDocument().length > 0, "imzalı XML üretilmeli");

        DSSDocument signedDoc = new InMemoryDocument(signed.getSignedDocument(), "signed.xml");
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

    @ParameterizedTest(name = "XAdES {0} TSA yokken fail-fast")
    @CsvSource({"XADES_T", "XADES_LT", "XADES_A"})
    @DisplayName("TSA yapılandırılmamışken T/LT/A talebi fail-fast TimestampException fırlatır")
    void tsaRequiredLevelsFailFastWhenTsaMissing(XadesSignatureLevel requested) {
        XAdESSignatureService service = serviceWith(
                new TimestampConfigurationService("", "", "", false));

        TimestampException ex = assertThrows(TimestampException.class,
                () -> service.signXml(new ByteArrayInputStream(E2eFixtures.genericXml()),
                        DocumentType.OtherXmlDocument,
                        "id-" + UUID.randomUUID().toString().replace("-", ""),
                        false, pki.signingMaterial(), requested));
        assertEquals("TIMESTAMP_ERROR", ex.getErrorCode());
    }

    @Test
    @DisplayName("XADES_BES TSA olmadan da üretilir (zaman damgası gerektirmez)")
    void besDoesNotRequireTsa() {
        XAdESSignatureService service = serviceWith(
                new TimestampConfigurationService("", "", "", false));

        SignResponse signed = service.signXml(
                new ByteArrayInputStream(E2eFixtures.genericXml()),
                DocumentType.OtherXmlDocument,
                "id-" + UUID.randomUUID().toString().replace("-", ""),
                false, pki.signingMaterial(), XadesSignatureLevel.XADES_BES);
        assertTrue(signed.getSignedDocument().length > 0);
    }
}
