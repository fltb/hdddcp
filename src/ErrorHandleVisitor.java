import org.antlr.v4.runtime.tree.ParseTreeProperty;

import java.util.ArrayList;
import java.util.HashMap;

class Type {
    public String typename() {
        return "base";
    }

    public boolean accept(Type t) {
        return false;
    }
};

class VoidType extends Type {
    @Override
    public String typename() {
        return "void";
    }

    @Override
    public boolean accept(Type t) {
        return t == null || t instanceof VoidType;
    }
};

class IntType extends Type {
    @Override
    public String typename() {
        return "int";
    }

    @Override
    public boolean accept(Type t) {
        return t instanceof ConstIntType
                || t instanceof IntType
                || t instanceof FloatType
                || t instanceof ConstFloatType;
    }
};

class ConstIntType extends Type {
    @Override
    public String typename() {
        return "const int";
    }

    @Override
    public boolean accept(Type t) {
        return t instanceof ConstIntType
                || t instanceof ConstFloatType;
    }
};

class FloatType extends Type {
    @Override
    public String typename() {
        return "float";
    }

    @Override
    public boolean accept(Type t) {
        return t instanceof ConstIntType
                || t instanceof IntType
                || t instanceof FloatType
                || t instanceof ConstFloatType;
    }
};

class ConstFloatType extends Type {
    @Override
    public String typename() {
        return "const float";
    }

    @Override
    public boolean accept(Type t) {
        return t instanceof ConstIntType
                || t instanceof ConstFloatType;
    }
};

class ArrayType extends Type {
    @Override
    public String typename() {
        return "array";
    }

    private Type type;
    private int len;

    public ArrayType(Type type, int len) {
        this.type = type;
        this.len = len;
    }

    public Type getType() {
        return type;
    }

    public int getLen() {
        return len;
    }

    private int getDepth(Type type) {
        if (type instanceof ArrayType) {
            return getDepth(((ArrayType) type).getType()) + 1;
        } else {
            return 1;
        }
    }

    public int getDepth() {
        return getDepth(type);
    }

    private Type getBaseType(Type type) {
        if (type instanceof ArrayType) {
            return getBaseType(((ArrayType) type).getType());
        } else {
            return type;
        }
    }

    public Type getBaseType() {
        return getBaseType(type);
    }

    @Override
    public boolean accept(Type t) {
        return t instanceof ArrayType
                && getBaseType().accept(((ArrayType) t).getBaseType())
                && getDepth() == ((ArrayType) t).getDepth();
    }
};

class FuncType extends Type {
    @Override
    public String typename() {
        return "function";
    }

    private Type retType;
    private ArrayList<Type> paramsType;

    public FuncType(Type rt, ArrayList<Type> pst) {
        this.retType = rt;
        this.paramsType = pst;
    }

    public ArrayList<Type> getParamsType() {
        return paramsType;
    }

    public Type getRetType() {
        return retType;
    }

    @Override
    public boolean accept(Type t) {
        if (!(t instanceof FuncType)
                || !retType.accept(((FuncType) t).retType)
                || paramsType.size() != ((FuncType) t).paramsType.size()) {
            return false;
        }
        int n = paramsType.size();
        for (int i = 0; i < n; i++) {
            if (!paramsType.get(i).accept(((FuncType) t).paramsType.get(i))) {
                return false;
            }
        }
        return true;
    }
};

class Value {
    private Type type;
    private Object value;

    public Value(Type t, Object v) {
        type = t;
        value = v;
    }

    public Type getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }
}

class Symbol {
    private Type type;
    private String id;
    private Value value;

    public Symbol(Type t, String i) {
        this.type = t;
        this.id = i;
        this.value = null;
    }

    public String getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public Value getValue() {
        return value;
    }

    public void setValue(Value value) {
        this.value = value;
    }
};

class Scope {
    private Scope parentScope = null;
    private HashMap<String, Symbol> mp;

    public Scope() {
        mp = new HashMap<String, Symbol>();
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

    public void setSymbol(String id, Symbol symbol) {
        if (getSymbol(id) != null) {
            throw new Error("Detected duplicate symbol in scope");
        }
        mp.put(id, symbol);
    }
}

final class ERROR_TYPE {
    public static final int RESERVED = 0;
    public static final int VAR_NO_DECL = 1;
    public static final int FUN_NO_DECL = 2;
    public static final int VAR_DUPLICATE_DECL = 3;
    public static final int FUN_DUPLICATE_DECL = 4;
    public static final int ASSIGN_TYPE_N_MATCH = 5;
    public static final int OP_TYPE_N_MATCH = 6;
    public static final int RET_TYPE_N_MATCH = 7;
    public static final int FUN_PARAM_N_MATCH = 8;
    public static final int INDEX_NON_ARRAY = 9;
    public static final int CALL_NON_FUN = 10;
    public static final int ASSIGN_TO_FUN = 11;
    public static final int UNINTIALIZED_VALUE = 12;
    public static final int ARRAY_BOUND = 13;
    public static final int CONST_ASSIGN = 14;
    public static final int DATA_OVERFLOW = 15;
    public static final int DIVID_ZERO = 16;
};

class OutputErrHelper {
    private int lastPrintType = ERROR_TYPE.RESERVED;
    private int lastLine = -1;

    public void PrintHelper(int type, int line, String msg) {
        if (lastLine == line && lastPrintType != type) {
            return;
        }
        System.out.println("Error type " + type + " at line " + line + ": " + msg);
        lastPrintType = type;
        lastLine = line;
    }

    public boolean HasError() {
        return lastLine != -1;
    }
}

class BaseTypeHelper {
    public static final VoidType voidType = new VoidType();
    public static final IntType intType = new IntType();
    public static final FloatType floatType = new FloatType();
    public static final ConstIntType constIntType = new ConstIntType();
    public static final ConstFloatType constFloatType = new ConstFloatType();
}

public class ErrorHandleVisitor extends SysYParserBaseVisitor<Void> {
    static int ARRAY_TYPE_LEN_PLACEHOLDER = 0;
    private ParseTreeProperty<Type> propType = new ParseTreeProperty<>();
    private ParseTreeProperty<Value> propValue = new ParseTreeProperty<>();
    private Scope currentScope = new Scope(); // global
    private Symbol currentFuncSymbol = null;
    private OutputErrHelper put = new OutputErrHelper();

    private Symbol getSymbolGlobal(Scope scope, String id) {
        if (scope == null) {
            return null;
        } else {
            return scope.getSymbol(id) != null ? scope.getSymbol(id) : getSymbolGlobal(scope.getParentScope(), id);
        }
    }

    private Symbol getSymbolLocal(Scope scope, String id) {
        return scope.getSymbol(id);
    }

    public boolean hasError() {
        return put.HasError();
    }

    public Void visitConstDecl(SysYParser.ConstDeclContext ctx) {
        var btype = ctx.bType();
        Type curConstDeclType = null;
        if (btype.INT() != null) {
            curConstDeclType = BaseTypeHelper.constIntType;
        } else { // if (btype.FLOAT() != null)
            curConstDeclType = BaseTypeHelper.constFloatType;
        }

        var constDefs = ctx.constDef();

        for (int i = 0; i < constDefs.size(); i++) {
            var constDef = constDefs.get(i);
            String id = constDef.IDENT().getText();
            // ERR VAR_DUPLICATE_DEF
            if (getSymbolLocal(currentScope, id) != null) {
                put.PrintHelper(ERROR_TYPE.VAR_DUPLICATE_DECL, constDef.IDENT().getSymbol().getLine(),
                        "var " + id + " duplicate define.");
                continue; // drop def
            }
            Type tn = curConstDeclType;
            // var iniValCtx = constDef.constInitVal();
            for (int j = 0; j < constDef.constExp().size(); j++) {
                // var exp = exps.get(j); wont resolve const exp's value
                tn = new ArrayType(tn, ARRAY_TYPE_LEN_PLACEHOLDER);
            }
            currentScope.setSymbol(id, new Symbol(tn, id));
        }
        return this.defaultResult();
    }

    @Override
    public Void visitVarDecl(SysYParser.VarDeclContext ctx) {
        var btype = ctx.bType();
        Type curConstDeclType = null;
        if (btype.INT() != null) {
            curConstDeclType = BaseTypeHelper.intType;
        } else { // if (btype.FLOAT() != null)
            curConstDeclType = BaseTypeHelper.floatType;
        }

        var varDefs = ctx.varDef();

        for (int i = 0; i < varDefs.size(); i++) {
            var vardef = varDefs.get(i);
            String id = vardef.IDENT().getText();
            // ERR VAR_DUPLICATE_DEF
            if (getSymbolLocal(currentScope, id) != null) {
                put.PrintHelper(ERROR_TYPE.VAR_DUPLICATE_DECL, vardef.IDENT().getSymbol().getLine(),
                        "var " + id + " duplicate define.");
                continue; // drop def
            }
            Type tn = curConstDeclType;
            for (int j = 0; j < vardef.constExp().size(); j++) {
                // var exp = exps.get(j); wont resolve const exp's value
                tn = new ArrayType(tn, ARRAY_TYPE_LEN_PLACEHOLDER);
            }
            currentScope.setSymbol(id, new Symbol(tn, id));
        }
        return this.defaultResult();
    }

    @Override
    public Void visitFuncDef(SysYParser.FuncDefContext ctx) {
        String funcName = ctx.IDENT().getText();
        if (currentScope.getSymbol(funcName) != null) { // curScope为当前的作用域
            put.PrintHelper(ERROR_TYPE.FUN_DUPLICATE_DECL, ctx.IDENT().getSymbol().getLine(),
                    "func " + funcName + " duplicate define.");
            return null;
        }

        Type retType = BaseTypeHelper.voidType;
        String typeStr = ctx.getChild(0).getText();
        if (typeStr.equals("int")) {
            retType = BaseTypeHelper.intType; // 返回值类型为int32
        } else if (typeStr.equals("float")) {
            retType = BaseTypeHelper.floatType;
        }

        Scope funcParamScope = new Scope();
        var paramsTyList = new ArrayList<Type>();
        if (ctx.funcFParams() != null) { // 如有入参，处理形参，添加形参信息等
            for (int i = 0; i < ctx.funcFParams().funcFParam().size(); i++) {
                var param = ctx.funcFParams().funcFParam(i);
                String id = param.IDENT().getText();
                // ERR VAR_DUPLICATE_DEF
                if (getSymbolLocal(funcParamScope, id) != null) {
                    put.PrintHelper(ERROR_TYPE.VAR_DUPLICATE_DECL, param.IDENT().getSymbol().getLine(),
                            "fun " + id + " duplicate define.");
                    continue; // drop def
                }
                Type curParamDeclType = null;
                if (param.bType().INT() != null) {
                    curParamDeclType = BaseTypeHelper.intType;
                } else { // if (btype.FLOAT() != null)
                    curParamDeclType = BaseTypeHelper.floatType;
                }
                Type tn = curParamDeclType;
                var exps = param.exp();
                for (int j = 0; j < exps.size(); j++) {
                    // var exp = exps.get(j); wont resolve const exp's value
                    tn = new ArrayType(tn, ARRAY_TYPE_LEN_PLACEHOLDER);
                }
                // System.err.println("INFO Added Func Scope " + id);
                funcParamScope.setSymbol(id, new Symbol(tn, id));
                paramsTyList.add(tn);
            }
        }

        var functionType = new FuncType(retType, paramsTyList);
        currentFuncSymbol = new Symbol(functionType, funcName);
        // 顶层作用域中压入此函数
        currentScope.setSymbol(funcName, currentFuncSymbol);
        // 切换 scope
        funcParamScope.setParentScope(currentScope);
        currentScope = funcParamScope;
        visit(ctx.block());
        currentScope = currentScope.getParentScope(); // exit block scope
        currentFuncSymbol = null;
        return null;
    }

    @Override
    public Void visitBlock(SysYParser.BlockContext ctx) {
        var blockScope = new Scope();
        blockScope.setParentScope(currentScope);
        currentScope = blockScope;
        var ret = visitChildren(ctx);
        currentScope = currentScope.getParentScope();
        return ret;
    }

    @Override
    public Void visitLVal(SysYParser.LValContext ctx) {
        String id = ctx.IDENT().getText();
        // ERR TYPE 1 VAR_NO_DECL
        if (null == getSymbolGlobal(currentScope, id)) {
            put.PrintHelper(ERROR_TYPE.VAR_NO_DECL, ctx.IDENT().getSymbol().getLine(), "var " + id + " not defined.");
        } else {
            var symbol = getSymbolGlobal(currentScope, id);
            var tn = symbol.getType();
            int n = ctx.exp().size();
            for (int i = 0; i < n; i++) {
                var exp = ctx.exp(i);
                visit(exp);
                if (!BaseTypeHelper.intType.accept(propType.get(exp))) {
                    put.PrintHelper(ERROR_TYPE.OP_TYPE_N_MATCH, ctx.IDENT().getSymbol().getLine(),
                            "non int type detected in subscript operator.");
                    return null;
                }
                if (tn instanceof ArrayType) {
                    tn = ((ArrayType) tn).getType();
                } else {
                    put.PrintHelper(ERROR_TYPE.INDEX_NON_ARRAY, ctx.IDENT().getSymbol().getLine(),
                            "Using the subscript operator on non-array var " + id);
                    return null;
                }
            }
            propType.put(ctx, tn);
        }
        return null;
    }

    @Override
    public Void visitExp(SysYParser.ExpContext ctx) {
        if (ctx.IDENT() != null) { // func call;
            String id = ctx.IDENT().getText();
            // ERR TYPE 2 FUN_NO_DECL
            if (null == getSymbolGlobal(currentScope, id)) {
                put.PrintHelper(ERROR_TYPE.FUN_NO_DECL, ctx.IDENT().getSymbol().getLine(),
                        "var " + id + " not defined.");
                return null;
            }
            var t = getSymbolGlobal(currentScope, id).getType();
            if (!(t instanceof FuncType)) {
                put.PrintHelper(ERROR_TYPE.CALL_NON_FUN, ctx.IDENT().getSymbol().getLine(), "Call non function: " + id);
                return null;
            }
            FuncType funcType = (FuncType) t;
            // check arg types
            if (ctx.funcRParams().param().size() != funcType.getParamsType().size()) {
                put.PrintHelper(ERROR_TYPE.FUN_PARAM_N_MATCH, ctx.IDENT().getSymbol().getLine(),
                        "param of fun call " + id + " not match param length");
                return null;
            }
            for (int i = 0; i < ctx.funcRParams().param().size(); i++) {
                var param = ctx.funcRParams().param(i);
                visit(param.exp());
                if (null == propType.get(param.exp())
                        || !funcType.getParamsType().get(i).accept(propType.get(param.exp()))) {
                    var texp = propType.get(param.exp());
                    String nametexp = texp == null ? "null" : texp.typename();
                    put.PrintHelper(ERROR_TYPE.FUN_PARAM_N_MATCH, ctx.IDENT().getSymbol().getLine(),
                            "param of fun call " + id + " not match type, expected "
                                    + funcType.getParamsType().get(i).typename() + " but detected "
                                    + nametexp);
                    return null;
                }
            }
            propType.put(ctx, funcType.getRetType());

            return null;
        } else if (ctx.L_PAREN() != null) { // (exp)
            var ret = visitChildren(ctx);
            propType.put(ctx, propType.get(ctx.exp(0)));
            propValue.put(ctx, propValue.get(ctx.lVal()));
            return ret;
        } else if (ctx.lVal() != null) { // lval
            var ret = visitChildren(ctx);
            propType.put(ctx, propType.get(ctx.lVal()));
            propValue.put(ctx, propValue.get(ctx.lVal()));
            return ret;
        } else if (ctx.number() != null) { // number
            propType.put(ctx, BaseTypeHelper.intType);
            propValue.put(ctx, new Value(BaseTypeHelper.intType, Integer.decode(ctx.number().getText())));
            return null;
        } else if (ctx.unaryOp() != null) { // unary exp
            var ret = visitChildren(ctx);
            var t = propType.get(ctx.exp(0));
            var op = ctx.unaryOp().MINUS();
            if (null != ctx.unaryOp().MINUS()) {
                op = ctx.unaryOp().MINUS();
            } else if (null != ctx.unaryOp().PLUS()) {
                op = ctx.unaryOp().PLUS();
            } else if (null != ctx.unaryOp().NOT()) {
                op = ctx.unaryOp().NOT();
            }
            if (!(t instanceof IntType)
                    || !(t instanceof ConstIntType)
                    || !(t instanceof FloatType)
                    || !(t instanceof ConstFloatType)) {
                put.PrintHelper(ERROR_TYPE.OP_TYPE_N_MATCH, op.getSymbol().getLine(),
                        "op " + op.getText() + " type not match exp");
                propType.put(ctx, null);
            } else {
                propType.put(ctx, t);
                var val = propValue.get(ctx.exp(0));
                // var valType = val.getType();
                if (null != ctx.unaryOp().MINUS()) {
                    propValue.put(ctx, new Value(val.getType(), -(int) val.getValue()));
                } else if (null != ctx.unaryOp().PLUS()) {

                } else if (null != ctx.unaryOp().NOT()) {

                }
            }
            return ret;
        } else { // binary exp
            var ret = visitChildren(ctx);
            var t1 = propType.get(ctx.exp(0));
            var t2 = propType.get(ctx.exp(1));
            if (t1 != null && t1.accept(t2)) {
                propType.put(ctx, t1);
            } else if (t2 != null && t2.accept(t1)) {
                propType.put(ctx, t2);
            } else {
                var op = ctx.MUL();
                if (op == null) {
                    op = ctx.MOD();
                }
                if (op == null) {
                    op = ctx.DIV();
                }
                if (op == null) {
                    op = ctx.PLUS();
                }
                if (op == null) {
                    op = ctx.MINUS();
                }
                put.PrintHelper(ERROR_TYPE.OP_TYPE_N_MATCH, op.getSymbol().getLine(),
                        "op type not match exp, types: "
                                + (t1 != null ? t1.typename() : "null")
                                + ", "
                                + (t2 != null ? t2.typename() : "null")
                                + ".");
            }
            return ret;
        }
    }

    @Override
    public Void visitStmt(SysYParser.StmtContext ctx) {
        var ret = visitChildren(ctx);
        if (ctx.ASSIGN() != null) { // assign
            var tl = propType.get(ctx.lVal());
            var tr = propType.get(ctx.exp());
            if (tl instanceof ConstIntType || tl instanceof ConstFloatType) {
                put.PrintHelper(ERROR_TYPE.CONST_ASSIGN, ctx.ASSIGN().getSymbol().getLine(),
                        "assign to const value.");
            } else if (tl instanceof FuncType) {
                put.PrintHelper(ERROR_TYPE.ASSIGN_TO_FUN, ctx.ASSIGN().getSymbol().getLine(),
                        "assign to func: "
                                + tl.typename()
                                + ", "
                                + (tr != null ? tr.typename() : "null"));
            } else if (tl == null || !tl.accept(tr)) {
                put.PrintHelper(ERROR_TYPE.ASSIGN_TYPE_N_MATCH, ctx.ASSIGN().getSymbol().getLine(),
                        "assign type not match: "
                                + (tl != null ? tl.typename() : "null")
                                + ", "
                                + (tr != null ? tr.typename() : "null"));
            }
        } else if (ctx.RETURN() != null) { // return
            var retType = ((FuncType) currentFuncSymbol.getType()).getRetType();
            Type t = null;
            if (ctx.exp() != null) {
                t = propType.get(ctx.exp());
            }
            if (!retType.accept(t)) {
                put.PrintHelper(ERROR_TYPE.RET_TYPE_N_MATCH, ctx.RETURN().getSymbol().getLine(),
                        "Return type Not match, expected "
                                + retType.typename()
                                + " but detected "
                                + (t != null ? t.typename() : "null")
                                + ".");
            }
        }
        return ret;
    }
}
