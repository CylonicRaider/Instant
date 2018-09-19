package net.instant.console;

import java.io.IOException;
import java.io.Writer;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class ScriptRunner {

    /* We remind the reader that Java and JavaScript are *not* related... */
    public static final String DEFAULT_LANGUAGE = "JavaScript";

    private static final ScriptEngineManager engineManager =
        new ScriptEngineManager();

    private static ScriptEngine engine;

    public ScriptRunner(String language) {
        engine = engineManager.getEngineByName(language);
    }
    public ScriptRunner() {
        this(DEFAULT_LANGUAGE);
    }

    public ScriptEngine getEngine() {
        return engine;
    }

    public Object getVariable(String name) {
        return engine.getContext().getAttribute(name);
    }
    public void setVariable(String name, Object value) {
        engine.getContext().setAttribute(name, value,
                                         ScriptContext.ENGINE_SCOPE);
    }

    public void redirectOutput(Writer output, Writer error) {
        engine.getContext().setWriter(output);
        engine.getContext().setErrorWriter(error);
    }
    public void redirectOutput(Writer output) {
        redirectOutput(output, output);
    }

    public void print(String str) {
        try {
            engine.getContext().getWriter().write(str);
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
    }
    public void prinln(String str) {
        print(str + "\n");
    }

    public Object execute(String script) throws ScriptException {
        return engine.eval(script);
    }
    public Object executeSafe(String script) {
        try {
            return execute(script);
        } catch (ScriptException exc) {
            return exc;
        }
    }

    public static ScriptEngineManager getEngineManager() {
        return engineManager;
    }

}
