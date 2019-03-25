package net.instant.util.argparse;

public class ParsingException extends Exception {

    private final String source;

    public ParsingException(String message) {
        super(message);
        this.source = null;
    }
    public ParsingException(String message, String source) {
        super(message);
        this.source = source;
    }
    public ParsingException(String message, Throwable cause) {
        super(message, cause);
        this.source = null;
    }
    public ParsingException(String message, String source, Throwable cause) {
        super(message, cause);
        this.source = source;
    }
    public ParsingException(ParsingException cause, String newSource) {
        super(cause.getOriginalMessage(), cause);
        this.source = newSource;
    }

    protected String getOriginalMessage() {
        return super.getMessage();
    }

    public String getMessage() {
        String ret = getOriginalMessage();
        if (ret == null) return null;
        String src = getSource();
        if (src != null) ret += " " + src;
        return ret;
    }

    public String getSource() {
        return source;
    }

}
