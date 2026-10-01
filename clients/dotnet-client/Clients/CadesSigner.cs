using MERSEL.Services.DssSigner.Client.Interfaces;
using MERSEL.Services.DssSigner.Client.Internal;
using MERSEL.Services.DssSigner.Client.Models;
using Microsoft.Extensions.Logging;

namespace MERSEL.Services.DssSigner.Client.Clients;

/// <summary>
/// CAdES-BES imzalama operasyonlarını DSS Signer mikroservisi üzerinden gerçekleştirir.
/// </summary>
internal sealed class CadesSigner : DssSignerHttpBase, ICadesSigner
{
    public CadesSigner(HttpClient httpClient, ILogger<CadesSigner> logger)
        : base(httpClient, logger)
    {
    }

    /// <inheritdoc />
    public Task<SignResult> SignAsync(byte[] document, bool detached = false, CancellationToken ct = default)
        => SignAsync(new SignCadesRequest { Document = document, Detached = detached }, ct);

    /// <inheritdoc />
    public async Task<SignResult> SignAsync(SignCadesRequest request, CancellationToken ct = default)
    {
        if (request is null) throw new ArgumentNullException(nameof(request));
        if (request.Document is null || request.Document.Length == 0)
            throw new ArgumentException("Belge içeriği boş olamaz.", nameof(request));

        Logger.LogDebug(
            "DSS Signer CAdES isteği — boyut: {Boyut} bayt, detached: {Detached}, seviye: {Seviye}",
            request.Document.Length, request.Detached, request.SignatureLevel);

        using var form = new MultipartFormDataContent();
        AddFilePart(form, "document", request.Document, request.FileName);
        AddStringPart(form, "detached", request.Detached ? "true" : "false");
        // signatureLevel asla null değildir (default CADES_BES); enum sabit ismi
        // sunucu Java enum'u ile birebir eşleşir (CADES_BES / CADES_T / CADES_LT / CADES_A).
        AddStringPart(form, "signatureLevel", request.SignatureLevel.ToString());

        using var response = await PostMultipartBinaryAsync(
            "/v1/cadessign", form, ct, request.Headers).ConfigureAwait(false);
        var bytes = await ReadResponseBytesAsync(response, ct).ConfigureAwait(false);

        // x-signature-value header'ı sadece detached modda set edilir.
        return new SignResult
        {
            SignedDocument = bytes,
            SignatureValue = FirstHeaderOrDefault(response, "x-signature-value"),
            SuggestedFileName = ExtractFileName(response),
            ContentType = response.Content.Headers.ContentType?.MediaType
        };
    }
}
