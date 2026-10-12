package io.mersel.dss.signer.api.models.enums;

/**
 * XAdES imza profili (seviyesi).
 *
 * <h3>Karar Verici Sözleşmesi</h3>
 * <p>İmza seviyesinin tek karar vericisi <em>request</em>'tir. {@code documentType}
 * (örn. {@link DocumentType#EArchiveReport}) seviyenin seçimine artık dahil değildir;
 * sistem belge tipine bakarak otomatik olarak XADES_A'ya yükseltme yapmaz.</p>
 *
 * <h3>Profiller</h3>
 * <ul>
 *   <li>{@link #XADES_BES} — Yalnızca imza, TSA çağrılmaz.</li>
 *   <li>{@link #XADES_T}   — İmza timestamp'i eklenir; TSA gerekir.</li>
 *   <li>{@link #XADES_LT}  — Uzun dönem doğrulama verisi (sertifika + iptal değerleri,
 *       legacy XAdES-X-L); imza timestamp'i içerir, TSA gerekir.</li>
 *   <li>{@link #XADES_A}   — Archive timestamp ile genişletilmiş profil; TSA gerekir.</li>
 * </ul>
 *
 * <h3>Varsayılan</h3>
 * <p>API kontratı gereği bu alan <strong>null olamaz</strong>. Request'te alan
 * gönderilmezse {@link #XADES_BES} değeri uygulanır — yani upgrade akışına
 * girilmez ve TSA'ya tek bir RTT bile gitmez.</p>
 *
 * <h3>Mali Sorumluluk</h3>
 * <p>e-Arşiv Raporu / e-Bilet Raporu gibi 10 yıllık saklama gerektiren akışlarda
 * {@code XADES_A}'nın istenmesi <em>çağıran tarafın sorumluluğundadır</em>.
 * Sistem belge tipine göre proaktif yükseltme yapmaz; "implicit upgrade" davranışı
 * v0.x serisinde kaldırılmıştır.</p>
 *
 * <h3>DSS Eşlemesi</h3>
 * <ul>
 *   <li>{@code XADES_BES} → {@code XAdES_BASELINE_B} (override edilmiş baseline imza)</li>
 *   <li>{@code XADES_T}   → {@code XAdESLevelBaselineT} (signature timestamp)</li>
 *   <li>{@code XADES_LT}  → {@code XAdESLevelXL} (legacy XAdES-X-L: CertificateValues +
 *       RevocationValues; OCSP/CRL cache override'ı bu yolda devrededir)</li>
 *   <li>{@code XADES_A}   → {@code XAdESLevelA}
 *       (legacy ETSI TS 101 903 terminolojisindeki XAdES-A / archive profili)</li>
 * </ul>
 */
public enum XadesSignatureLevel {

    /**
     * Baseline-B profili: yalnızca <code>ds:Signature</code> ve <code>xades:SignedProperties</code>.
     * Hiçbir TSA çağrısı yapılmaz; kontör harcanmaz.
     * e-Fatura, e-Arşiv faturası, irsaliye, uygulama yanıtı, HrXml, e-Adisyon raporu,
     * e-Döviz raporu (iptal hariç) vb. timestamp gerektirmeyen tüm akışlar için.
     */
    XADES_BES,

    /**
     * XAdES-T profili: baseline imzaya bir <code>SignatureTimeStamp</code> eklenir.
     * İmzanın belirli bir andan önce var olduğunu ispatlar.
     *
     * <p><strong>Zorunlu önkoşul</strong>: TSA host'u yapılandırılmış olmalı
     * (örn. {@code TS_SERVER_HOST}). Aksi halde imza akışı fail-fast olarak
     * {@code TIMESTAMP_ERROR} ile reddedilir.</p>
     */
    XADES_T,

    /**
     * XAdES-LT profili (legacy XAdES-X-L): imza + signature timestamp + uzun dönem
     * doğrulama verisi (<code>CertificateValues</code> ve <code>RevocationValues</code>
     * — gömülü sertifika zinciri, OCSP/CRL yanıtları). Doğrulayanın çevrimiçi
     * kaynaklara erişmeden imzayı uzun vadede doğrulamasını sağlar.
     *
     * <p><strong>Zorunlu önkoşul</strong>: TSA host'u yapılandırılmış olmalı; aksi
     * halde fail-fast olarak {@code TIMESTAMP_ERROR} döner.</p>
     */
    XADES_LT,

    /**
     * Baseline-LTA profili (legacy adıyla XAdES-A): imza + signature timestamp +
     * archive timestamp. e-Arşiv Raporu / e-Bilet Raporu gibi arşivsel akışlar için
     * uygundur.
     *
     * <p><strong>Zorunlu önkoşul</strong>: TSA host'u yapılandırılmış
     * olmalı (örn. {@code TS_SERVER_HOST} property). Aksi halde imza akışı
     * fail-fast olarak {@code TIMESTAMP_ERROR} ile reddedilir — XADES_BES
     * seviyesinde sessiz fallback yapılmaz (silent data corruption pattern'inden
     * kasıtlı kaçınma).</p>
     */
    XADES_A
}
