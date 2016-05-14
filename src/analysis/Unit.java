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
import org.antlr.v4.runtime.tree.ErrorNodeImpl;
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
import antlr.JavaParser.ExpressionListContext;
import antlr.JavaParser.FieldDeclarationContext;
import antlr.JavaParser.LocalVariableDeclarationStatementContext;
import antlr.JavaParser.MethodBodyContext;
import antlr.JavaParser.MethodDeclarationContext;
import antlr.JavaParser.StatementContext;
import antlr.JavaParser.StatementExpressionContext;
import antlr.JavaParser.TypeContext;
import antlr.JavaParser.TypeDeclarationContext;

public class Unit {
	public final Path path;
	public final boolean compiled;
	
	public int classCount = 0;
	public String superClassName;
	public FailureSet failureSet; 
	public String className;
	public boolean hasForwarding;
	public List<ExpressionContext> delegationStatements = new ArrayList<ExpressionContext>();
	
	public static final Pattern forwarding =
			Pattern.compile("[\\w<>]+\\s+(\\w+)\\s*\\(.*\\)\\s*\\{\\s*return\\s+\\w+(\\.\\w+)*\\.\\1\\(.*\\)\\s*;\\s*\\}");
	
	private static final List<String> classDeclKeywords = 
			Arrays.asList("public", "private", "abstract", "protected", "static", "final", "strictfp", "class");
	
	public Unit(Path path){
		System.out.println(path);
		this.path = path;
		if (path.toString().endsWith("sunflow/src/org/sunflow/math/PerlinScalar.java")){
			compiled = false;
			return;
		}
		CompilationUnitContext compilationUnit = getCompilationUnit(path);
		System.out.println("ping");
		this.compiled = compilationUnit != null;
		if (!this.compiled) return;
		this.failureSet = new FailureSet(path);
		findClassnameAndExtension(compilationUnit);
		scanClasses(compilationUnit);
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

	public void scanClasses(ParseTree tree){
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof ClassDeclarationContext){
				classCount++;
				ClassDeclarationContext classDecl = (ClassDeclarationContext) c;
				scanSubDeclarations(classDecl);
			}
			else scanClasses(c);
		}
	}

	/**
	 * Go through all sub-class-declarations -> methods, constructors and fields
	 */
	public void scanSubDeclarations(ParseTree tree){
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof MethodDeclarationContext){
				MethodDeclarationContext methodDecl = (MethodDeclarationContext) c;
				scanMthdStatements(methodDecl);
			} else if (c instanceof ConstructorDeclarationContext){
				ConstructorDeclarationContext ctorDecl = (ConstructorDeclarationContext) c;
				scanCtorStatements(ctorDecl);
			} else if (c instanceof FieldDeclarationContext){
				// Field decl is safe
			}
			scanSubDeclarations(c);
		}
	}

	private void scanMthdStatements(MethodDeclarationContext tree) {
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof MethodBodyContext){
				MethodBodyContext mthdBody = (MethodBodyContext) c;
				for (BlockStatementContext stmt : getStatements(mthdBody)){
					scanMthdExpressions(stmt);
				}
			}
		}
	}

	private void scanMthdExpressions(BlockStatementContext tree) {
		delegationStatements
			.addAll(getExpressions(tree)
				.stream()
				.filter(this::mthdExpressionIsDelegation)
				.collect(Collectors.toList())
			);
	}

	private boolean mthdExpressionIsDelegation(ExpressionContext expr) {
		if (isAssignment(expr)){
			return mthdExpressionIsDelegation((ExpressionContext)expr.getChild(2)); //look at rhs of assignment
		}
		else if (expr.getChildCount() == 4
				&& expr.getChild(0).getText().equals("(")
				&& expr.getChild(1) instanceof TypeContext){
			return mthdExpressionIsDelegation((ExpressionContext)expr.getChild(3)); //look at rhs of cast
		}
		// [identifier][(][parameters][)] == 4 components
		else if (expr.getChildCount() == 4 && !(expr.getChild(0) instanceof ErrorNodeImpl)) {
//			System.out.println(expr.children.stream().map(c -> c.getClass().getName()).collect(Collectors.joining("  -  ")));
			ExpressionContext identifier = (ExpressionContext)expr.getChild(0);
			if (identifier.getChildCount() <= 1) {
				return false; //can't be calling another class with one or fewer identifier components
			}
			if (identifier.getChildCount() <= 3 && identifier.getChild(0).getText().equals("this")) { 
				return false; //calling method on this is fine
			}
			if (expr.getChild(2) instanceof ExpressionListContext){
				ExpressionListContext params = (ExpressionListContext)expr.getChild(2);
				return params.children.stream().anyMatch(p -> p.getText().equals("this"));
			}
			else if (expr.getChild(2) instanceof ExpressionContext){
				ExpressionContext params = (ExpressionContext)expr.getChild(2);
				return params.getText().equals("this");
			}
			return false;
		}
		else return false;
	}

	private void scanCtorStatements(ConstructorDeclarationContext tree) {
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof ConstructorBodyContext){
				ConstructorBodyContext ctorBody = (ConstructorBodyContext) c;
				for (BlockStatementContext stmt : getStatements(ctorBody)){
					scanCtorExpressions(stmt);
				}
			}
		}
	}

	private void scanCtorExpressions(BlockStatementContext stmt) {
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
