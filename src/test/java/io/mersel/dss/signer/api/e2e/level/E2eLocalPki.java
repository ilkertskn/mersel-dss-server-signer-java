package io.mersel.dss.signer.api.e2e.level;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.spi.x509.revocation.crl.ExternalResourcesCRLSource;
import eu.europa.esig.dss.spi.x509.tsp.KeyEntityTSPSource;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import io.mersel.dss.signer.api.models.SigningMaterial;
import io.mersel.dss.signer.api.services.timestamp.TimestampConfigurationService;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;

/**
 * İmza-seviyesi (T/LT/A) roundtrip testleri için <b>tamamen yerel, ağsız</b>
 * bir PKI + zaman damgası otoritesi (TSA) kurar.
 *
 * <h3>Neden gerekli?</h3>
 * <p>T/LT/A profilleri gerçek bir RFC 3161 TSA ve imzalama anında erişilebilir
 * iptal verisi (CRL/OCSP) gerektirir. Üretim test sertifikaları KamuSM'e
 * zincirlenir; bunların TSA'sı ve OCSP/CRL responder'ı CI ortamında ağ olmadan
 * erişilemez. Dolayısıyla "gerçek TSA varsa koşar, yoksa atlar" şeklindeki gated
 * testler CI'da hiç çalışmaz ve güvenlik kanıtı üretmez.</p>
 *
 * <p>Bu helper, BouncyCastle ile <em>kendi kontrolümüzde</em> bir kök CA, bir
 * imza sertifikası, bir TSA sertifikası ve kök tarafından imzalanmış bir CRL
 * üretir. {@link KeyEntityTSPSource} ile yerel ve deterministik RFC 3161 zaman
 * damgası üretilir. Sonuçta T/LT/A imzaları gerçekten oluşturulur ve DSS'in
 * kendi {@link SignedDocumentValidator}'ı ile <b>tam güven zinciri</b> altında
 * doğrulanır — hiçbir güvenlik kontrolü atlanmaz, indication {@code TOTAL_PASSED}
 * beklenir.</p>
 *
 * <p>Tüm anahtar/sertifika üretimi tek seferlik ve immutable'dır; thread-safe
 * paylaşılabilir.</p>
 */
public final class E2eLocalPki {

    private final KeyPair rootKeyPair;
    private final X500Name rootName;
    private final X509Certificate rootCert;

    private final PrivateKey signingKey;
    private final X509Certificate signingCert;

    private final PrivateKey tsaKey;
    private final X509Certificate tsaCert;

    private final byte[] crlDer;

    public E2eLocalPki() {
        try {
            // 1) Kök CA — DN nesnesi TEK kaynak: root subject, EE issuer ve CRL
            // issuer aynı X500Name örneğini kullanır. String'den yeniden parse
            // edilirse DN byte-encoding'i değişebilir; bu da hem zincir kurulumunu
            // hem CRL eşleşmesini bozar (issuer/subject byte-match gerektirir).
            this.rootKeyPair = generateRsaKeyPair();
            this.rootName = new X500Name("CN=Mersel E2E Local Root CA, O=Mersel, C=TR");
            this.rootCert = buildRootCertificate(rootName, rootKeyPair);

            // 2) İmza sertifikası (digitalSignature + nonRepudiation)
            KeyPair signingKp = generateRsaKeyPair();
            this.signingKey = signingKp.getPrivate();
            this.signingCert = buildEndEntityCertificate(
                    new X500Name("CN=Mersel E2E Signer, O=Mersel, C=TR"),
                    signingKp.getPublic(),
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation),
                    /*timeStamping*/ false,
                    BigInteger.valueOf(1001));

            // 3) TSA sertifikası (kritik id-kp-timeStamping EKU — RFC 3161 / BC zorunlu)
            KeyPair tsaKp = generateRsaKeyPair();
            this.tsaKey = tsaKp.getPrivate();
            this.tsaCert = buildEndEntityCertificate(
                    new X500Name("CN=Mersel E2E TSA, O=Mersel, C=TR"),
                    tsaKp.getPublic(),
                    new KeyUsage(KeyUsage.digitalSignature),
                    /*timeStamping*/ true,
                    BigInteger.valueOf(1002));

            // 4) Kök tarafından imzalanmış CRL (hiçbir EE sertifika revoke değil)
            this.crlDer = buildCrl();

        } catch (Exception e) {
            throw new IllegalStateException("Yerel E2E PKI kurulamadı", e);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  İmza katmanına beslenen materyaller
    // ════════════════════════════════════════════════════════════════════

    /** İmza servislerine verilecek {@link SigningMaterial} (imza EE + kök zincir). */
    public SigningMaterial signingMaterial() {
        return new SigningMaterial(signingKey, signingCert, Arrays.asList(signingCert, rootCert));
    }

    /** Yerel, ağsız RFC 3161 TSA. Her çağrıda taze instance döner (idempotent). */
    public TSPSource tspSource() {
        KeyEntityTSPSource tsp = new KeyEntityTSPSource(
                tsaKey, tsaCert, Arrays.asList(tsaCert, rootCert));
        tsp.setTsaPolicy("1.3.6.1.4.1.99999.1.1");
        tsp.setAcceptedDigestAlgorithms(EnumSet.of(
                DigestAlgorithm.SHA1, DigestAlgorithm.SHA224, DigestAlgorithm.SHA256,
                DigestAlgorithm.SHA384, DigestAlgorithm.SHA512));
        tsp.setProductionTime(new Date());
        return tsp;
    }

    /**
     * İmzalama servislerine geçilecek {@link TimestampConfigurationService} —
     * her zaman "available" döner ve yerel TSA'yı sağlar. Üretim sınıfının
     * davranışını bozmadan, sadece bu iki uç noktayı override eder.
     */
    public TimestampConfigurationService timestampConfigurationService() {
        final TSPSource tsp = tspSource();
        return new TimestampConfigurationService("", "", "", false) {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public TSPSource getTspSource() {
                return tsp;
            }
        };
    }

    // ════════════════════════════════════════════════════════════════════
    //  Doğrulama katmanı — tam güven zinciri
    // ════════════════════════════════════════════════════════════════════

    /**
     * Hem imzalama (seviye yükseltme) hem de doğrulama için kullanılacak
     * {@link CertificateVerifier}. Kök CA güvenilir; iptal verisi yerel CRL'den
     * sağlanır. Online kaynak yok — tamamen offline.
     *
     * <p>Her çağrıda taze instance döner; DSS validator'ları context state
     * mutasyonu yaptığı için paylaşmıyoruz.</p>
     */
    public CertificateVerifier newCertificateVerifier() {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();

        CommonTrustedCertificateSource trusted = new CommonTrustedCertificateSource();
        trusted.addCertificate(new CertificateToken(rootCert));
        verifier.setTrustedCertSources(trusted);

        verifier.setCrlSource(new ExternalResourcesCRLSource(
                new InMemoryDocument(crlDer, "local-root.crl")));
        // Online OCSP/AIA bilinçli olarak yok — testler ağa çıkmamalı.
        verifier.setOcspSource(null);
        verifier.setAIASource(null);
        verifier.setCheckRevocationForUntrustedChains(true);
        return verifier;
    }

    /** İmzalı belge için tam-güvenli bir DSS validator döner. */
    public SignedDocumentValidator validatorFor(DSSDocument signedDocument) {
        SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(signedDocument);
        validator.setCertificateVerifier(newCertificateVerifier());
        return validator;
    }

    public X509Certificate rootCertificate() {
        return rootCert;
    }

    public X509Certificate signingCertificate() {
        return signingCert;
    }

    // ════════════════════════════════════════════════════════════════════
    //  BouncyCastle üretim yardımcıları
    // ════════════════════════════════════════════════════════════════════

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    private static X509Certificate buildRootCertificate(X500Name dn, KeyPair keyPair)
            throws Exception {
        Date notBefore = new Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
        Date notAfter = new Date(System.currentTimeMillis() + 3650L * 24 * 60 * 60 * 1000);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                dn, BigInteger.ONE, notBefore, notAfter, dn, keyPair.getPublic());

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private X509Certificate buildEndEntityCertificate(X500Name subjectDn,
                                                      PublicKey subjectPublicKey,
                                                      KeyUsage keyUsage,
                                                      boolean timeStamping,
                                                      BigInteger serial) throws Exception {
        Date notBefore = new Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
        Date notAfter = new Date(System.currentTimeMillis() + 730L * 24 * 60 * 60 * 1000);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                rootName, serial, notBefore, notAfter, subjectDn, subjectPublicKey);

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, keyUsage);
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(subjectPublicKey));
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                extUtils.createAuthorityKeyIdentifier(rootKeyPair.getPublic()));

        if (timeStamping) {
            // RFC 3161 / BouncyCastle: TSA sertifikası YALNIZ id-kp-timeStamping
            // EKU içermeli ve bu kritik olmalı.
            builder.addExtension(Extension.extendedKeyUsage, true,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(rootKeyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private byte[] buildCrl() throws Exception {
        Date thisUpdate = new Date(System.currentTimeMillis() - 60L * 60 * 1000);
        Date nextUpdate = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);

        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(rootName, thisUpdate);
        crlBuilder.setNextUpdate(nextUpdate);

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        crlBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                extUtils.createAuthorityKeyIdentifier(rootKeyPair.getPublic()));
        crlBuilder.addExtension(Extension.cRLNumber, false, new CRLNumber(BigInteger.ONE));
        // Hiçbir sertifika revoke edilmedi: boş ama geçerli, imzalı bir CRL.

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(rootKeyPair.getPrivate());
        X509CRLHolder holder = crlBuilder.build(signer);
        X509CRL crl = new JcaX509CRLConverter().getCRL(holder);
        return crl.getEncoded();
    }
}
