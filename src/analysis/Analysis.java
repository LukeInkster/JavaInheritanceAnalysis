package analysis;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
import antlr.JavaParser.MethodDeclarationContext;
import antlr.JavaParser.PrimaryContext;
import antlr.JavaParser.StatementContext;
import antlr.JavaParser.StatementExpressionContext;

public class Analysis {
	private static final int limit = 20;
	private static final String PASS = "\t\tPASS";
	private static final String FAIL = "\t\tFAIL";
	private static int breaks = 0;

	public static void main(String[] args) throws IOException {
		List<Path> projects = getProjects("/Users/lukeinkster/Documents/QualitasCorpus-20130901r/Systems");
		System.out.println("Found " + projects.size() + " projects.");
		
		printTotalFileCount(projects);
		printFailures(projects);
		countExtends(projects);
	}
	
	private static void printTotalFileCount(List<Path> projects) throws IOException{
		 long fileCount = projects
				.stream()
				.map(path -> Project.from(path))
				.mapToLong(project -> project
					.streamUnits()
					.count()
				).sum();
		
		System.out.println(fileCount + " total files");
	}

	private static List<Path> getProjects(String file) throws IOException {
		return Arrays
			.stream(new File(file).listFiles())
			.filter(f -> f.isDirectory())
			.map(f -> f.toPath())
			.limit(limit)
			.collect(Collectors.toList());
	}

	private static void countExtends(List<Path> projects) throws IOException {
		long extendsCount = 0;

		for (Path path : projects){
			extendsCount += Project.from(path)
				.streamUnits()
				.filter(cu -> extendsSomething(cu))
				.count();
		}
		
		System.out.println(extendsCount + " classes extend another class");
		//extendsSomething(compilationUnit(new File("data/extendsB.txt")));
	}

	private static void printFailures(List<Path> projects) throws IOException {
		List<FailureSet> failures = new ArrayList<FailureSet>();
		for (Path path : projects){
			failures.addAll(
					Project.from(path)
					.streamEntries()
					.map(entry -> new FailureSet(entry.getKey(), getFailures(entry.getValue())))
					.filter(fs -> !fs.failures.isEmpty())
					.collect(Collectors.toList())
				);
		}

		//System.out.println("Parser broke on " + breaks + " files");
		System.out.println("Found " + failures.size() + " failures:");
		failures.forEach(f -> System.out.println(f));
	}

	private static boolean extendsSomething(ParseTree tree) {
		for (int i=0; i<tree.getChildCount(); i++){
			ParseTree c = tree.getChild(i);
			if (c instanceof ClassDeclarationContext){
				ClassDeclarationContext classDecl = (ClassDeclarationContext) c;
				if (classDecl.children.stream().anyMatch(child -> child.getText().equals("extends"))) return true;
			}
			else if (extendsSomething(c)) return true;
		}
		return false;
	}

	static CompilationUnitContext getCompilationUnit(Path path) {
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

	private static Stream<ExpressionContext> getFailures(CompilationUnitContext compilationUnit) {
		try {
	        List<ExpressionContext> failures = listClasses(compilationUnit, "");
	        return failures.stream();
		}
		catch (Exception e) { 
			breaks++;
			return Stream.empty(); 
		}
	}

	public static List<ExpressionContext> listClasses(ParseTree tree, String indent){
		List<ExpressionContext> failures = new ArrayList<ExpressionContext>();
		for (int i=0; i<tree.getChildCount(); i++){
			ParseTree c = tree.getChild(i);
			if (c instanceof ClassDeclarationContext){
				ClassDeclarationContext classDecl = (ClassDeclarationContext) c;
				print("class:" + indent + classDecl.getText());
				failures.addAll(listSubDeclarations(classDecl, indent + "  "));
			}
			else failures.addAll(listClasses(c, indent + "  "));
		}
		return failures;
	}
	
	public static List<ExpressionContext> listSubDeclarations(ParseTree tree, String indent){
		List<ExpressionContext> failures = new ArrayList<ExpressionContext>();
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

	private static List<ExpressionContext> listStatements(ParseTree tree, String indent) {
		List<ExpressionContext> failures = new ArrayList<ExpressionContext>();
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
	
	private static List<ExpressionContext> listExpressions(BlockStatementContext stmt, String indent) {
		List<ExpressionContext> failures = new ArrayList<ExpressionContext>();
		for (ExpressionContext expr : getExpressions((BlockStatementContext)stmt)){
			if (isAssignment(expr)){
				ExpressionContext rhs = (ExpressionContext) expr.getChild(2);
				if(rhs.getText().equals("this")){
					failures.add(expr);
					print("stmnt:" + indent + expr.getText() + FAIL);
				} else if (rhs.getChild(0) instanceof ExpressionContext
						&& isSelfMethodCall((ExpressionContext)rhs.getChild(0))){
					failures.add(expr);
					print("stmnt:" + indent + expr.getText() + FAIL);
				}
				else print("stmnt:" + indent + expr.getText() + PASS);
			}
			else print("stmnt:" + indent + expr.getText() + PASS);
		}
		return failures;
	}

	private static boolean isSelfMethodCall(ExpressionContext expr) {
		return expr.getChild(0) instanceof PrimaryContext;
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
		return ((StatementContext)stmt.getChild(0))
				.children
				.stream()
				.filter(x -> x instanceof StatementExpressionContext)
				.map(x -> (ExpressionContext)x.getChild(0))
				.collect(Collectors.toList());
	}
	
	private static List<ParseTree> childrenOf(ParseTree tree){
		return IntStream
				.range(0, tree.getChildCount())
				.mapToObj(i -> tree.getChild(i))
				.collect(Collectors.toList());
	}

	private static void print(String s) {
		boolean print = false;
		if (print) System.out.println(s);
	}
}





//passes this in constructor or stores in field
// calls method on this