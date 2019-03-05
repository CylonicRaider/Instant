package net.instant.tools.console_client.cli;

public interface Terminal {

    String readLine(String prompt);

    /* Since the value ultimately goes into JMX as a String, we avoid the
     * mostly futile effort of maintaining character arrays. */
    String readPassword(String prompt);

    void write(String text);

}
