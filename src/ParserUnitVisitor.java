import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

public class ParserUnitVisitor extends SysYParserBaseVisitor<Void> {

    private int curPadding = 0;

    private void paddingAdvance() {
        curPadding++;
    }

    private void paddingBack() {
        curPadding--;
    }

    private void printWithPadding(String str) {
        for (int i = 0; i < curPadding; i++) {
            System.out.print("    ");
        }
        System.out.println(str);
    }

    @Override
    public Void visitTerminal(TerminalNode node) {
        var token = node.getSymbol();
        if (token.getType() == SysYLexer.EOF) {
            paddingBack();
            return null;
        }
        var typeName = SysYLexer.ruleNames[token.getType() - 1];
        var text = token.getText();
        if (token.getType() == SysYLexer.INTEGER_CONST) {
            text = String.valueOf(Integer.decode(token.getText()));
        }
        printWithPadding(typeName + " " + text);
        return null;
    }

    @Override
    public Void visitChildren(RuleNode ctx) {
        var className = ctx.getRuleContext().getClass().getSimpleName();
        var name = className.substring(0, className.length() - 7);
        printWithPadding(name + " (" + ctx.getRuleContext().getRuleIndex() + ")");
        paddingAdvance();
        super.visitChildren(ctx);
        paddingBack();
        return null;
    }
}
