package analysis;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.antlr.v4.runtime.tree.Tree;

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

	private static final String PASS = "\t\tPASS";
	private static final String FAIL = "\t\tFAIL";

	public static void main(String[] args) {
		CharStream in = new ANTLRInputStream("public class A {"
				+ "private int x;"
				+ "private A me;"
				+ "public int f(int g){return g+1;}"
				+ "public int h(int i){return f(i);}"
				+ "public A(int jjj){x = jjj; me = this;}"
				+ "public A(int j, int k){x = f(j); me = null;}"
				+ "}");
        JavaLexer lexer = new JavaLexer(in);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        CompilationUnitContext compilationUnit = parser.compilationUnit();
        listClasses(compilationUnit, "");
	}
	
	public static void listClasses(ParseTree tree, String indent){
		for (int i=0; i<tree.getChildCount(); i++){
			ParseTree c = tree.getChild(i);
			if (c instanceof ClassDeclarationContext){
				ClassDeclarationContext classDecl = (ClassDeclarationContext) c;
				System.out.println("class:" + indent + classDecl.getText());
				listSubDeclarations(classDecl, indent + "  ");
			}
			else listClasses(c, indent + "  ");
		}
	}
	
	public static void listSubDeclarations(ParseTree tree, String indent){
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof MethodDeclarationContext){
				MethodDeclarationContext methodDecl = (MethodDeclarationContext) c;
				System.out.println("methd:" + indent + methodDecl.getText());
				listStatements(methodDecl, indent + "  ");
			} else if (c instanceof ConstructorDeclarationContext){
				ConstructorDeclarationContext ctorDecl = (ConstructorDeclarationContext) c;
				System.out.println("c-tor:" + indent + ctorDecl.getText());
				listStatements(ctorDecl, indent + "  ");
			} else if (c instanceof FieldDeclarationContext){
				FieldDeclarationContext fieldDecl = (FieldDeclarationContext) c;
				System.out.println("feild:" + indent + fieldDecl.getText());
			}
			listSubDeclarations(c, indent);
		}
	}

	private static void listStatements(ParseTree tree, String indent) {
		for (ParseTree c : childrenOf(tree)){
			if (c instanceof ConstructorBodyContext){
				ConstructorBodyContext ctorBody = (ConstructorBodyContext) c;
				for (BlockStatementContext stmt : getStatements(ctorBody)){
					listExpressions(stmt, indent);
				}
			}
		}	
	}
	
	private static void listExpressions(BlockStatementContext stmt, String indent) {
		for (ExpressionContext expr : getExpressions((BlockStatementContext)stmt)){
			if (isAssignment(expr)){
				ExpressionContext lhs = (ExpressionContext) expr.getChild(0);
				TerminalNodeImpl op  = (TerminalNodeImpl) expr.getChild(1);
				ExpressionContext rhs = (ExpressionContext) expr.getChild(2);
				if(rhs.getText().equals("this")){
					System.out.println("stmnt:" + indent + expr.getText() + FAIL);
				} else if (rhs.getChild(0) instanceof ExpressionContext
						&& isSelfMethodCall((ExpressionContext)rhs.getChild(0))){
					System.out.println("stmnt:" + indent + expr.getText() + FAIL);
				}
				else System.out.println("stmnt:" + indent + expr.getText() + PASS);
			}
			else System.out.println("stmnt:" + indent + expr.getText() + PASS);
		}
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
}

//passes this in constructor or stores in field
// calls method on this