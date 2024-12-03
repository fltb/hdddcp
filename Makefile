include Makefile.git

export CLASSPATH=/home/float/.local/lib/nju-compiler/antlr-*-complete.jar

DOMAINNAME = oj.compilers.cpl.icu

LLVM_JAR = $(shell echo `find  ~/.local/lib/nju-compiler -name "llvm-*.jar"` | sed  "s/\s\+/:/g")
JAVACPP_JAR = $(shell echo `find ~/.local/lib/nju-compiler -name "javacpp-*.jar"` | sed  "s/\s\+/:/g")

ANTLR = java -jar ~/.local/lib/nju-compiler/antlr-*-complete.jar -listener -visitor -long-messages
JAVAC = javac -encoding UTF-8 -g
JAVA = java -encoding UTF-8


PFILE = $(shell find . -name "SysYParser.g4")
LFILE = $(shell find . -name "SysYLexer.g4")
JAVAFILE = $(shell find . -maxdepth 2 -name "*.java")

ANTLR_PATH = $(shell find ~/.local/lib/nju-compiler -name "antlr-*-complete.jar")
export CLASSPATH=$(ANTLR_PATH):$(LLVM_JAR):$(JAVACPP_JAR)

FPATH ?= ./tests/test1.sysy

compile: antlr
#	$(call git_commit,"make")
	mkdir -p classes
	$(JAVAC) -classpath $(CLASSPATH) $(JAVAFILE) -d classes

run: compile
	java -classpath ./classes:$(CLASSPATH) Main $(FPATH) $(FPATH).ll

antlr: $(LFILE) $(PFILE) 
	$(ANTLR) $(PFILE) $(LFILE)


test: compile
#	$(call git_commit, "test")
	if [ -e nohup.out ]; then rm nohup.out; fi
	nohup java -classpath ./classes:$(CLASSPATH) Main $(FPATH) $(FPATH).ll &

clean:
	rm -f src/*.tokens
	rm -f src/*.interp
	rm -f src/SysYLexer.java src/SysYParser.java src/SysYParserBaseListener.java src/SysYParserBaseVisitor.java src/SysYParserListener.java src/SysYParserVisitor.java
	rm -rf classes
	rm -rf out
	rm -rf src/.antlr

.PHONY: compile antlr test run clean submit


