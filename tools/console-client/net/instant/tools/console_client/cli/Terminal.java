package net.instant.tools.console_client.cli;

public interface Terminal {

    String readLine(String prompt);

    void write(String text);

}
