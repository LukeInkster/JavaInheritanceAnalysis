package analysis;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Pattern;

public class Analysis {
	public static void main(String[] args) throws IOException {
		scanCorpus();
//		scanTestFile();
	}

	private static void scanCorpus() {
		long start = System.currentTimeMillis();
		Corpus corpus = new Corpus("/Users/lukeinkster/Documents/QualitasCorpus-20130901r/Systems", 0, 100);
		System.out.println("Found " + corpus.size() + " projects.");
		
		System.out.println(corpus.countFiles() + " - total files");
		System.out.println(corpus.countClasses() + " - total classes");

		System.out.println(corpus.countExtends() + " - classes extend another class");
		System.out.println(corpus.countExtended() + " - classes are extended by another class");
		System.out.println(corpus.countClassesWithForwarding() + " - classes with forwarding");
		System.out.println(corpus.countClassesWithForwardingThatExtend() + " - classes with forwarding that extend");
		
		System.out.println(corpus.countClassesWithFailures() + " - classes with downcalls or storing this in constructor");
		System.out.println(corpus.countClassesWithFailure(FailureType.DOWN_CALL) + " - classes with downcalls in constructors");
		System.out.println(corpus.countClassesWithFailure(FailureType.STORING_THIS) + " - classes storing this in constructors");
		System.out.println(corpus.countExtendedClassesWithFailure(FailureType.DOWN_CALL)
				+ " - extended classes with downcalls in constructors");
		System.out.println(corpus.countExtendedClassesWithFailure(FailureType.STORING_THIS)
				+ " - extended classes storing this in constructors");
//		writeErrorsToFile(projects);
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