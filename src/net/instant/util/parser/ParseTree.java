package net.instant.util.parser;

import java.util.List;
import net.instant.util.NamedValue;

public interface ParseTree extends NamedValue {

    Lexer.Token getToken();

    String getContent();

    List<ParseTree> getChildren();

    int childCount();

    ParseTree childAt(int index);

}
