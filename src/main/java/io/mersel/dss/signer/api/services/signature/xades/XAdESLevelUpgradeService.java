package io.mersel.dss.signer.api.services.signature.xades;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.XAdESTimestampParameters;
import eu.europa.esig.dss.xades.signature.XAdESLevelA;
import eu.europa.esig.dss.xades.signature.XAdESLevelBaselineT;
import eu.europa.esig.dss.xades.signature.XAdESLevelXL;
import io.mersel.dss.signer.api.exceptions.TimestampException;
import io.mersel.dss.signer.api.models.enums.XadesSignatureLevel;
import io.mersel.dss.signer.api.services.timestamp.TimestampConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.crypto.dsig.CanonicalizationMethod;

/**
 * XAdES imza seviyelerini yükselten servis.
 *
 * <h3>Karar Verici Sözleşmesi</h3>
 * <p>İmza seviyesinin <em>tek</em> karar vericisi request'te gelen
 * {@link XadesSignatureLevel} alanıdır. Belge tipi (örn. {@code EArchiveReport},
 * {@code EBiletReport}) seviyenin seçimine dahil değildir; sistem belge tipine
 * bakarak otomatik olarak XADES_A'ya yükseltme yapmaz. v0.x'te kalan
 * "rapor tipinde implicit upgrade" davranışı kaldırılmıştır.</p>
 *
 * <h3>Davranış Tablosu</h3>
 * <table>
 *   <tr><th>requestedLevel</th><th>Davranış</th></tr>
 *   <tr><td>{@link XadesSignatureLevel#XADES_BES}</td><td>Orijinal belge döner. TSA'ya çağrı yok.</td></tr>
 *   <tr><td>{@link XadesSignatureLevel#XADES_T}</td><td>Signature timestamp eklenir. TSA gerekir.</td></tr>
 *   <tr><td>{@link XadesSignatureLevel#XADES_LT}</td><td>T + CertificateValues/RevocationValues eklenir. TSA gerekir.</td></tr>
 *   <tr><td>{@link XadesSignatureLevel#XADES_A}</td><td>Archive timestamp eklenir.
 *       TSA yapılandırılmamışsa {@link TimestampException} fırlatılır (fail-fast).</td></tr>
 * </table>
 * <p>TSA gerektiren tüm seviyelerde (T/LT/A) timestamp sunucusu yapılandırılmamışsa
 * {@link TimestampException} ile fail-fast davranılır.</p>
 *
 * <h3>Non-null Kontratı</h3>
 * <p>{@code requestedLevel} parametresi <strong>asla null olamaz</strong>. DTO
 * katmanı (getter) zaten null'u {@code XADES_BES}'e düşürür; bu servise null
 * gelmesi {@link NullPointerException} ile programlama hatası olarak yansır.</p>
 *
 * <h3>Fail-fast sözleşmesi (XADES_A iken)</h3>
 * <ul>
 *   <li>Timestamp sunucusu yapılandırılmamışsa (TS_SERVER_HOST boş)
 *       sessiz fallback yerine {@link TimestampException} fırlatılır
 *       — caller HTTP 503 + {@code TIMESTAMP_ERROR} alır.</li>
 *   <li>XADES_A yükseltmesi sırasında oluşan herhangi bir hata da
 *       {@link TimestampException} olarak yukarı bubble edilir; XADES_BES
 *       seviyesinde "yarım imzalı" belge üretilmez (silent data corruption
 *       pattern'inden kasıtlı kaçınma).</li>
 * </ul>
 *
 * <h3>Mali Sorumluluk Notu</h3>
 * <p>e-Arşiv Raporu / e-Bilet Raporu gibi 10 yıllık saklama gerektiren akışlarda
 * XADES_A profilinin talep edilmesi çağıran tarafın sorumluluğundadır. Sistem
 * documentType'a göre proaktif yükseltme yapmadığı için, eski client'ların
 * doğrudan {@code XadesSignatureLevel.XADES_A} göndermesi gerekir.</p>
 */
@Service
public class XAdESLevelUpgradeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(XAdESLevelUpgradeService.class);

    private final CertificateVerifier certificateVerifier;
    private final TimestampConfigurationService timestampService;

    public XAdESLevelUpgradeService(CertificateVerifier certificateVerifier,
                                   TimestampConfigurationService timestampService) {
        this.certificateVerifier = certificateVerifier;
        this.timestampService = timestampService;
    }

    /**
     * Request'te gelen imza profiline göre belgeyi gerekirse XADES_A seviyesine yükseltir.
     *
     * @param signedDocument İmzalanmış belge (baseline XAdES-B çıktısı)
     * @param baseParameters Temel imza parametreleri
     * @param requestedLevel İstenen XAdES profili. {@link XadesSignatureLevel#XADES_BES}
     *                       ise orijinal belge döner; {@link XadesSignatureLevel#XADES_A}
     *                       ise archive timestamp eklenir. <strong>null geçilemez</strong>.
     * @return Seviyesi yükseltilmiş belge ({@code XADES_A} için) veya orijinal belge
     * @throws TimestampException {@code XADES_A} istenmiş ve TSA yapılandırılmamışsa veya
     *         yükseltme işlemi başarısız olursa
     */
    public DSSDocument upgradeIfNeeded(DSSDocument signedDocument,
                                      XAdESSignatureParameters baseParameters,
                                      XadesSignatureLevel requestedLevel) {
        if (requestedLevel == XadesSignatureLevel.XADES_BES) {
            // XADES_BES — TSA'ya tek bir RTT bile gitmesin.
            return signedDocument;
        }

        if (!timestampService.isAvailable()) {
            String message = requestedLevel + " profili istendi ancak timestamp sunucusu yapılandırılmamış. "
                    + "TS_SERVER_HOST property'sini ayarlayın.";
            LOGGER.error(message);
            throw new TimestampException(message);
        }

        try {
            LOGGER.info("{} seviyesine yükseltiliyor (request bazlı)...", requestedLevel);

            XAdESTimestampParameters tsParams = new XAdESTimestampParameters();
            tsParams.setCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS);
            tsParams.setDigestAlgorithm(DigestAlgorithm.SHA256);

            baseParameters.setArchiveTimestampParameters(tsParams);
            baseParameters.setSignatureTimestampParameters(tsParams);
            baseParameters.setContentTimestampParameters(tsParams);
            baseParameters.setEn319132(false);

            XAdESLevelBaselineT extension = createExtension(requestedLevel);
            extension.setTspSource(timestampService.getTspSource());

            DSSDocument upgradedDocument = extension.extendSignatures(signedDocument, baseParameters);

            LOGGER.info("Belge başarıyla {} seviyesine yükseltildi", requestedLevel);
            return upgradedDocument;

        } catch (TimestampException ex) {
            // TimestampConfigurationService.getTspSource() zaten doğru exception'ı fırlatır;
            // tekrar sarmalamadan bubble et ki orijinal hata kodu/mesajı korunsun.
            throw ex;
        } catch (Exception ex) {
            String message = requestedLevel + " yükseltmesi başarısız. Belge XADES_BES seviyesinde "
                    + "bırakılamaz; imza işlemi reddediliyor.";
            LOGGER.error(message, ex);
            throw new TimestampException(message, ex);
        }
    }

    /**
     * İstenen seviyeye karşılık gelen DSS extension builder'ını seçer.
     *
     * <p>Override edilmiş legacy zincir kullanılır: {@code XAdESLevelA} →
     * {@code XAdESLevelXL} → {@code XAdESLevelC} → {@code XAdESLevelBaselineT}.
     * Üst seviye sınıf çağrıldığında alt seviyeler de sırayla uygulanır; bu
     * sayede OCSP/CRL cache override'ı LT ve A yollarında tutarlı çalışır.</p>
     *
     * <ul>
     *   <li>{@link XadesSignatureLevel#XADES_T}  → {@link XAdESLevelBaselineT} (yalnız signature timestamp)</li>
     *   <li>{@link XadesSignatureLevel#XADES_LT} → {@link XAdESLevelXL} (T + CertificateValues/RevocationValues)</li>
     *   <li>{@link XadesSignatureLevel#XADES_A}  → {@link XAdESLevelA} (LT + archive timestamp)</li>
     * </ul>
     */
    private XAdESLevelBaselineT createExtension(XadesSignatureLevel requestedLevel) {
        switch (requestedLevel) {
            case XADES_T:
                return new XAdESLevelBaselineT(certificateVerifier);
            case XADES_LT:
                return new XAdESLevelXL(certificateVerifier);
            case XADES_A:
                return new XAdESLevelA(certificateVerifier);
            default:
                throw new IllegalStateException(
                        "Beklenmeyen XAdES seviyesi (yükseltme dışı): " + requestedLevel);
        }
    }
}
