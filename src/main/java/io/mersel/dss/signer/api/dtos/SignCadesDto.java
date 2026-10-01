package io.mersel.dss.signer.api.dtos;

import javax.validation.constraints.NotBlank;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;
import io.mersel.dss.signer.api.models.enums.CadesSignatureLevel;

/**
 * CAdES imzalama endpoint'ine gönderilen isteğin veri taşıma nesnesi.
 *
 * <p>Multipart/form-data olarak gönderilir. {@code document} alanı imzalanacak
 * dosyayı, {@code detached} alanı ise imza modunu belirler.</p>
 *
 * <p>Örnek cURL kullanımı:</p>
 * <pre>{@code
 * curl -X POST http://localhost:8080/v1/cadessign \
 *   -F "document=@fatura.xml" \
 *   -F "detached=true"
 * }</pre>
 */
public class SignCadesDto {

    /**
     * İmzalanacak dosya. Format kısıtlaması yoktur; CAdES her türlü
     * binary ve text içeriği imzalayabilir.
     */
    private MultipartFile Document;

    /**
     * İmza modu seçimi.
     * {@code true} → detached: yalnızca imza üretilir, orijinal dosya ayrı kalır.
     * {@code false} veya {@code null} → attached: dosya CMS zarfının içine gömülür.
     */
    private Boolean Detached;

    /**
     * CAdES imza profili (seviyesi). API kontratı gereği bu alan
     * <strong>asla null değildir</strong>; gönderilmediği takdirde varsayılan
     * {@link CadesSignatureLevel#CADES_BES} uygulanır (TSA çağrılmaz).
     */
    private CadesSignatureLevel SignatureLevel = CadesSignatureLevel.CADES_BES;

    @Schema(description = "İmzalanacak dosya (zorunlu). Her türlü dosya formatı kabul edilir.",
            required = true)
    public MultipartFile getDocument() {
        return Document;
    }

    @NotBlank
    public void setDocument(MultipartFile document) {
        Document = document;
    }

    @Schema(description = "true ise detached imza (ayrık), false ise attached imza (gömülü). Varsayılan: false",
            example = "false")
    public Boolean getDetached() {
        return Detached;
    }

    public void setDetached(Boolean detached) {
        Detached = detached;
    }

    /**
     * Daima non-null bir {@link CadesSignatureLevel} döner; field initializer ve
     * setter null-guard birlikte "asla null" kontratını korur.
     */
    public CadesSignatureLevel getSignatureLevel() {
        return SignatureLevel;
    }

    @Schema(
        enumAsRef = true,
        description = "CAdES imza profili. Boş bırakılırsa CADES_BES uygulanır (TSA çağrılmaz). "
                + "CADES_T (signature timestamp), CADES_LT (uzun dönem doğrulama verisi) veya "
                + "CADES_A (archive timestamp) istenirse TSA gerekir; TSA yapılandırılmamışsa "
                + "istek TIMESTAMP_ERROR ile reddedilir.",
        example = "CADES_BES",
        defaultValue = "CADES_BES"
    )
    public void setSignatureLevel(CadesSignatureLevel signatureLevel) {
        SignatureLevel = signatureLevel != null ? signatureLevel : CadesSignatureLevel.CADES_BES;
    }
}