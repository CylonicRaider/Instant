package net.instant.ws.ssl;

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

// While there are lots of things that one could decode from a PEM object, we
// concentrate on the things we need for SSL configuration: Certificates and
// (private) keys.
public class PEMDecoder {

    private CertificateFactory certFactory;
    private KeyFactory keyFactory;

    protected String getDefaultCertificateFactoryAlgorithm() {
        return "X.509";
    }
    protected String getDefaultKeyFactoryAlgorithm() {
        return "RSA";
    }

    protected CertificateFactory createDefaultCertificateFactory()
            throws CertificateException {
        return CertificateFactory.getInstance(
            getDefaultCertificateFactoryAlgorithm());
    }
    public CertificateFactory getCertificateFactory() {
        return certFactory;
    }
    public void setCertificateFactory(CertificateFactory cf) {
        certFactory = cf;
    }

    public Certificate decodeCertificate(PEMObject obj)
            throws CertificateException {
        if (certFactory == null)
            certFactory = createDefaultCertificateFactory();
        ByteArrayInputStream is = new ByteArrayInputStream(
            obj.getDataBytes());
        try {
            return certFactory.generateCertificate(is);
        } finally {
            if (is.available() != 0)
                throw new CertificateException(
                    "Certificate contains trailing padding");
        }
    }

    protected KeyFactory createDefaultKeyFactory()
            throws NoSuchAlgorithmException {
        return KeyFactory.getInstance(getDefaultKeyFactoryAlgorithm());
    }
    public KeyFactory getKeyFactory() {
        return keyFactory;
    }
    public void setKeyFactory(KeyFactory kf) {
        keyFactory = kf;
    }

    public PublicKey decodePublicKey(PEMObject obj)
            throws GeneralSecurityException {
        if (keyFactory == null)
            keyFactory = createDefaultKeyFactory();
        return keyFactory.generatePublic(new X509EncodedKeySpec(
            obj.getDataBytes()));
    }
    public PrivateKey decodePrivateKey(PEMObject obj)
            throws GeneralSecurityException {
        if (keyFactory == null)
            keyFactory = createDefaultKeyFactory();
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(
            obj.getDataBytes()));
    }

}
