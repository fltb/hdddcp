include Makefile.git

export CLASSPATH=/home/float/.local/bin/antlr-*-complete.jar

DOMAINNAME = oj.compilers.cpl.icu
ANTLR = java -jar /home/float/.local/bin/antlr-*-complete.jar -listener -visitor -long-messages
JAVAC = javac -encoding UTF-8 -g
JAVA = java -encoding UTF-8


PFILE = $(shell find . -name "SysYParser.g4")
LFILE = $(shell find . -name "SysYLexer.g4")
JAVAFILE = $(shell find . -maxdepth 2 -name "*.java")
ANTLRPATH = $(shell find /home/float/.local/bin -name "antlr-*-complete.jar")
FPATH ?= ./tests/test1.sysy

compile: antlr
#	$(call git_commit,"make")
	mkdir -p classes
	$(JAVAC) -classpath $(ANTLRPATH) $(JAVAFILE) -d classes

run: compile
	java -classpath ./classes:$(ANTLRPATH) Main $(FPATH)


antlr: $(LFILE) $(PFILE) 
	$(ANTLR) $(PFILE) $(LFILE)


test: compile
#	$(call git_commit, "test")
	nohup java -classpath ./classes:$(ANTLRPATH) Main $(FPATH) &


clean:
	rm -f src/*.tokens
	rm -f src/*.interp
	rm -f src/SysYLexer.java src/SysYParser.java src/SysYParserBaseListener.java src/SysYParserBaseVisitor.java src/SysYParserListener.java src/SysYParserVisitor.java
	rm -rf classes
	rm -rf out
	rm -rf src/.antlr

.PHONY: compile antlr test run clean submit


