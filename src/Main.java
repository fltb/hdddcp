import java.io.IOException;

import org.antlr.v4.runtime.*;

import java.util.List;

public class Main {
    private static void printSysYTokenInformation(Token t) {
        var typeName = SysYLexer.ruleNames[t.getType() - 1];
        if (typeName.matches("^(WS|MULTILINE_COMMENT|LINE_COMMENT)$")) {
            return;
        }
        System.err.println(typeName + " " + t.getText() + " at Line " + t.getLine() +
                ".");
    }

    private static boolean task4_2(String source) throws IOException {
        System.out.println("Task 4.2 lexer begin::");
        SysYLexer sysYLexer = new SysYLexer(CharStreams.fromFileName(source));
        LexerErrorListener myErrorListener = new LexerErrorListener();
        sysYLexer.removeErrorListeners();
        sysYLexer.addErrorListener(myErrorListener);

        List<? extends Token> myTokens = sysYLexer.getAllTokens();

        if (myErrorListener.hasErr()) {
            System.out.println("Task 4.2 succ caught lexer err end::");
            return false;
        }
        for (var t : myTokens) {
            printSysYTokenInformation(t);
        }
        System.out.println("Task 4.2 succ no lexer err end::");
        return true;
    }

    private static boolean task4_3(String source) throws IOException {
        System.out.println("Task 4.3 parser begin::");
        SysYLexer sysYLexer = new SysYLexer(CharStreams.fromFileName(source));
        SysYParser sysYParser = new SysYParser(new CommonTokenStream(sysYLexer));
        ParserErrorListener myErrorListener = new ParserErrorListener();
        sysYParser.removeErrorListeners();
        sysYParser.addErrorListener(myErrorListener);

        SysYParser.ProgramContext tree = sysYParser.program();

        if (myErrorListener.hasErr()) {
            System.out.println("Task 4.3 succ caught parser err end::");
            return false;
        }
        ParserUnitVisitor visitor = new ParserUnitVisitor();
        visitor.visit(tree);

        System.out.println("Task 4.3 succ no parser err end::");
        return true;
    }

    private static void task4_4(String source) throws IOException {
        System.out.println("Task 4.4 gramma check begin::");
        SysYLexer sysYLexer = new SysYLexer(CharStreams.fromFileName(source));
        SysYParser sysYParser = new SysYParser(new CommonTokenStream(sysYLexer));
        LexerErrorListener myErrorListener = new LexerErrorListener();
        sysYParser.removeErrorListeners();
        sysYParser.addErrorListener(myErrorListener);
        SysYParser.ProgramContext tree = sysYParser.program();
        ErrorHandleVisitor visitor = new ErrorHandleVisitor();
        visitor.visit(tree);
        if (visitor.hasError()) {
            System.out.println("Task 4.4 gramma check end::");
        } else {
            System.out.println("Task 4.4 gramma check no error end::");
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
        }
        String source = args[0];
        if (task4_2(source) && task4_3(source)) {
            task4_4(source);
        }

    }
}