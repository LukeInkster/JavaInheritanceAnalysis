package analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

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

public class Project{
	private static final String PASS = "\t\tPASS";
	private static final String FAIL = "\t\tFAIL";
	
	// Set in the constructor
	public int fileCount = 0;
	public int classCount = 0;
	
	public final Set<String> extendedClasses = new HashSet<String>();
	public final List<String> extendsClasses = new ArrayList<String>();
	public final List<FailureSet> failures = new ArrayList<FailureSet>();
	
	public static Project from(Path path){
		List<Path> javaFiles = javaFilesIn(path);
		if (javaFiles.isEmpty()) return null;
		
		System.out.println(javaFiles.size());
		
		return new Project(
			javaFiles
				.stream()
				.map(f -> new Pair<Path, CompilationUnitContext>(f, Analysis.getCompilationUnit(f)))
				.filter(pair -> pair.second() != null)
			);
	}

	public static Project from(File file) {
		Path p = file.toPath();

		return new Project(Stream.of(
				new Pair<Path, CompilationUnitContext>(p, Analysis.getCompilationUnit(p))));
	}

	public Project(Stream<Pair<Path, CompilationUnitContext>> compilationUnits){
		compilationUnits.forEach(unit ->{
			fileCount++;
			countExtensions(unit.second());
			countFailures(unit);
		});
	}

	private void countExtensions(ParseTree tree) {
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof ClassDeclarationContext){
				ClassDeclarationContext classDecl = (ClassDeclarationContext) c;
				for (int j=0; j<classDecl.getChildCount(); j++){
					if (classDecl.getChild(j).getText().equals("extends")){
						extendsClasses.add(classDecl.getChild(j-1).getText());
						extendedClasses.add(classDecl.getChild(j+1).getText());
					}
				}
			}
			else countExtensions(c);
		}
	}

	private void countFailures(Pair<Path, CompilationUnitContext> compilationUnit) {
        List<Failure> failures = listClasses(compilationUnit.second(), "");
        if (!failures.isEmpty()){
        	this.failures.add(new FailureSet(compilationUnit.first(), failures.stream()));
        }
	}

	public List<Failure> listClasses(ParseTree tree, String indent){
		List<Failure> failures = new ArrayList<Failure>();
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof ClassDeclarationContext){
				classCount++;
				ClassDeclarationContext classDecl = (ClassDeclarationContext) c;
				print("class:" + indent + classDecl.getText());
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
				print("methd:" + indent + methodDecl.getText());
				failures.addAll(listStatements(methodDecl, indent + "  "));
			} else if (c instanceof ConstructorDeclarationContext){
				ConstructorDeclarationContext ctorDecl = (ConstructorDeclarationContext) c;
				print("c-tor:" + indent + ctorDecl.getText());
				failures.addAll(listStatements(ctorDecl, indent + "  "));
			} else if (c instanceof FieldDeclarationContext){
				FieldDeclarationContext fieldDecl = (FieldDeclarationContext) c;
				print("field:" + indent + fieldDecl.getText());
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
			System.out.println(expr.getText());
			if (isAssignment(expr)){
				ExpressionContext rhs = (ExpressionContext) expr.getChild(2);
				if(rhs.getText().equals("this")){
					failures.add(new Failure(expr, FailureType.STORING_THIS));
					print("stmnt:" + indent + expr.getText() + FAIL);
				} else if (isSelfMethodCall(rhs)){
					failures.add(new Failure(expr, FailureType.DOWN_CALL));
					print("stmnt:" + indent + expr.getText() + FAIL);
				}
				else {
					print("stmnt:" + indent + expr.getText() + PASS);
				}
			}
			else if (isSelfMethodCall(expr)) {
				failures.add(new Failure(expr, FailureType.DOWN_CALL));
			} 
			else {
				print("stmnt:" + indent + expr.getText() + PASS);
			}
		}
		return failures;
	}

	private static boolean isSelfMethodCall(ExpressionContext expr) {
		if (expr.getChildCount() >= 2){
			// [notSuper][(]
			return !expr.getChild(0).getText().equals("super") &&
					expr.getChild(0).getChildCount() == 1 &&
					expr.getChild(1).getText().equals("(");
		}
		if (expr.getChildCount() >= 5){
			// [this][.][anything][(]
			return expr.getChild(0).getText().equals("this") && expr.getChild(3).getText().equals("(");
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
	
	private static List<Path> javaFilesIn(Path path) {
		try {
			return Files
				.walk(path)
				.filter(f -> f.toString().endsWith(".java"))
				.collect(Collectors.toList());
		} catch (IOException e) {
			return new ArrayList<Path>();
		}
	}

	private static void print(String s) {
		boolean print = false;
		if (print) System.out.println(s);
	}
}
