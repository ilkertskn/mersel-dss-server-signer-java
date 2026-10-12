using System.Text.Json.Serialization;

namespace MERSEL.Services.DssSigner.Client.Models;

/// <summary>
/// PAdES (PDF) imza profili (seviyesi).
/// </summary>
/// <remarks>
/// <para>
/// <b>Varsayılan</b> — API kontratı gereği <see cref="SignPadesRequest.SignatureLevel"/>
/// alanı <em>null olamaz</em>. Talep oluşturulurken set edilmezse <see cref="PADES_BES"/>
/// uygulanır (TSA çağrılmaz).
/// </para>
/// <para>
/// <b>DSS Eşlemesi</b>:
/// <list type="bullet">
///   <item><c>PADES_BES</c> → <c>SignatureLevel.PAdES_BASELINE_B</c></item>
///   <item><c>PADES_T</c>   → <c>SignatureLevel.PAdES_BASELINE_T</c></item>
///   <item><c>PADES_LT</c>  → <c>SignatureLevel.PAdES_BASELINE_LT</c></item>
///   <item><c>PADES_A</c>   → <c>SignatureLevel.PAdES_BASELINE_LTA</c></item>
/// </list>
/// </para>
/// </remarks>
[JsonConverter(typeof(JsonStringEnumConverter))]
public enum PadesSignatureLevel
{
    /// <summary>PAdES-BES (Baseline-B): yalnızca imza. TSA çağrılmaz. <b>Varsayılan.</b></summary>
    PADES_BES,

    /// <summary>PAdES-T (Baseline-T): signature timestamp eklenir. TSA gerekir.</summary>
    PADES_T,

    /// <summary>PAdES-LT (Baseline-LT): imza + timestamp + uzun dönem doğrulama verisi. TSA gerekir.</summary>
    PADES_LT,

    /// <summary>PAdES-A (Baseline-LTA): imza + timestamp + archive timestamp. TSA gerekir.</summary>
    PADES_A
}
