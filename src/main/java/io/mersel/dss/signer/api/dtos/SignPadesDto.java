package io.mersel.dss.signer.api.dtos;

import javax.validation.constraints.NotBlank;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;
import io.mersel.dss.signer.api.models.enums.PadesSignatureLevel;

public class SignPadesDto {
    private MultipartFile Document;
    private MultipartFile Attachment;
    private String AttachmentFileName;
    private Boolean AppendMode;

    /**
     * PAdES imza profili (seviyesi). API kontratı gereği bu alan
     * <strong>asla null değildir</strong>; gönderilmediği takdirde varsayılan
     * {@link PadesSignatureLevel#PADES_BES} uygulanır (TSA çağrılmaz).
     */
    private PadesSignatureLevel SignatureLevel = PadesSignatureLevel.PADES_BES;

    public MultipartFile getDocument() {
        return Document;
    }

    @NotBlank
    public void setDocument(MultipartFile document) {
        Document = document;
    }

    public MultipartFile getAttachment() {
        return Attachment;
    }

    public void setAttachment(MultipartFile attachment) {
        Attachment = attachment;
    }

    public String getAttachmentFileName() {
        return AttachmentFileName;
    }

    public void setAttachmentFileName(String attachmentFileName) {
        AttachmentFileName = attachmentFileName;
    }

    public Boolean getAppendMode() {
        return AppendMode;
    }

    public void setAppendMode(Boolean appendMode) {
        AppendMode = appendMode;
    }

    /**
     * Daima non-null bir {@link PadesSignatureLevel} döner; field initializer ve
     * setter null-guard birlikte "asla null" kontratını korur.
     */
    public PadesSignatureLevel getSignatureLevel() {
        return SignatureLevel;
    }

    @Schema(
        enumAsRef = true,
        description = "PAdES imza profili. Boş bırakılırsa PADES_BES uygulanır (TSA çağrılmaz). "
                + "PADES_T (signature timestamp), PADES_LT (uzun dönem doğrulama verisi) veya "
                + "PADES_A (archive timestamp) istenirse TSA gerekir; TSA yapılandırılmamışsa "
                + "istek TIMESTAMP_ERROR ile reddedilir.",
        example = "PADES_BES",
        defaultValue = "PADES_BES"
    )
    public void setSignatureLevel(PadesSignatureLevel signatureLevel) {
        SignatureLevel = signatureLevel != null ? signatureLevel : PadesSignatureLevel.PADES_BES;
    }
}
