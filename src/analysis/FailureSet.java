package analysis;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FailureSet {
	public final String filename;
	public final List<Failure> failures;
	
	public FailureSet(Path file, Stream<Failure> failures) {
		this.filename = file.toString();
		this.failures = failures.collect(Collectors.toList());
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder(filename);
		failures.forEach(f -> sb.append("\n  " + f.text()));
		return sb.toString();
	}
}
