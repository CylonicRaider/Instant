package net.instant.tools.console_client.cli;

import java.io.Closeable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.management.MBeanServerConnection;
import net.instant.tools.console_client.jmx.ConsoleProxy;

public class SynchronousClient implements Closeable,
        ConsoleProxy.OutputListener {

    public static class OutputBlock {

        private final long sequence;
        private final String data;

        public OutputBlock(long sequence, String data) {
            this.sequence = sequence;
            this.data = data;
        }
        public OutputBlock(ConsoleProxy.OutputEvent evt) {
            this(evt.getSequence(), evt.getData());
        }

        public long getSequence() {
            return sequence;
        }

        public String getData() {
            return data;
        }

    }

    private final ConsoleProxy proxy;
    private final boolean closeProxy;
    private final BlockingQueue<OutputBlock> bufferedOutput;
    private long lastCommand;
    private long lastOutput;
    private boolean closed;

    public SynchronousClient(ConsoleProxy proxy, boolean closeProxy) {
        this.proxy = proxy;
        this.closeProxy = closeProxy;
        this.bufferedOutput = new LinkedBlockingQueue<OutputBlock>();
        this.lastCommand = -1;
        this.lastOutput = -1;
        this.closed = false;
        proxy.addOutputListener(this);
    }

    public void outputReceived(ConsoleProxy.OutputEvent evt) {
        try {
            bufferedOutput.put(new OutputBlock(evt));
        } catch (InterruptedException exc) {
            // Should not happen.
            throw new RuntimeException(exc);
        }
    }

    public void submitCommand(String command) {
        synchronized (bufferedOutput) {
            ensureNotClosed();
            lastCommand = proxy.submitCommand(command);
        }
    }

    public String readOutputBlock() throws InterruptedException {
        synchronized (bufferedOutput) {
            ensureNotClosed();
            if (lastOutput >= lastCommand) return null;
            OutputBlock blk = bufferedOutput.take();
            lastOutput = blk.getSequence();
            return blk.getData();
        }
    }

    public void close() {
        synchronized (bufferedOutput) {
            proxy.removeOutputListener(this);
            if (closeProxy) proxy.close();
            closed = true;
        }
    }

    private void ensureNotClosed() {
        if (closed)
            throw new IllegalStateException(
                "Accessing closed SynchronousClient");
    }

    public static SynchronousClient getNewDefault(
            MBeanServerConnection conn) {
        return new SynchronousClient(ConsoleProxy.getNewDefault(conn), true);
    }

}
