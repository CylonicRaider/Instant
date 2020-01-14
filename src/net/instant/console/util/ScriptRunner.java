package net.instant.console.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
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
    public void setVariables(Map<String, Object> vars) {
        ScriptContext ctx = engine.getContext();
        ctx.getBindings(ScriptContext.ENGINE_SCOPE).putAll(vars);
    }

    public void redirectOutput(Writer output, Writer error) {
        engine.getContext().setWriter(output);
        engine.getContext().setErrorWriter(error);
    }
    public void redirectOutput(Writer output) {
        redirectOutput(output, output);
    }

    private void printTo(Writer wr, String str) {
        try {
            wr.write(str);
            wr.flush();
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }
    }

    public void print(String str) {
        printTo(engine.getContext().getWriter(), str);
    }
    public void prinln(String str) {
        print(str + "\n");
    }

    public void printError(String str) {
        printTo(engine.getContext().getErrorWriter(), str);
    }
    public void printlnError(String str) {
        printError(str + "\n");
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
    public Object executeAndPrint(String script) {
        try {
            Object ret = execute(script);
            print(formatObjectLine(ret));
            return ret;
        } catch (ScriptException exc) {
            printlnError(String.valueOf(exc));
            return exc;
        }
    }

    public Object executeFile(File path) throws ScriptException {
        try {
            Reader rd = new FileReader(path);
            Object oldFilename = engine.get(ScriptEngine.FILENAME);
            try {
                engine.put(ScriptEngine.FILENAME, path.toString());
                return engine.eval(rd);
            } finally {
                engine.put(ScriptEngine.FILENAME, oldFilename);
                rd.close();
            }
        } catch (IOException exc) {
            throw new ScriptException(exc);
        }
    }

    public static ScriptEngineManager getEngineManager() {
        return engineManager;
    }

    public static String formatObjectLine(Object input) {
        return (input == null) ? "" : input + "\n";
    }

    public static Object executeSingleAndPrint(String script) {
        return new ScriptRunner().executeAndPrint(script);
    }

}
