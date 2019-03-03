package net.instant.tools.console_client.cli;

public class CLI implements Runnable {

    public static final String PROMPT = "> ";

    private final SynchronousClient client;
    private final Terminal term;

    public CLI(SynchronousClient client, Terminal term) {
        this.client = client;
        this.term = term;
    }

    public SynchronousClient getClient() {
        return client;
    }

    public Terminal getTerminal() {
        return term;
    }

    public void run() {
        try {
            for (;;) {
                for (;;) {
                    String block = client.readOutputBlock();
                    if (block == null) break;
                    term.write(block);
                }
                if (client.isAtEOF()) break;
                String command = term.readLine(PROMPT);
                if (command == null) {
                    term.write("\n");
                    break;
                }
                client.submitCommand(command);
            }
        } catch (InterruptedException exc) {
            /* NOP */
        } finally {
            client.close();
        }
    }

}
