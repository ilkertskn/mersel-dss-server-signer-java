package io.mersel.dss.signer.api.models.enums;

/**
 * CAdES imza profili (seviyesi).
 *
 * <h3>Karar Verici Sözleşmesi</h3>
 * <p>İmza seviyesinin tek karar vericisi <em>request</em>'tir. Seviye belge
 * tipinden bağımsızdır.</p>
 *
 * <h3>Profiller</h3>
 * <ul>
 *   <li>{@link #CADES_BES} — Yalnızca imza, TSA çağrılmaz.</li>
 *   <li>{@link #CADES_T}   — İmza timestamp'i eklenir; TSA gerekir.</li>
 *   <li>{@link #CADES_LT}  — Uzun dönem doğrulama verisi (sertifika + iptal değerleri);
 *       TSA gerekir.</li>
 *   <li>{@link #CADES_A}   — Archive timestamp ile genişletilmiş profil; TSA gerekir.</li>
 * </ul>
 *
 * <h3>Varsayılan</h3>
 * <p>API kontratı gereği bu alan <strong>null olamaz</strong>. Request'te alan
 * gönderilmezse {@link #CADES_BES} değeri uygulanır — yani upgrade akışına
 * girilmez ve TSA'ya tek bir RTT bile gitmez.</p>
 *
 * <h3>Fail-fast Sözleşmesi</h3>
 * <p>TSA gerektiren seviyeler (T/LT/A) istenir ve timestamp sunucusu
 * yapılandırılmamışsa istek fail-fast olarak {@code TIMESTAMP_ERROR} ile reddedilir;
 * sessiz fallback yapılmaz.</p>
 *
 * <h3>DSS Eşlemesi</h3>
 * <ul>
 *   <li>{@code CADES_BES} → {@code SignatureLevel.CAdES_BASELINE_B}</li>
 *   <li>{@code CADES_T}   → {@code SignatureLevel.CAdES_BASELINE_T}</li>
 *   <li>{@code CADES_LT}  → {@code SignatureLevel.CAdES_BASELINE_LT}</li>
 *   <li>{@code CADES_A}   → {@code SignatureLevel.CAdES_BASELINE_LTA}</li>
 * </ul>
 */
public enum CadesSignatureLevel {

    /**
     * CAdES-BES (Baseline-B): yalnızca imza + {@code SigningCertificateV2}.
     * Hiçbir TSA çağrısı yapılmaz; kontör harcanmaz. <b>API'nin varsayılan değeri.</b>
     */
    CADES_BES,

    /**
     * CAdES-T (Baseline-T): imzaya signature timestamp eklenir; TSA gerekir.
     */
    CADES_T,

    /**
     * CAdES-LT (Baseline-LT): imza + signature timestamp + uzun dönem doğrulama
     * verisi (sertifika zinciri, OCSP/CRL). TSA gerekir.
     */
    CADES_LT,

    /**
     * CAdES-A (Baseline-LTA): imza + signature timestamp + archive timestamp.
     * Arşivsel akışlar için uygundur; TSA gerekir.
     */
    CADES_A
}
