package io.mersel.dss.signer.api.services.signature.pades;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import io.mersel.dss.signer.api.exceptions.TimestampException;
import io.mersel.dss.signer.api.models.SigningMaterial;
import io.mersel.dss.signer.api.models.enums.PadesSignatureLevel;
import io.mersel.dss.signer.api.services.crypto.CryptoSignerService;
import io.mersel.dss.signer.api.services.crypto.DigestAlgorithmResolverService;
import io.mersel.dss.signer.api.services.crypto.SignatureAlgorithmResolverService;
import io.mersel.dss.signer.api.services.timestamp.TimestampConfigurationService;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PAdES imzalama servisi için birim testleri.
 *
 * <p>PAdES artık (CAdES/XAdES gibi) EU DSS {@code PAdESService} üzerinden çalışır;
 * digest seçimi {@link DigestAlgorithmResolverService}'e, kriptografik imzalama
 * {@link CryptoSignerService}'e delege edilir. Bu testler:</p>
 * <ul>
 *   <li>Constructor'ın {@link DigestAlgorithmResolverService} bağımlılığını istediğini,</li>
 *   <li>Uygulama seviyesi enum'unun doğru DSS {@link SignatureLevel}'a eşlendiğini,</li>
 *   <li>TSA gerektiren seviyelerde (T/LT/A) TSA yoksa fail-fast davranıldığını doğrular.</li>
 * </ul>
 */
@Epic("Service Layer")
@Feature("PAdESSignatureService")
@Severity(SeverityLevel.NORMAL)
class PAdESSignatureServiceTest {

    private static X509Certificate rsaCert;
    private static KeyPair rsaPair;

    @BeforeAll
    static void initCert() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        rsaPair = kpg.generateKeyPair();

        X500Name dn = new X500Name("CN=PAdES Test, O=Mersel, C=TR");
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + 365L * 24 * 60 * 60 * 1000);
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(
            rsaPair.getPublic().getEncoded());

        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
            dn, BigInteger.ONE, notBefore, notAfter, dn, spki);

        rsaCert = new JcaX509CertificateConverter().getCertificate(
            builder.build(new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                .build(rsaPair.getPrivate())));
    }

    private static PAdESSignatureService newService(TimestampConfigurationService tsService,
                                                    DigestAlgorithmResolverService digestResolver) {
        PAdESService dssPades = new PAdESService(new CommonCertificateVerifier());
        CryptoSignerService crypto = new CryptoSignerService(new SignatureAlgorithmResolverService());
        return new PAdESSignatureService(dssPades, crypto, digestResolver, tsService, new Semaphore(1));
    }

    @Test
    @DisplayName("PAdES constructor DigestAlgorithmResolverService bağımlılığını ister")
    void constructorRequiresDigestAlgorithmResolverService() {
        Constructor<?>[] ctors = PAdESSignatureService.class.getConstructors();
        boolean found = false;
        for (Constructor<?> c : ctors) {
            for (Class<?> p : c.getParameterTypes()) {
                if (p == DigestAlgorithmResolverService.class) {
                    found = true;
                    break;
                }
            }
        }
        assertTrue(found,
            "PAdESSignatureService DigestAlgorithmResolverService inject etmeli; "
            + "aksi halde CAdES/XAdES ile farklı digest seçer ve aynı sertifika ile "
            + "tutarsız imzalar üretir.");
    }

    @Test
    @DisplayName("Uygulama seviyesi enum'u doğru DSS SignatureLevel'a eşlenir")
    void toDssLevel_mapsAllLevels() throws Exception {
        Method m = PAdESSignatureService.class.getDeclaredMethod("toDssLevel", PadesSignatureLevel.class);
        m.setAccessible(true);

        assertEquals(SignatureLevel.PAdES_BASELINE_B, m.invoke(null, PadesSignatureLevel.PADES_BES));
        assertEquals(SignatureLevel.PAdES_BASELINE_T, m.invoke(null, PadesSignatureLevel.PADES_T));
        assertEquals(SignatureLevel.PAdES_BASELINE_LT, m.invoke(null, PadesSignatureLevel.PADES_LT));
        assertEquals(SignatureLevel.PAdES_BASELINE_LTA, m.invoke(null, PadesSignatureLevel.PADES_A));
    }

    @Test
    @DisplayName("TSA yapılandırılmamışsa PADES_T/LT/A fail-fast (TimestampException) verir")
    void tsaRequiredLevels_failFastWhenTsaMissing() {
        DigestAlgorithmResolverService resolver = mock(DigestAlgorithmResolverService.class);
        when(resolver.resolveDigestAlgorithm(any(X509Certificate.class)))
            .thenReturn(DigestAlgorithm.SHA256);

        // Boş TS yapılandırması → isAvailable() == false.
        TimestampConfigurationService tsService =
            new TimestampConfigurationService("", "", "", false);
        PAdESSignatureService service = newService(tsService, resolver);

        SigningMaterial material = new SigningMaterial(rsaPair.getPrivate(), rsaCert,
            Collections.singletonList(rsaCert));

        for (PadesSignatureLevel level : new PadesSignatureLevel[]{
                PadesSignatureLevel.PADES_T, PadesSignatureLevel.PADES_LT, PadesSignatureLevel.PADES_A}) {
            // PDF içeriği parse edilmeden, TSA kontrolünde fail-fast olmalı.
            assertThrows(TimestampException.class, () ->
                service.signPdf(new ByteArrayInputStream(new byte[]{'%', 'P', 'D', 'F'}),
                    null, null, false, material, level),
                "TSA yoksa " + level + " fail-fast TimestampException vermeli");
        }
    }
}
