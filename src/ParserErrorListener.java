import org.antlr.v4.runtime.*;

import java.util.BitSet;

import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;

public class ParserErrorListener extends BaseErrorListener {
        class OutputErrHelper {
                private int lastLine = -1;

                public void PrintHelper(int line, String msg) {
                        if (lastLine == line ) {
                                return;
                        }
                        System.out.println("Error type B at line " + line + ": " + msg);
                        lastLine = line;
                }

                public boolean HasError() {
                        return lastLine != -1;
                }
        }

        private OutputErrHelper put =  new OutputErrHelper();
        private boolean haserr = false;

        public ParserErrorListener() {
        }

        public boolean hasErr() {
                return haserr;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
                        String msg, RecognitionException e) {
                put.PrintHelper(line, msg);
                haserr = true;
        }

        @Override
        public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact,
                        BitSet ambigAlts, ATNConfigSet configs) {
        }

        @Override
        public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex,
                        BitSet conflictingAlts, ATNConfigSet configs) {
        }

        @Override
        public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction,
                        ATNConfigSet configs) {
        }

}