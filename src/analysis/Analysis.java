package analysis;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class Analysis {
	public static void main(String[] args) throws IOException {
		scanCorpus();
//		scanTestFile();
	}

	private static void scanCorpus() {
		long start = System.currentTimeMillis();
		Corpus projects = new Corpus("/Users/lukeinkster/Documents/QualitasCorpus-20130901r/Systems", 0, 50);
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
}