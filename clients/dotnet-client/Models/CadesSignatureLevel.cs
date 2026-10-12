using System.Text.Json.Serialization;

namespace MERSEL.Services.DssSigner.Client.Models;

/// <summary>
/// CAdES imza profili (seviyesi).
/// </summary>
/// <remarks>
/// <para>
/// <b>Varsayılan</b> — API kontratı gereği <see cref="SignCadesRequest.SignatureLevel"/>
/// alanı <em>null olamaz</em>. Talep oluşturulurken set edilmezse <see cref="CADES_BES"/>
/// uygulanır (TSA çağrılmaz).
/// </para>
/// <para>
/// <b>DSS Eşlemesi</b>:
/// <list type="bullet">
///   <item><c>CADES_BES</c> → <c>SignatureLevel.CAdES_BASELINE_B</c></item>
///   <item><c>CADES_T</c>   → <c>SignatureLevel.CAdES_BASELINE_T</c></item>
///   <item><c>CADES_LT</c>  → <c>SignatureLevel.CAdES_BASELINE_LT</c></item>
///   <item><c>CADES_A</c>   → <c>SignatureLevel.CAdES_BASELINE_LTA</c></item>
/// </list>
/// </para>
/// </remarks>
[JsonConverter(typeof(JsonStringEnumConverter))]
public enum CadesSignatureLevel
{
    /// <summary>CAdES-BES (Baseline-B): yalnızca imza. TSA çağrılmaz. <b>Varsayılan.</b></summary>
    CADES_BES,

    /// <summary>CAdES-T (Baseline-T): signature timestamp eklenir. TSA gerekir.</summary>
    CADES_T,

    /// <summary>CAdES-LT (Baseline-LT): imza + timestamp + uzun dönem doğrulama verisi. TSA gerekir.</summary>
    CADES_LT,

    /// <summary>CAdES-A (Baseline-LTA): imza + timestamp + archive timestamp. TSA gerekir.</summary>
    CADES_A
}
