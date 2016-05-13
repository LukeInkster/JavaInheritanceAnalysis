package analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Project{
	// Set in the constructor
	public int fileCount = 0;
	public int classCount = 0;
	
	public final List<Unit> units;
	
	public long extendedClassCount(){
		return units
			.stream()
			.map(unit -> unit.extendsClass)
			.filter(superClass -> superClass != null)
			.distinct()
			.count();
	}
	
	public long extendsClassCount(){
		return units
			.stream()
			.filter(unit -> unit.extendsClass != null)
			.count();
	}
	
	public Stream<FailureSet> failures(){
		return units.stream().map(unit -> unit.failureSet);
	}
	
	public static Project from(Path path){
		List<Path> javaFiles = javaFilesIn(path);
		if (javaFiles.isEmpty()) return null;
		System.out.println(path);
		return new Project(
			javaFiles.size(),
			javaFiles
				.stream()
				.map(f -> new Unit(f))
				.filter(unit -> unit.compiled)
			);
	}

	public static Project from(File file) {
		Path p = file.toPath();

		return new Project(1, Stream.of(new Unit(p)));
	}

	public Project(int fileCount, Stream<Unit> units){
		this.fileCount = fileCount;
		this.units = units.collect(Collectors.toList());
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
}
