package net.instant.ws.ssl;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class SSLContextBuilder {

    protected enum KeyStoreRole { KEY_STORE, TRUST_STORE }

    private static final char[] PASSWORD = {};

    private KeyStore keyStore;
    private KeyStore trustStore;

    protected String deriveCertificateAlias(Certificate cert) {
        if (! (cert instanceof X509Certificate))
            throw new UnsupportedOperationException(
                "Cannot generate alias for non-X.509 certificate " + cert);
        return ((X509Certificate) cert).getSubjectX500Principal().getName();
    }
    protected String getContextAlgorithm() {
        return "TLS";
    }

    protected KeyStore createDefaultKeyStore(KeyStoreRole role)
            throws KeyStoreException {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try {
            ks.load(null, null);
        } catch (IOException exc) {
            // Why would initializing a keystore to be empty involve I/O?
            throw new KeyStoreException(
                "I/O error while creating empty keystore?!", exc);
        } catch (GeneralSecurityException exc) {
            // Why would a default keystore use an undefined algoritm... okay,
            // that's not entirely unanticipable.
            // However, I do not see any of the nonexistent certificates
            // inside the keystore failing to load.
            throw new KeyStoreException(
                "Error while creating empty keystore?!", exc);
        }
        return ks;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }
    public void setKeyStore(KeyStore ks) {
        keyStore = ks;
    }

    public void addCertificate(Certificate[] chain, Key key)
            throws KeyStoreException {
        if (chain == null || chain.length == 0)
            throw new IllegalArgumentException(
                "At least one certificate required");
        if (keyStore == null)
            keyStore = createDefaultKeyStore(KeyStoreRole.KEY_STORE);
        keyStore.setKeyEntry(deriveCertificateAlias(chain[0]), key, PASSWORD,
                             chain);
    }

    public KeyStore getTrustStore() {
        return trustStore;
    }
    public void setTrustStore(KeyStore ts) {
        trustStore = ts;
    }

    public void addTrustedCertificate(Certificate cert)
            throws KeyStoreException {
        if (trustStore == null)
            trustStore = createDefaultKeyStore(KeyStoreRole.TRUST_STORE);
        trustStore.setCertificateEntry(deriveCertificateAlias(cert), cert);
    }

    protected KeyManager[] createKeyManagers()
            throws GeneralSecurityException {
        if (keyStore == null) return null;
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(getKeyStore(), PASSWORD);
        return kmf.getKeyManagers();
    }
    protected TrustManager[] createTrustManagers()
            throws GeneralSecurityException {
        if (trustStore == null) return null;
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(getTrustStore());
        return tmf.getTrustManagers();
    }

    public SSLContext build() throws GeneralSecurityException {
        SSLContext ctx = SSLContext.getInstance(getContextAlgorithm());
        ctx.init(createKeyManagers(), createTrustManagers(), null);
        return ctx;
    }

    public void reset() {
        keyStore = null;
        trustStore = null;
    }

}
