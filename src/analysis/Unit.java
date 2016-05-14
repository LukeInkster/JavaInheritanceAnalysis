package analysis;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
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
	public final boolean compiled;
	
	public int classCount = 0;
	public String superClassName;
	public FailureSet failureSet; 
	public String className;
	public boolean hasForwarding;
	public boolean hasDelegation;
	
	public static final Pattern forwarding =
			Pattern.compile("[\\w<>]+\\s+(\\w+)\\s*\\(.*\\)\\s*\\{\\s*return\\s+\\w+(\\.\\w+)*\\.\\1\\(.*\\)\\s*;\\s*\\}");
	
	private static final List<String> classDeclKeywords = 
			Arrays.asList("public", "private", "abstract", "protected", "static", "final", "strictfp", "class");
	
	public Unit(Path path){
		this.path = path;
		CompilationUnitContext compilationUnit = getCompilationUnit(path);
		this.compiled = compilationUnit != null;
		if (!this.compiled) return;
		this.failureSet = new FailureSet(path);
		findClassnameAndExtension(compilationUnit);
		scanClasses(compilationUnit, "");
	}
	
	private void findClassnameAndExtension(ParseTree tree) {
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof ClassDeclarationContext){
				ClassDeclarationContext classDecl = (ClassDeclarationContext) c;
				for (ParseTree declWord : childrenOf(classDecl)){
					if (classDeclKeywords.contains(declWord.getText().toLowerCase())) continue;
					className = declWord.getText();
					break;
				}
				for (int j=0; j<classDecl.getChildCount(); j++){
					if (classDecl.getChild(j).getText().equals("extends")){
						superClassName = classDecl.getChild(j+1).getText();
						break;
					}
				}
			}
			else findClassnameAndExtension(c);
		}
	}

	public void scanClasses(ParseTree tree, String indent){
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof ClassDeclarationContext){
				classCount++;
				ClassDeclarationContext classDecl = (ClassDeclarationContext) c;
				scanSubDeclarations(classDecl, indent + "  ");
			}
			else scanClasses(c, indent + "  ");
		}
	}

	/**
	 * Go through all sub-class-declarations -> methods, constructors and fields
	 */
	public void scanSubDeclarations(ParseTree tree, String indent){
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof MethodDeclarationContext){
				MethodDeclarationContext methodDecl = (MethodDeclarationContext) c;
				scanStatements(methodDecl, indent + "  ");
			} else if (c instanceof ConstructorDeclarationContext){
				ConstructorDeclarationContext ctorDecl = (ConstructorDeclarationContext) c;
				scanStatements(ctorDecl, indent + "  ");
			} else if (c instanceof FieldDeclarationContext){
				// Field decl is safe
			}
			scanSubDeclarations(c, indent);
		}
	}

	private void scanStatements(ParseTree tree, String indent) {
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof ConstructorBodyContext){
				ConstructorBodyContext ctorBody = (ConstructorBodyContext) c;
				for (BlockStatementContext stmt : getStatements(ctorBody)){
					scanExpressions(stmt, indent);
				}
			}
		}
	}

	private void scanExpressions(BlockStatementContext stmt, String indent) {
		for (ExpressionContext expr : getExpressions((BlockStatementContext)stmt)){
			if (isAssignment(expr)){
				ExpressionContext rhs = (ExpressionContext) expr.getChild(2);
				if(rhs.getText().equals("this") || isMethodCallPassingThis(rhs)){
					failureSet.add(new Failure(expr, FailureType.STORING_THIS));
				} else if (isDownCall(rhs)){
					failureSet.add(new Failure(expr, FailureType.DOWN_CALL));
				}
			}
			else if (isDownCall(expr)) {
				failureSet.add(new Failure(expr, FailureType.DOWN_CALL));
			}
			else if (isMethodCallPassingThis(expr)){
				failureSet.add(new Failure(expr, FailureType.STORING_THIS));
			}
		}
	}

	private boolean isMethodCallPassingThis(ExpressionContext expr) {
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

	private boolean isDownCall(ExpressionContext expr) {
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
	
	private boolean isAssignment(ExpressionContext expr) {
		return expr.getChildCount() > 1
				&& expr.getChild(1) instanceof TerminalNodeImpl
				&& ((TerminalNodeImpl)expr.getChild(1)).symbol.getText().equals("=");
	}

	private List<BlockStatementContext> getStatements(ParseTree tree){
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
	
	private List<ExpressionContext> getExpressions(BlockStatementContext stmt){
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
	
	private List<ParseTree> childrenOf(ParseTree tree){
		return IntStream
			.range(0, tree.getChildCount())
			.mapToObj(i -> tree.getChild(i))
			.collect(Collectors.toList());
	}

	private CompilationUnitContext getCompilationUnit(Path path) {
		try {
			String wholeFile = readFile(path);
			if (wholeFile == null) return null;
			hasForwarding = forwarding.matcher(wholeFile).find();
			
			CharStream in = new ANTLRInputStream(wholeFile);
		    JavaLexer lexer = new JavaLexer(in);
		    lexer.removeErrorListeners();
		    CommonTokenStream tokens = new CommonTokenStream(lexer);
		    JavaParser parser = new JavaParser(tokens);
		    parser.removeErrorListeners();
		    return parser.compilationUnit();
		} catch (Exception e) {
//			e.printStackTrace();
			return null;//throw new RuntimeException("Could not read file");
		}
	}
	
	private String readFile(Path path){
		List<Charset> charsetsToTry = Arrays.asList(StandardCharsets.UTF_8, StandardCharsets.UTF_16,
				StandardCharsets.UTF_16BE, StandardCharsets.UTF_16LE, StandardCharsets.US_ASCII);
		
		for (Charset charset : charsetsToTry){
			String file = tryReadFile(path, charset);
			if (file != null) return file;
		}
		return null;
	}
	
	private String tryReadFile(Path path, Charset encoding){
		try {
			byte[] encoded = Files.readAllBytes(path);
			return new String(encoded, encoding);
		} 
		catch (IOException e) {
			return null;
		}
	}
}
