package net.instant.ws.ssl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SSLConfiguration {

    public static class ConfigurationException extends Exception {

        public ConfigurationException() {
            super();
        }
        public ConfigurationException(String message) {
            super(message);
        }
        public ConfigurationException(Throwable cause) {
            super(cause);
        }
        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    private interface Sink<T> {

        void consume(T item) throws Exception;

    }

    protected PEMReader createReader(Reader base) {
        return new PEMReader(base);
    }
    protected PEMReader createReader(File path) throws FileNotFoundException {
        return new PEMReader(new FileReader(path));
    }
    protected PEMDecoder createDecoder() {
        return new PEMDecoder();
    }
    protected SSLContextBuilder createBuilder() {
        return new SSLContextBuilder();
    }

    private void consumeFile(File path, Sink<PEMObject> sink)
            throws ConfigurationException {
        PEMReader reader = null;
        try {
            reader = createReader(path);
            for (;;) {
                PEMObject item = reader.readPEMObject();
                if (item == null) break;
                sink.consume(item);
            }
        } catch (ConfigurationException exc) {
            throw exc;
        } catch (RuntimeException exc) {
            throw exc;
        } catch (Exception exc) {
            throw new ConfigurationException(exc);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException exc) {
                    throw new ConfigurationException(exc);
                }
            }
        }
    }

    public SSLEngineFactory doConfiguration(final File cert, final File key,
                                            final File ca)
            throws ConfigurationException {
        if (cert == null && key != null)
            throw new IllegalArgumentException(
                "Private key may not be null if certificate is null");
        final PEMDecoder decoder = createDecoder();
        final SSLContextBuilder builder = createBuilder();
        try {
            if (cert != null) {
                final List<Certificate> chain = new ArrayList<Certificate>();
                final PrivateKey[] keyBox = { null };
                consumeFile(cert, new Sink<PEMObject>() {
                    public void consume(PEMObject item) throws Exception {
                        if (item.getLabel().equals("CERTIFICATE")) {
                            chain.add(decoder.decodeCertificate(item));
                        } else if (item.getLabel().equals("PRIVATE KEY")) {
                            if (key != null)
                                throw new ConfigurationException(
                                    "Both in-certificate and explicit " +
                                    "private key provided");
                            if (keyBox[0] != null)
                                throw new ConfigurationException(
                                    "Multiple in-certificate private keys " +
                                    "provided");
                                keyBox[0] = decoder.decodePrivateKey(item);
                        } else {
                            throw new ConfigurationException(
                                "Unrecognized certificate file object " +
                                item.getLabel());
                        }
                    }
                });
                if (key != null) {
                    consumeFile(key, new Sink<PEMObject>() {
                        public void consume(PEMObject item) throws Exception {
                            if (! item.getLabel().equals("PRIVATE KEY"))
                                throw new ConfigurationException(
                                    "Unexpected private key file object " +
                                    item.getLabel());
                            keyBox[0] = decoder.decodePrivateKey(item);
                        }
                    });
                }
                if (chain.size() == 0)
                    throw new ConfigurationException(
                        "No certificate specified");
                if (keyBox[0] == null)
                    throw new ConfigurationException(
                        "No private key specified");
                builder.addCertificate(
                    chain.toArray(new Certificate[chain.size()]),
                    keyBox[0]);
            }
            if (ca != null) {
                final List<Certificate> cas = new ArrayList<Certificate>();
                consumeFile(ca, new Sink<PEMObject>() {
                    public void consume(PEMObject item) throws Exception {
                        if (! item.getLabel().equals("CERTIFICATE"))
                            throw new ConfigurationException(
                                "Unexpected CA certificate file object " +
                                item.getLabel());
                        cas.add(decoder.decodeCertificate(item));
                    }
                });
                for (Certificate caCert : cas) {
                    builder.addTrustedCertificate(caCert);
                }
            }
            return builder.buildEngineFactory();
        } catch (GeneralSecurityException exc) {
            throw new ConfigurationException(exc);
        }
    }

    public SSLEngineFactory doConfiguration(Map<String, String> data)
            throws ConfigurationException {
        return doConfiguration(fileOrNull(data.get("cert")),
                               fileOrNull(data.get("key")),
                               fileOrNull(data.get("ca")));
    }

    public static SSLEngineFactory configure(File cert, File key, File ca)
            throws ConfigurationException {
        return new SSLConfiguration().doConfiguration(cert, key, ca);
    }

    public static SSLEngineFactory configure(Map<String, String> data)
            throws ConfigurationException {
        return new SSLConfiguration().doConfiguration(data);
    }

    private static File fileOrNull(String str) {
        return (str == null) ? null : new File(str);
    }

}
