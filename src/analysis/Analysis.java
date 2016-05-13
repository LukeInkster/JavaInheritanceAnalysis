package analysis;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;

import antlr.JavaLexer;
import antlr.JavaParser;
import antlr.JavaParser.CompilationUnitContext;

public class Analysis {
	private static final int limit = 1;

	public static void main(String[] args) throws IOException {
		scanCorpus();
//		scanTestFile();
	}

	private static void scanCorpus() {
		long start = System.currentTimeMillis();
		Corpus projects = new Corpus("/Users/lukeinkster/Documents/QualitasCorpus-20130901r/Systems", limit);
		System.out.println("Found " + projects.size() + " projects.");
		
		System.out.println(projects.countFiles() + " total files");
		System.out.println(projects.countClasses() + " total classes");
		System.out.println(projects.countClassesWithFailures() + " classes with constructor issues");
		System.out.println(projects.countClassesWithDowncallFailures() + " classes with downcalls in constructors");
		System.out.println(projects.countClassesWithStoringThisFailures() + " classes storing this in constructors");

		System.out.println(projects.countExtends() + " classes extend another class");
		System.out.println(projects.countExtended() + " classes are extended by another class");
		writeErrorsToFile(projects);
		System.out.println("Took " + (System.currentTimeMillis() - start) + "ms");
	}

	private static void scanTestFile() {
		Project p = Project.from(new File("data/storingThis.txt"));
		p.failures().forEach(failureSet -> System.out.println(failureSet));
//		System.out.println(p.failures.size());
//		System.out.println(p.failures.stream().flatMap(f -> f.failures.stream()).map(x -> x.text()).collect(Collectors.toList()));
	}

	private static void writeErrorsToFile(Corpus projects) {
		try {
			PrintWriter writer = new PrintWriter("output/failures.txt", "UTF-8");
			projects.failures().forEach(f -> writer.println(f));
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
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