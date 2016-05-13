package analysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import antlr.JavaLexer;
import antlr.JavaParser;
import antlr.JavaParser.BlockContext;
import antlr.JavaParser.BlockStatementContext;
import antlr.JavaParser.ClassDeclarationContext;
import antlr.JavaParser.CompilationUnitContext;
import antlr.JavaParser.ConstructorBodyContext;
import antlr.JavaParser.ConstructorDeclarationContext;
import antlr.JavaParser.ExpressionContext;
import antlr.JavaParser.FieldDeclarationContext;
import antlr.JavaParser.LocalVariableDeclarationStatementContext;
import antlr.JavaParser.MethodDeclarationContext;
import antlr.JavaParser.StatementContext;
import antlr.JavaParser.StatementExpressionContext;
import antlr.JavaParser.TypeDeclarationContext;

public class Unit {
	public final Path path;
	public int classCount = 0;
	public String extendsClass;
	public FailureSet failureSet; 
	public final boolean compiled;
	
	public Unit(Path path){
//		System.out.println(path.toString());
		this.path = path;
		CompilationUnitContext compilationUnit = getCompilationUnit(path);
		this.compiled = compilationUnit != null;
		if (!this.compiled) return;
//		System.out.println(compilationUnit.getText().length());
		this.failureSet = new FailureSet(path);
		findExtension(compilationUnit);
		findFailures(compilationUnit);
	}
	
	private void findExtension(ParseTree tree) {
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof ClassDeclarationContext){
				ClassDeclarationContext classDecl = (ClassDeclarationContext) c;
				for (int j=0; j<classDecl.getChildCount(); j++){
					if (classDecl.getChild(j).getText().equals("extends")){
						extendsClass = classDecl.getChild(j+1).getText();
						return;
					}
				}
			}
			else findExtension(c);
		}
	}

	private void findFailures(CompilationUnitContext compilationUnit) {
        List<Failure> failures = listClasses(compilationUnit, "");
        if (!failures.isEmpty()){
        	this.failureSet.add(failures.stream());
        }
	}

	public List<Failure> listClasses(ParseTree tree, String indent){
		List<Failure> failures = new ArrayList<Failure>();
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof ClassDeclarationContext){
				classCount++;
				ClassDeclarationContext classDecl = (ClassDeclarationContext) c;
				failures.addAll(listSubDeclarations(classDecl, indent + "  "));
			}
			else failures.addAll(listClasses(c, indent + "  "));
		}
		return failures;
	}

	/**
	 * Go through all sub-class-declarations -> methods, constructors and fields
	 */
	public List<Failure> listSubDeclarations(ParseTree tree, String indent){
		List<Failure> failures = new ArrayList<Failure>();
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof MethodDeclarationContext){
				MethodDeclarationContext methodDecl = (MethodDeclarationContext) c;
				failures.addAll(listStatements(methodDecl, indent + "  "));
			} else if (c instanceof ConstructorDeclarationContext){
				ConstructorDeclarationContext ctorDecl = (ConstructorDeclarationContext) c;
				failures.addAll(listStatements(ctorDecl, indent + "  "));
			} else if (c instanceof FieldDeclarationContext){
				// Field decl is safe
			}
			failures.addAll(listSubDeclarations(c, indent));
		}
		return failures;
	}

	private List<Failure> listStatements(ParseTree tree, String indent) {
		List<Failure> failures = new ArrayList<Failure>();
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof ConstructorBodyContext){
				ConstructorBodyContext ctorBody = (ConstructorBodyContext) c;
				for (BlockStatementContext stmt : getStatements(ctorBody)){
					failures.addAll(listExpressions(stmt, indent));
				}
			}
		}
		return failures;
	}

	private static List<Failure> listExpressions(BlockStatementContext stmt, String indent) {
		List<Failure> failures = new ArrayList<Failure>();
		for (ExpressionContext expr : getExpressions((BlockStatementContext)stmt)){
			if (isAssignment(expr)){
				ExpressionContext rhs = (ExpressionContext) expr.getChild(2);
				if(rhs.getText().equals("this") || isMethodCallPassingThis(rhs)){
					failures.add(new Failure(expr, FailureType.STORING_THIS));
				} else if (isDownCall(rhs)){
					failures.add(new Failure(expr, FailureType.DOWN_CALL));
				}
			}
			else if (isDownCall(expr)) {
				failures.add(new Failure(expr, FailureType.DOWN_CALL));
			}
			else if (isMethodCallPassingThis(expr)){
				failures.add(new Failure(expr, FailureType.STORING_THIS));
			}
		}
		return failures;
	}

	private static boolean isMethodCallPassingThis(ExpressionContext expr) {
		boolean inParams = false;
		for (int i = 0; i < expr.getChildCount(); i++){
			if (expr.getChild(i).getText().equals("(")){
				inParams = true;
				continue;
			}
			if (!inParams) continue;
			if (expr.getChild(i).getText().equals(")")) return false;
			if (expr.getChild(i).getText().equals("this")) return true;
		}
		return false;
	}

	private static boolean isDownCall(ExpressionContext expr) {
		if (expr.getChildCount() >= 2){
			// [notSuper][(]
			return !expr.getChild(0).getText().equals("super") &&
					!expr.getChild(0).getText().equals("this") &&
					expr.getChild(0).getChildCount() == 1 &&
					expr.getChild(1).getText().equals("(");
		}
		if (expr.getChildCount() >= 5){
			// [this][.][anything][(]
			return expr.getChild(0).getText().equals("this") &&
					expr.getChild(3).getText().equals("(");
		}
		return false;
	}
	
	private static boolean isAssignment(ExpressionContext expr) {
		return expr.getChildCount() > 1
				&& expr.getChild(1) instanceof TerminalNodeImpl
				&& ((TerminalNodeImpl)expr.getChild(1)).symbol.getText().equals("=");
	}

	private static List<BlockStatementContext> getStatements(ParseTree tree){
		if (tree.getChildCount() >= 1 && tree.getChild(0) instanceof BlockContext){
			return getStatements(tree.getChild(0));
		}
		if (!(tree instanceof BlockContext)) throw new RuntimeException("Block context not found");
		return ((BlockContext) tree)
				.children
				.stream()
				.filter(x -> x instanceof BlockStatementContext)
				.map(x -> (BlockStatementContext)x)
				.collect(Collectors.toList());
	}
	
	private static List<ExpressionContext> getExpressions(BlockStatementContext stmt){
		ParseTree c = stmt.getChild(0);
		if (c instanceof LocalVariableDeclarationStatementContext){
			return ((LocalVariableDeclarationStatementContext)c)
				.children
				.stream()
				.filter(x -> x instanceof StatementExpressionContext)
				.map(x -> (ExpressionContext)x.getChild(0))
				.collect(Collectors.toList());
		}
		else if (c instanceof TypeDeclarationContext){
			TypeDeclarationContext tdc = (TypeDeclarationContext)c;
			if (tdc.children == null) return new ArrayList<ExpressionContext>();
			else return ((TypeDeclarationContext)c)
				.children
				.stream()
				.filter(x -> x instanceof StatementExpressionContext)
				.map(x -> (ExpressionContext)x.getChild(0))
				.collect(Collectors.toList());
		}
		else if (c instanceof StatementContext){
			StatementContext sc = (StatementContext)c;
			if (sc.children == null) return new ArrayList<ExpressionContext>();
			return ((StatementContext)c)
				.children
				.stream()
				.filter(x -> x instanceof StatementExpressionContext)
				.map(x -> (ExpressionContext)x.getChild(0))
				.collect(Collectors.toList());
		}
		return new ArrayList<ExpressionContext>();
	}
	
	private static List<ParseTree> childrenOf(ParseTree tree){
		return IntStream
			.range(0, tree.getChildCount())
			.mapToObj(i -> tree.getChild(i))
			.collect(Collectors.toList());
	}

	private CompilationUnitContext getCompilationUnit(Path path) {
		try {
			CharStream in = new ANTLRInputStream(Files.newBufferedReader(path));
		    JavaLexer lexer = new JavaLexer(in);
		    lexer.removeErrorListeners();
		    CommonTokenStream tokens = new CommonTokenStream(lexer);
		    JavaParser parser = new JavaParser(tokens);
		    parser.removeErrorListeners();
		    return parser.compilationUnit();
		} catch (Exception e) {
			return null;//throw new RuntimeException("Could not read file");
		}
	}
}
