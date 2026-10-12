package io.mersel.dss.signer.api.services.signature.cades;

import java.io.InputStream;
import java.util.Base64;
import java.util.concurrent.Semaphore;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.cades.signature.CAdESService;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import io.mersel.dss.signer.api.exceptions.SignatureException;
import io.mersel.dss.signer.api.exceptions.TimestampException;
import io.mersel.dss.signer.api.models.SignResponse;
import io.mersel.dss.signer.api.models.SigningMaterial;
import io.mersel.dss.signer.api.models.enums.CadesSignatureLevel;
import io.mersel.dss.signer.api.services.crypto.CryptoSignerService;
import io.mersel.dss.signer.api.services.crypto.DigestAlgorithmResolverService;
import io.mersel.dss.signer.api.services.timestamp.TimestampConfigurationService;

/**
 * CAdES-BES seviyesinde elektronik imza üreten servis.
 *
 * <p>EU DSS (Digital Signature Services) kütüphanesinin {@link CAdESService} altyapısı
 * üzerinden RFC 5652 (CMS) ve ETSI EN 319 122-1 (CAdES Baseline) gereksinimlerine
 * uygun imzalar oluşturur. XAdES imzalama ile aynı mimari kullanılır:</p>
 *
 * <ul>
 *   <li>{@link CAdESService} — CMS zarfı oluşturma, SigningCertificateV2 attribute yönetimi</li>
 *   <li>{@link CryptoSignerService} — HSM-aware kriptografik imzalama</li>
 *   <li>{@link DigestAlgorithmResolverService} — sertifikaya göre digest algoritma çözümleme</li>
 * </ul>
 *
 * <h3>Eşzamanlılık</h3>
 * <p>PKCS#11 (HSM) session havuzlarının tükenmesini engellemek için eş zamanlı imza
 * sayısı bir {@link Semaphore} ile sınırlandırılır.</p>
 *
 * @see CAdESService
 * @see CryptoSignerService
 * @see DigestAlgorithmResolverService
 */
@Service
public class CAdESSignatureService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CAdESSignatureService.class);

    private final CAdESService cadesService;
    private final CryptoSignerService cryptoSigner;
    private final DigestAlgorithmResolverService digestAlgorithmResolver;
    private final TimestampConfigurationService timestampService;
    private final Semaphore semaphore;

    /**
     * @param cadesService            DSS CAdES imza servisi
     * @param cryptoSigner            HSM-aware kriptografik imzalama servisi
     * @param digestAlgorithmResolver sertifikaya göre digest algoritma çözümleme servisi
     * @param timestampService        TSA (zaman damgası) yapılandırma servisi; T/LT/A
     *                                seviyelerinde TSP source sağlar
     * @param signatureSemaphore      eş zamanlı imza işlemi sayısını kısıtlayan semaphore
     */
    public CAdESSignatureService(CAdESService cadesService,
                                 CryptoSignerService cryptoSigner,
                                 DigestAlgorithmResolverService digestAlgorithmResolver,
                                 TimestampConfigurationService timestampService,
                                 Semaphore signatureSemaphore) {
        this.cadesService = cadesService;
        this.cryptoSigner = cryptoSigner;
        this.digestAlgorithmResolver = digestAlgorithmResolver;
        this.timestampService = timestampService;
        this.semaphore = signatureSemaphore;
    }

    /**
     * Verilen stream'deki veriyi okuyarak CAdES-BES imzası üretir.
     *
     * @param dataInputStream imzalanacak dosyanın stream'i — metot içinde tüketilir, kapatılmaz
     * @param detached        {@code true} → ayrık imza, {@code false} → gömülü imza
     * @param material        sertifika zinciri ve private key'i barındıran imzalama materyali
     * @return imzalanmış byte dizisi ve Base64 kodlanmış imza değerini içeren {@link SignResponse}
     * @throws SignatureException imza oluşturma sırasında herhangi bir hata meydana gelirse
     */
    public SignResponse signData(InputStream dataInputStream,
                                 boolean detached,
                                 SigningMaterial material) {
        return signData(dataInputStream, detached, material, CadesSignatureLevel.CADES_BES);
    }

    /**
     * Verilen stream'deki veriyi okuyarak istenen seviyede CAdES imzası üretir.
     *
     * @param dataInputStream imzalanacak dosyanın stream'i — metot içinde tüketilir, kapatılmaz
     * @param detached        {@code true} → ayrık imza, {@code false} → gömülü imza
     * @param material        sertifika zinciri ve private key'i barındıran imzalama materyali
     * @param signatureLevel  istenen CAdES profili. {@link CadesSignatureLevel#CADES_BES} ise
     *                        yalnızca baseline imza üretilir; TSA çağrılmaz.
     *                        T/LT/A için TSA gerekir; yapılandırılmamışsa fail-fast
     *                        ({@link TimestampException}). <strong>null geçilemez.</strong>
     * @return imzalanmış byte dizisi ve Base64 kodlanmış imza değerini içeren {@link SignResponse}
     * @throws SignatureException imza oluşturma sırasında herhangi bir hata meydana gelirse
     * @throws TimestampException T/LT/A istenmiş ve TSA yapılandırılmamışsa
     */
    public SignResponse signData(InputStream dataInputStream,
                                 boolean detached,
                                 SigningMaterial material,
                                 CadesSignatureLevel signatureLevel) {
        try {
            // TSA gerektiren seviyelerde TSP source'u fail-fast olarak doğrula ve bağla —
            // herhangi bir iş yapılmadan ÖNCE (input okuma/param kurma maliyetinden kaçın).
            // Singleton CAdESService'e set ediliyor; aynı (idempotent) source olduğundan
            // eş zamanlı isteklerde sorun yaratmaz — semaphore zaten paralelliği sınırlar.
            if (signatureLevel != CadesSignatureLevel.CADES_BES) {
                if (!timestampService.isAvailable()) {
                    throw new TimestampException(signatureLevel
                            + " profili istendi ancak timestamp sunucusu yapılandırılmamış. "
                            + "TS_SERVER_HOST property'sini ayarlayın.");
                }
                cadesService.setTspSource(timestampService.getTspSource());
            }

            byte[] contentBytes = IOUtils.toByteArray(dataInputStream);
            DSSDocument document = new InMemoryDocument(contentBytes, "document.bin");

            DigestAlgorithm digestAlgorithm =
                    digestAlgorithmResolver.resolveDigestAlgorithm(material.getSigningCertificate());

            CAdESSignatureParameters parameters =
                    buildParameters(detached, digestAlgorithm, material, signatureLevel);

            semaphore.acquire();
            try {
                ToBeSigned dataToSign = cadesService.getDataToSign(document, parameters);

                SignatureValue signatureValue = cryptoSigner.sign(
                        dataToSign,
                        material,
                        digestAlgorithm);

                DSSDocument signedDocument = cadesService.signDocument(document, parameters, signatureValue);

                byte[] signedBytes = IOUtils.toByteArray(signedDocument.openStream());
                String encodedSignature = Base64.getEncoder().encodeToString(signatureValue.getValue());

                LOGGER.info("CAdES imzası başarıyla oluşturuldu (detached: {}, seviye: {})",
                        detached, signatureLevel);
                return new SignResponse(signedBytes, encodedSignature);

            } finally {
                semaphore.release();
            }

        } catch (SignatureException e) {
            // TimestampException de SignatureException alt sınıfıdır; olduğu gibi bubble eder.
            throw e;
        } catch (Exception e) {
            LOGGER.error("CAdES imzası oluşturulurken hata", e);
            throw new SignatureException("CADES_SIGN_ERROR", "CAdES imzası oluşturulamadı", e);
        }
    }

    /**
     * DSS {@link CAdESSignatureParameters} nesnesini oluşturur.
     *
     * <p>İstenen {@link CadesSignatureLevel} ilgili DSS baseline seviyesine
     * eşlenir. Tüm seviyeler {@code SigningCertificateV2} attribute'ünü otomatik
     * olarak ekler.</p>
     *
     * @param detached        ayrık imza mı
     * @param digestAlgorithm kullanılacak digest algoritması
     * @param material        imzalama materyali
     * @param signatureLevel  istenen CAdES profili
     * @return yapılandırılmış CAdES parametreleri
     */
    private CAdESSignatureParameters buildParameters(boolean detached,
                                                     DigestAlgorithm digestAlgorithm,
                                                     SigningMaterial material,
                                                     CadesSignatureLevel signatureLevel) {
        CAdESSignatureParameters parameters = new CAdESSignatureParameters();
        parameters.setSignatureLevel(toDssLevel(signatureLevel));
        parameters.setSignaturePackaging(
                detached ? SignaturePackaging.DETACHED : SignaturePackaging.ENVELOPING);
        parameters.setDigestAlgorithm(digestAlgorithm);
        parameters.setSigningCertificate(material.getPrimaryCertificateToken());
        parameters.setCertificateChain(material.getCertificateTokens());
        return parameters;
    }

    /**
     * Uygulama seviyesi enum'unu DSS {@link SignatureLevel}'a eşler.
     */
    private static SignatureLevel toDssLevel(CadesSignatureLevel level) {
        switch (level) {
            case CADES_BES:
                return SignatureLevel.CAdES_BASELINE_B;
            case CADES_T:
                return SignatureLevel.CAdES_BASELINE_T;
            case CADES_LT:
                return SignatureLevel.CAdES_BASELINE_LT;
            case CADES_A:
                return SignatureLevel.CAdES_BASELINE_LTA;
            default:
                throw new IllegalStateException("Beklenmeyen CAdES seviyesi: " + level);
        }
    }
}
