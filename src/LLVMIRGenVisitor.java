import java.io.IOException;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.bytedeco.llvm.LLVM.*;
import static org.bytedeco.llvm.global.LLVM.*;

import org.bytedeco.javacpp.*;


public class LLVMIRGenVisitor extends SysYParserBaseVisitor<LLVMValueRef> {
    class Symbol {
        private LLVMTypeRef type;
        private String id;
        // stores memref contain value, not value itself
        private LLVMValueRef valueMem;
    
        public Symbol(LLVMTypeRef t, String i) {
            this.type = t;
            this.id = i;
            this.valueMem = null;
        }
    
        public Symbol(LLVMTypeRef t, String i, LLVMValueRef v) {
            this.type = t;
            this.id = i;
            this.valueMem = v;
        }
    
        public String getId() {
            return id;
        }
    
        public LLVMTypeRef getType() {
            return type;
        }
    
        public LLVMValueRef getValueMem() {
            return valueMem;
        }
    
        public void setValueMem(LLVMValueRef valueMem) {
            this.valueMem = valueMem;
        }
    };
    
    class Scope {
        private Scope parentScope = null;
        private HashMap<String, Symbol> mp;
        private String name;
    
        private Symbol getSymbolGlobal(Scope scope, String id) {
            if (scope == null) {
                return null;
            } else {
                return scope.getSymbol(id) != null ? scope.getSymbol(id) : getSymbolGlobal(scope.getParentScope(), id);
            }
        }
    
        public Scope(String name) {
            mp = new HashMap<String, Symbol>();
            this.name = name;
        }
    
        public Scope getParentScope() {
            return parentScope;
        }
    
        public void setParentScope(Scope parentScope) {
            this.parentScope = parentScope;
        }
    
        public Symbol getSymbol(String id) {
            return mp.get(id);
        }
    
        public Symbol getSymbolGlobal(String id) {
            return getSymbolGlobal(this, id);
        }
    
        public void setSymbol(String id, Symbol symbol) {
            if (getSymbol(id) != null) {
                throw new Error("Detected duplicate symbol in scope");
            }
            mp.put(id, symbol);
        }
    
        public String getName() {
            return name;
        }
    }
    
    class BasicBlock {
        private LLVMBasicBlockRef block;
        private boolean used = false;
    
        BasicBlock(LLVMBasicBlockRef b) {
            block = b;
        }
    
        public LLVMBasicBlockRef getBlock() {
            return block;
        }
    
        public void setUsed(boolean used) {
            this.used = used;
        }
    
        public boolean getUsed() {
            return used;
        }
    }
    
    private final String GLOBAL_NAME = "global";
    private String filename;
    private LLVMModuleRef module = LLVMModuleCreateWithName("module"); // make module
    private LLVMBuilderRef builder = LLVMCreateBuilder(); // LLVM IR Builder for usage
    private LLVMTypeRef i32Type = LLVMInt32Type(); // since our language only have int type, store it for usage;
    private LLVMTypeRef voidType = LLVMVoidType();
    private LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);

    private LLVMValueRef curFunc;
    // private LLVMBasicBlockRef curBlock;

    private int curVRegCounter = 0, curVBlockCounter = 0;

    private String genVReg() {
        return "r" + curVRegCounter++;
    }

    private String genVReg(String id) {
        return id + "_r" + curVRegCounter++;
    }

    private String genVBlock() {
        return "b" + curVBlockCounter++;
    }

    private String genVBlock(String id) {
        return id + "_b" + curVBlockCounter++;
    }

    private Scope currentScope = new Scope(GLOBAL_NAME); // global

    private ParseTreeProperty<BasicBlock> propTrueBlock = new ParseTreeProperty<>();
    private ParseTreeProperty<BasicBlock> propFalseBlock = new ParseTreeProperty<>();
    private ParseTreeProperty<BasicBlock> propNextBlock = new ParseTreeProperty<>();

    private Stack<BasicBlock> whileBeginStack = new Stack<>();
    private Stack<BasicBlock> whileExitStack = new Stack<>();

    LLVMIRGenVisitor(String filename) {
        this.filename = filename;
    }

    @Override
    public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
        // init LLVM

        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();

        visitChildren(ctx);

        BytePointer error = new BytePointer();

        if (LLVMPrintModuleToFile(module, filename, error) != 0) { // module
            LLVMDisposeMessage(error);
        }
        return null;
    }

    @Override
    public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {
        /**
         * // build functype
         * local.retType = LLVMType(funcType)
         * local.ID = ID.string
         * 
         * // buld params type and scope
         * create paramsScope
         * visit Params
         * put params as new Symbol() into paramsScope
         * local.paramsType = Array[params*.type]
         * append paramsScope to curScope
         * 
         * build function as new Symbol() into curScope(global)
         * 
         * // gencode
         * gencode(local.paramsType)
         * gencode(funcdef)
         * 
         * // build context
         * global.curFunc = new Fun()
         * 
         * // build symbol table
         * creact functionScope
         * append functionScope to paramsScope
         * set curScope to functionScope
         * 
         * // base block for generate
         * global.curBlock = new Block(curFunc) : base block, then follow inst.;
         * 
         * 
         * // set Flow Control related logic
         * D.next = new Block()
         * 
         * visit block
         * 
         * set curScope back to global
         * 
         */

        // local.retType = LLVMType(funcType)
        var globalScope = currentScope;
        LLVMTypeRef retType;
        if (ctx.funcType().VOID() != null) {
            retType = voidType;
        } else {
            retType = i32Type;
        }

        // local.ID = ID.string
        String funcName = ctx.IDENT().getText();

        Scope paramsScope = new Scope(funcName + "_params");
        var funcFParams = ctx.funcFParams() != null ? ctx.funcFParams().funcFParam() : null;
        int n = (funcFParams != null) ? funcFParams.size() : 0;
        PointerPointer<Pointer> paramsTypes = new PointerPointer<>(n);
        for (int i = 0; i < n; i++) {
            // WARNING:: Assuming all type are i32
            var t = i32Type;
            paramsTypes.put(i, t);
        }

        paramsScope.setParentScope(currentScope);

        LLVMTypeRef ft = LLVMFunctionType(retType, paramsTypes, n, 0);
        curFunc = LLVMAddFunction(module, funcName, ft);
        Scope funcBlockScope = new Scope(funcName + "_block");
        currentScope.setSymbol(funcName, new Symbol(ft, funcName, curFunc));
        funcBlockScope.setParentScope(paramsScope);

        // a block to store
        var curBlock = LLVMAppendBasicBlock(curFunc, genVBlock(funcName));
        LLVMPositionBuilderAtEnd(builder, curBlock);

        for (int i = 0; i < n; i++) {
            var funcFParam = funcFParams.get(i);
            String paramName = funcFParam.IDENT().getText();
            // WARNING:: Assuming all type are i32
            var t = i32Type;
            LLVMValueRef vp = LLVMGetParam(curFunc, i);
            LLVMValueRef v = LLVMBuildAlloca(builder, t, genVReg(paramName));
            if (builder == null || vp == null || v == null) {
                throw new IllegalArgumentException("Invalid arguments for LLVMBuildStore");
            }
            LLVMBuildStore(builder, vp, v);
            var symbol = new Symbol(t, paramName, v);
            paramsScope.setSymbol(paramName, symbol);
        }

        currentScope = funcBlockScope;
        visit(ctx.block());
        currentScope = globalScope;

        return curFunc;
    }

    @Override
    public LLVMValueRef visitBlock(SysYParser.BlockContext ctx) {
        /**
         * D -> DI*
         * 
         * base:
         * DI1 || new || DI2 || new || ... || new <- this should always exist || DIn
         * want:
         * if DIk not used new:
         * DI1 DI2 instead of DI1 new DI2
         * 
         * var next = new()
         * for each DIk in range(0, n - 1) {
         * if next used
         * // some jump to new commited, just append block
         * switch to next
         * next = New()
         * }
         * DIk.next = next
         * visit DIk
         * // exit
         * if next not used:
         * // now shall use it
         * Br to next
         * fin:
         * // force pad this new
         * switch to next
         * DIn.next = this.next
         * viist DIn
         */
        if (ctx.blockItem() == null) {
            return super.visitBlock(ctx);
        }
        var DIs = ctx.blockItem();
        if (DIs.size() == 1) {
            var DI = DIs.get(0);
            propNextBlock.put(DI, propNextBlock.get(ctx));
            return visit(DI);
        }
        BasicBlock nextBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("d")));
        for (int i = 0; i < DIs.size() - 1; i++) {
            if (nextBlock.getUsed()) {
                LLVMPositionBuilderAtEnd(builder, nextBlock.getBlock());
                nextBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("d")));
            }
            var DIk = DIs.get(i);
            propNextBlock.put(DIk, nextBlock);
            visit(DIk);
        }
        if (!nextBlock.getUsed()) {
            LLVMBuildBr(builder, nextBlock.getBlock());
        }
        LLVMPositionBuilderAtEnd(builder, nextBlock.getBlock());
        var DIn = DIs.get(DIs.size() - 1);
        propNextBlock.put(DIn, propNextBlock.get(ctx));
        return visit(DIn);
    }

    @Override
    public LLVMValueRef visitBlockItem(SysYParser.BlockItemContext ctx) {
        if (ctx.stmt() != null) { // DI -> S
            propNextBlock.put(ctx.stmt(), propNextBlock.get(ctx));
        }
        return super.visitBlockItem(ctx);
    }

    public LLVMValueRef visitConstDecl(SysYParser.ConstDeclContext ctx) {
        /**
         * constDecl: CONST bType constDef (COMMA constDef)* SEMICOLON
         * 
         * local.type = i32
         * for constDef in decl:
         * get id, type
         * get iniVal
         * if global: gencode(globaldecl, iniVal)
         * else: gencode(inBlockDecl, iniVal)
         */
        var btype = ctx.bType();
        if (btype.INT() == null) {
            throw new Error("non int currently unsupported.");
        }
        LLVMTypeRef curConstDeclType = i32Type;

        var constDefs = ctx.constDef();

        for (int i = 0; i < constDefs.size(); i++) {
            var constDef = constDefs.get(i);
            String id = constDef.IDENT().getText();
            var tn = curConstDeclType;
            var iniValCtx = constDef.constInitVal();
            // WARN:: This Lab wont have array so disable
            // for (int j = 0; j < constDef.constExp().size(); j++) {
            // // var exp = exps.get(j); wont resolve const exp's value
            // tn = new ArrayType(tn, ARRAY_TYPE_LEN_PLACEHOLDER);
            // }

            // WARN:: assuming all value i32
            var iniValRef = visit(iniValCtx.constExp().exp());
            LLVMValueRef v;
            if (currentScope.getName().equals(GLOBAL_NAME)) {
                // global var
                v = LLVMAddGlobal(module, tn, genVReg(id));
                LLVMSetInitializer(v, iniValRef);
            } else {
                v = LLVMBuildAlloca(builder, tn, genVReg(id));
                LLVMBuildStore(builder, iniValRef, v);
            }
            currentScope.setSymbol(id, new Symbol(tn, id, v));
        }
        return this.defaultResult();
    }

    @Override
    public LLVMValueRef visitVarDecl(SysYParser.VarDeclContext ctx) {
        /**
         * constDecl: CONST bType constDef (COMMA constDef)* SEMICOLON
         * 
         * local.type = i32
         * for constDef in decl:
         * get id, type
         * get iniVal
         * if global: gencode(globaldecl, iniVal)
         * else: gencode(inBlockDecl, iniVal)
         */
        var btype = ctx.bType();
        if (btype.INT() == null) {
            throw new Error("non int currently unsupported.");
        }
        LLVMTypeRef curDeclType = i32Type;

        var varDefs = ctx.varDef();

        for (int i = 0; i < varDefs.size(); i++) {
            var varDef = varDefs.get(i);
            String id = varDef.IDENT().getText();
            var tn = curDeclType;
            var iniValCtx = varDef.initVal();
            // WARN:: This Lab wont have array so disable
            // for (int j = 0; j < constDef.constExp().size(); j++) {
            // // var exp = exps.get(j); wont resolve const exp's value
            // tn = new ArrayType(tn, ARRAY_TYPE_LEN_PLACEHOLDER);
            // }

            // WARN:: assuming all value i32
            LLVMValueRef v;
            LLVMValueRef iniValRef;
            if (iniValCtx != null) {
                iniValRef = visit(iniValCtx.exp());
            } else {
                iniValRef = zero;
            }

            if (currentScope.getName().equals(GLOBAL_NAME)) {
                // global var
                v = LLVMAddGlobal(module, tn, genVReg(id));
                LLVMSetInitializer(v, iniValRef);
            } else {
                v = LLVMBuildAlloca(builder, tn, genVReg(id));
                LLVMBuildStore(builder, iniValRef, v);
            }
            currentScope.setSymbol(id, new Symbol(tn, id, v));
        }
        return this.defaultResult();
    }

    @Override
    public LLVMValueRef visitLVal(SysYParser.LValContext ctx) {
        /**
         * WARN:: Assuming only have: LVal -> IDEND, no LVal IDENT ([ exp ])+
         * 
         * returns mem ref instead of val itself, caller need to load from mem
         */
        var id = ctx.IDENT().getText();
        var symbol = currentScope.getSymbolGlobal(id);
        return symbol.getValueMem();
    }

    @Override
    public LLVMValueRef visitStmt(SysYParser.StmtContext ctx) {
        if (ctx.RETURN() != null) { // stmt -> return (exp)? ;
            /**
             * local.v = exp ? exp.v : null
             * gencode(return local.v)
             */
            if (ctx.exp() != null) {
                LLVMBuildRet(builder, visit(ctx.exp()));
            } else {
                LLVMBuildRetVoid(builder);
            }
            return this.defaultResult();
        } else if (ctx.ASSIGN() != null) { // stmt -> lVal = exp;
            /**
             * get lval's mem
             * get exp's val
             * store val to mem
             */
            var mem = visit(ctx.lVal());
            var val = visit(ctx.exp());
            LLVMBuildStore(builder, val, mem);
        } else if (ctx.exp() != null) { // stmt -> exp;
            visit(ctx.exp());
        } else if (ctx.BREAK() != null) { // stmt -> break;
            var ExitBlock = whileExitStack.peek();
            LLVMBuildBr(builder, ExitBlock.getBlock());
            ExitBlock.setUsed(true);
        } else if (ctx.CONTINUE() != null) { // stmt -> continue;
            var BeginBlock = whileBeginStack.peek();
            LLVMBuildBr(builder, BeginBlock.getBlock());
            BeginBlock.setUsed(true);
        } else if (ctx.IF() != null && ctx.ELSE() == null) { // S -> if ( B ) S0
            /**
             * B.ture = new Block()
             * B.false = S0.next = S.next
             * S.code = B.code || label(B.true) || S0.code
             */
            var TrueBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("ifbody")));
            propTrueBlock.put(ctx.cond(), TrueBlock);
            propNextBlock.put(ctx.stmt(0), propNextBlock.get(ctx));
            propFalseBlock.put(ctx.cond(), propNextBlock.get(ctx));

            visit(ctx.cond());

            LLVMPositionBuilderAtEnd(builder, TrueBlock.getBlock()); // jump is done in B

            visit(ctx.stmt(0));
        } else if (ctx.IF() != null) { // S -> if ( B ) S0 else S1
            /**
             * B.true = new()
             * B.false = new()
             * S1.next = S0.next = S.next
             * build B
             * switch to B.true
             * build S0
             * gen goto S0.next
             * 
             * switch to B.false
             * build S1
             */
            var TrueBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("ifbody")));
            var FalseBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("elsebody")));
            propTrueBlock.put(ctx.cond(), TrueBlock);
            propFalseBlock.put(ctx.cond(), FalseBlock);
            propNextBlock.put(ctx.stmt(0), propNextBlock.get(ctx));
            propNextBlock.put(ctx.stmt(1), propNextBlock.get(ctx));

            visit(ctx.cond());

            LLVMPositionBuilderAtEnd(builder, TrueBlock.getBlock());
            visit(ctx.stmt(0));
            LLVMBuildBr(builder, propNextBlock.get(ctx.stmt(0)).getBlock());
            propNextBlock.get(ctx.stmt(0)).setUsed(true);

            LLVMPositionBuilderAtEnd(builder, FalseBlock.getBlock());
            visit(ctx.stmt(1));

        } else if (ctx.WHILE() != null) { // S -> while ( B ) S0
            /**
             * begin = new()
             * B.true = new()
             * B.false = S.next
             * S0.next = begin
             * 
             * link cur to begin
             * switch to begin
             * build B
             * switch to B.true
             * build S
             * gen goto begin
             */
            var BeginBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("whilebegin")));
            var TrueBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("whilebody")));
            propTrueBlock.put(ctx.cond(), TrueBlock);
            propFalseBlock.put(ctx.cond(), propNextBlock.get(ctx));
            propNextBlock.put(ctx.stmt(0), BeginBlock);

            LLVMBuildBr(builder, BeginBlock.getBlock());
            BeginBlock.setUsed(true);
            LLVMPositionBuilderAtEnd(builder, BeginBlock.getBlock());
            visit(ctx.cond());

            whileBeginStack.push(BeginBlock);
            whileExitStack.push(propNextBlock.get(ctx));

            LLVMPositionBuilderAtEnd(builder, TrueBlock.getBlock());
            visit(ctx.stmt(0));

            whileBeginStack.pop();
            whileExitStack.pop();

        } else if (ctx.block() != null) { // S -> D
            /**
             * D.next = S.next
             * build D
             */
            propNextBlock.put(ctx.block(), propNextBlock.get(ctx));
            visit(ctx.block());
        }
        return this.defaultResult();
    }

    @Override
    public LLVMValueRef visitCond(SysYParser.CondContext ctx) {
        var BTrueBlock = propTrueBlock.get(ctx);
        var BFalseBlock = propFalseBlock.get(ctx);
        if (ctx.AND() != null) { // B -> B0 && B1
            /**
             * B0.true = new()
             * B1.true = B.true
             * B1.false = B1.false = B.false
             * 
             * build B0
             * switch to B0.true
             * build B1
             */
            var B0 = ctx.cond(0);
            var B1 = ctx.cond(1);

            var B0TrueBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("true")));
            propTrueBlock.put(B0, B0TrueBlock);
            propTrueBlock.put(B1, BTrueBlock);
            propFalseBlock.put(B0, BFalseBlock);
            propFalseBlock.put(B1, BFalseBlock);

            visit(B0);
            LLVMPositionBuilderAtEnd(builder, B0TrueBlock.getBlock());
            visit(B1);
        } else if (ctx.OR() != null) { // B-> B0 || B1
            /**
             * B0.false = new()
             * B1.false = B.false
             * B1.true = B0.true = B.true
             * 
             * build B0
             * switch to B0.false
             * build B1
             */
            var B0 = ctx.cond(0);
            var B1 = ctx.cond(1);

            var B0FalseBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("false")));
            propFalseBlock.put(B0, B0FalseBlock);
            propFalseBlock.put(B1, BFalseBlock);
            propTrueBlock.put(B0, BTrueBlock);
            propTrueBlock.put(B1, BTrueBlock);

            visit(B0);
            LLVMPositionBuilderAtEnd(builder, B0FalseBlock.getBlock());
            visit(B1);
        } else if (ctx.exp() != null) { // B -> E
            /**
             * gen E: true:goto B.true, false:goto B.false
             */
            var v = visit(ctx.exp());
            var con = LLVMBuildICmp(builder, LLVMIntNE, v, zero, genVReg("con"));
            LLVMBuildCondBr(builder, con, BTrueBlock.getBlock(), BFalseBlock.getBlock());
            BTrueBlock.setUsed(true);
            BFalseBlock.setUsed(true);
        } else { // B -> B0 OP B1
            /**
             * LLVMValueRef valV0, valB1
             * if B0 -> exp:
             * valB0 = exp.val
             * else:
             * B0.true = new()
             * B0.false = new()
             * resb = new()
             * 
             * var mem = alloc
             * build B0
             * 
             * switch B0.true
             * store 1 to mem
             * br resb
             * 
             * switch B0.false
             * store 0 to mem
             * 
             * switch resb
             * valB0 = load mem
             * 
             * same to B1
             * 
             * LLVMValueRef res = valB0 OP valB1
             * br res true:B.true false:B.false
             */
            LLVMValueRef valB0, valB1;
            var B0 = ctx.cond(0);
            var B1 = ctx.cond(1);
            if (B0.exp() != null) {
                valB0 = visit(B0.exp());
            } else {
                var TrueBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("true")));
                var FalseBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("false")));
                var RestBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("rest")));
                propTrueBlock.put(B0, TrueBlock);
                propFalseBlock.put(B0, FalseBlock);

                var mem = LLVMBuildAlloca(builder, i32Type, genVReg("mem0"));
                visit(B0);

                LLVMPositionBuilderAtEnd(builder, TrueBlock.getBlock());
                LLVMBuildStore(builder, LLVMConstInt(i32Type, 1, 0), mem);
                LLVMBuildBr(builder, RestBlock.getBlock());

                LLVMPositionBuilderAtEnd(builder, FalseBlock.getBlock());
                LLVMBuildStore(builder, zero, mem);

                LLVMPositionBuilderAtEnd(builder, RestBlock.getBlock());
                valB0 = LLVMBuildLoad(builder, mem, genVBlock("vb0"));
            }
            if (B1.exp() != null) {
                valB1 = visit(B1.exp());
            } else {
                var TrueBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("true")));
                var FalseBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("false")));
                var RestBlock = new BasicBlock(LLVMAppendBasicBlock(curFunc, genVBlock("rest")));
                propTrueBlock.put(B1, TrueBlock);
                propFalseBlock.put(B1, FalseBlock);

                var mem = LLVMBuildAlloca(builder, i32Type, genVReg("mem1"));
                visit(B1);

                LLVMPositionBuilderAtEnd(builder, TrueBlock.getBlock());
                LLVMBuildStore(builder, LLVMConstInt(i32Type, 1, 0), mem);
                LLVMBuildBr(builder, RestBlock.getBlock());

                LLVMPositionBuilderAtEnd(builder, FalseBlock.getBlock());
                LLVMBuildStore(builder, LLVMConstInt(i32Type, 0, 0), mem);

                LLVMPositionBuilderAtEnd(builder, RestBlock.getBlock());
                valB1 = LLVMBuildLoad(builder, mem, genVBlock("vb1"));
            }
            LLVMValueRef varRes;
            if (ctx.LT() != null) {
                varRes = LLVMBuildICmp(builder, LLVMIntSLT, valB0, valB1, genVReg());
            } else if (ctx.GT() != null) {
                varRes = LLVMBuildICmp(builder, LLVMIntSGT, valB0, valB1, genVReg());
            } else if (ctx.LE() != null) {
                varRes = LLVMBuildICmp(builder, LLVMIntSLE, valB0, valB1, genVReg());
            } else if (ctx.GE() != null) {
                varRes = LLVMBuildICmp(builder, LLVMIntSGE, valB0, valB1, genVReg());
            } else if (ctx.EQ() != null) {
                varRes = LLVMBuildICmp(builder, LLVMIntEQ, valB0, valB1, genVReg());
            } else { // (ctx.NEQ() != null)
                varRes = LLVMBuildICmp(builder, LLVMIntNE, valB0, valB1, genVReg());
            }
            LLVMBuildCondBr(builder, varRes, BTrueBlock.getBlock(), BFalseBlock.getBlock());
            BTrueBlock.setUsed(true);
            BFalseBlock.setUsed(true);
        }
        return this.defaultResult();
    }

    @Override
    public LLVMValueRef visitExp(SysYParser.ExpContext ctx) {
        if (ctx.L_PAREN() != null && ctx.IDENT() == null) { // exp -> '(' exp ')'
            /**
             * visit exp0
             * this.val = this.exp0.val;
             */
            return visit(ctx.exp(0));
        } else if (ctx.number() != null) { // exp -> number
            /**
             * this.val = int(this.number.string)
             */
            int value = Integer.decode(ctx.number().getText());
            return LLVMConstInt(i32Type, value, 0);
        } else if (ctx.unaryOp() != null) { // exp -> unaryOp exp0
            /**
             * visit exp0
             * this.val = gencode(uop, exp0.val).valRef
             */
            var exp0vref = visit(ctx.exp(0));
            LLVMValueRef expvref;
            if (ctx.unaryOp().NOT() != null) {
                expvref = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(i32Type, 0, 0), exp0vref, genVReg());
                expvref = LLVMBuildXor(builder, expvref, LLVMConstInt(LLVMInt1Type(), 1, 0), genVReg());
                expvref = LLVMBuildZExt(builder, expvref, i32Type, genVReg());
            } else if (ctx.unaryOp().PLUS() != null) {
                expvref = exp0vref;
            } else { // MINUS
                expvref = LLVMBuildSub(builder, LLVMConstInt(i32Type, 0, 0), exp0vref, genVReg());
            }
            return expvref;
        } else if (ctx.exp().size() == 2) { // exp -> exp0 OP exp1
            /**
             * visit exp0
             * visit exp1
             * this.val = gencode(exp0.val, op, exp1.val).ref
             */
            var exp0vref = visit(ctx.exp(0));
            var exp1vref = visit(ctx.exp(1));
            if (ctx.MUL() != null) {
                return LLVMBuildMul(builder, exp0vref, exp1vref, genVReg());
            } else if (ctx.DIV() != null) {
                return LLVMBuildSDiv(builder, exp0vref, exp1vref, genVReg());
            } else if (ctx.MOD() != null) {
                return LLVMBuildSRem(builder, exp0vref, exp1vref, genVReg());
            } else if (ctx.PLUS() != null) {
                return LLVMBuildAdd(builder, exp0vref, exp1vref, genVReg());
            } else if (ctx.MINUS() != null) {
                return LLVMBuildSub(builder, exp0vref, exp1vref, genVReg());
            }
            return null;
        } else if (ctx.lVal() != null) { // epx -> lVal
            /**
             * visit lVal
             * this.val = gencode(load lval).ref
             */
            var lValMem = visit(ctx.lVal());
            return LLVMBuildLoad(builder, lValMem, genVReg());
        } else if (ctx.IDENT() != null) { // exp -> IDENT ( funcRParams? )
            var funcId = ctx.IDENT().getText();
            var funcSymbol = currentScope.getSymbolGlobal(funcId);
            var funcRetType = funcSymbol.getType();
            var funcRef = funcSymbol.getValueMem();
            if (ctx.funcRParams() == null) {
                return LLVMBuildCall2(builder, funcRetType, funcRef, null, 0, genVReg(funcId));
            } else {
                var params = ctx.funcRParams().param();
                PointerPointer<Pointer> pRefs = new PointerPointer<>(params.size());
                for (int i = 0; i < params.size(); i++) {
                    var exp = params.get(i).exp();
                    var v = visit(exp);
                    pRefs.put(i, v);
                }
                return LLVMBuildCall2(builder, funcRetType, funcRef, pRefs, params.size(), genVReg(funcId));
            }
        } else {
            return visitChildren(ctx);
        }
    }
}
