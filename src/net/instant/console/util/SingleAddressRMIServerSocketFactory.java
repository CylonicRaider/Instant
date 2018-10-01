package net.instant.console.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;

public class SingleAddressRMIServerSocketFactory
        implements RMIServerSocketFactory {

    public static class AddressMismatchException extends IOException {

        public AddressMismatchException() {
            super();
        }
        public AddressMismatchException(String message) {
            super(message);
        }
        public AddressMismatchException(Throwable cause) {
            super(cause);
        }
        public AddressMismatchException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    private final InetSocketAddress addr;

    public SingleAddressRMIServerSocketFactory(InetSocketAddress addr) {
        if (addr == null) throw new NullPointerException();
        this.addr = addr;
    }

    public InetSocketAddress getAddress() {
        return addr;
    }

    public boolean equals(Object other) {
        return ((other instanceof SingleAddressRMIServerSocketFactory) &&
            addr.equals(((SingleAddressRMIServerSocketFactory) other).addr));
    }

    public int hashCode() {
        // instant console single address server socket factory
        return addr.hashCode() ^ 0x1C5A55F;
    }

    public ServerSocket createServerSocket(int port) throws IOException {
        if (port != addr.getPort())
            throw new AddressMismatchException("RMI implementation tried " +
                "to create a socket on the wrong port");
        return new ServerSocket(addr.getPort(), -1, addr.getAddress());
    }

}
