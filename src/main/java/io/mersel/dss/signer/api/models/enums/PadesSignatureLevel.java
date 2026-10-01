package io.mersel.dss.signer.api.models.enums;

/**
 * PAdES (PDF) imza profili (seviyesi).
 *
 * <h3>Karar Verici Sözleşmesi</h3>
 * <p>İmza seviyesinin tek karar vericisi <em>request</em>'tir.</p>
 *
 * <h3>Profiller</h3>
 * <ul>
 *   <li>{@link #PADES_BES} — Yalnızca imza, TSA çağrılmaz.</li>
 *   <li>{@link #PADES_T}   — İmza (document) timestamp'i eklenir; TSA gerekir.</li>
 *   <li>{@link #PADES_LT}  — Uzun dönem doğrulama verisi (DSS dictionary: sertifika +
 *       OCSP/CRL); TSA gerekir.</li>
 *   <li>{@link #PADES_A}   — Document timestamp ile arşiv profili; TSA gerekir.</li>
 * </ul>
 *
 * <h3>Varsayılan</h3>
 * <p>API kontratı gereği bu alan <strong>null olamaz</strong>. Request'te alan
 * gönderilmezse {@link #PADES_BES} değeri uygulanır.</p>
 *
 * <h3>Fail-fast Sözleşmesi</h3>
 * <p>TSA gerektiren seviyeler (T/LT/A) istenir ve timestamp sunucusu
 * yapılandırılmamışsa istek fail-fast olarak {@code TIMESTAMP_ERROR} ile reddedilir.</p>
 *
 * <h3>DSS Eşlemesi</h3>
 * <ul>
 *   <li>{@code PADES_BES} → {@code SignatureLevel.PAdES_BASELINE_B}</li>
 *   <li>{@code PADES_T}   → {@code SignatureLevel.PAdES_BASELINE_T}</li>
 *   <li>{@code PADES_LT}  → {@code SignatureLevel.PAdES_BASELINE_LT}</li>
 *   <li>{@code PADES_A}   → {@code SignatureLevel.PAdES_BASELINE_LTA}</li>
 * </ul>
 */
public enum PadesSignatureLevel {

    /**
     * PAdES-BES (Baseline-B): yalnızca imza. Hiçbir TSA çağrısı yapılmaz.
     * <b>API'nin varsayılan değeri.</b>
     */
    PADES_BES,

    /**
     * PAdES-T (Baseline-T): imzaya signature timestamp eklenir; TSA gerekir.
     */
    PADES_T,

    /**
     * PAdES-LT (Baseline-LT): imza + timestamp + DSS dictionary içinde uzun dönem
     * doğrulama verisi (sertifika zinciri, OCSP/CRL). TSA gerekir.
     */
    PADES_LT,

    /**
     * PAdES-A (Baseline-LTA): imza + LT + document (archive) timestamp.
     * Arşivsel akışlar için uygundur; TSA gerekir.
     */
    PADES_A
}
