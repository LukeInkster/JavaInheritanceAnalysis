package analysis;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;

import antlr.JavaLexer;
import antlr.JavaParser;
import antlr.JavaParser.CompilationUnitContext;

public class Analysis {
	private static final int limit = 100;
//	private static PrintWriter writer;

	public static void main(String[] args) throws IOException {
		long start = System.currentTimeMillis();
		ProjectSet projects = new ProjectSet("/Users/lukeinkster/Documents/QualitasCorpus-20130901r/Systems", limit);
		System.out.println("Found " + projects.size() + " projects.");
//		writer = new PrintWriter("output/failures.txt", "UTF-8");
		
		Project p = Project.from(new File("data/selfMethodCalls.txt"));
		System.out.println(p.failures.size());
		System.out.println(p.failures.stream().flatMap(f -> f.failures.stream()).map(x -> x.getText()).collect(Collectors.toList()));
		
		System.out.println(projects.countFiles() + " total files");
		System.out.println(projects.countClasses() + " total classes");
		System.out.println(projects.countFailures() + " total constructor issues");
//		projects.failures().forEach(f -> System.out.println(f));

		System.out.println(projects.countExtends() + " classes extend another class");
		System.out.println(projects.countExtended() + " classes are extended by another class");
		System.out.println("Took " + (System.currentTimeMillis() - start) + "ms");
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
}





//passes this in constructor or stores in field
// calls method on this