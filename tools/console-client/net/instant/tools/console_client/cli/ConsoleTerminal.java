package net.instant.tools.console_client.cli;

import java.io.Console;

public class ConsoleTerminal implements Terminal {

    private final Console console;

    public ConsoleTerminal(Console console) {
        this.console = console;
    }

    public Console getConsole() {
        return console;
    }

    public String readLine(String prompt) {
        return console.readLine("%s", prompt);
    }

    public String readPassword(String prompt) {
        return new String(console.readPassword("%s", prompt));
    }

    public void write(String text) {
        console.printf("%s", text);
    }

    public static ConsoleTerminal getDefault() {
        Console con = System.console();
        if (con == null) return null;
        return new ConsoleTerminal(con);
    }

}
