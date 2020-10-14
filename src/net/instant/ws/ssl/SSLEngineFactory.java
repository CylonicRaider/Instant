package net.instant.ws.ssl;

import javax.net.ssl.SSLEngine;

// Ironically, SSLContext cannot be configured to spew out fully configured
// SSLEngines, so we provide an own interface for that.
public interface SSLEngineFactory {

    SSLEngine createSSLEngine(boolean clientMode);

}
