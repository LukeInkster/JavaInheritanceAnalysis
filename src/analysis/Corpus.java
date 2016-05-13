package analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Corpus {
	private List<Project> projects;
	
	public Corpus(String location, int start, int end){
		projects = Arrays
			.stream(new File(location).listFiles())
			.filter(file -> file.isDirectory())
			.map(directory -> directory.toPath())
			.filter(path -> containsJavaFiles(path))
			.skip(start)
			.limit(end)
//			.parallel()
			.map(path -> Project.from(path))
			.filter(project -> project != null)
			.collect(Collectors.toList());
	}
	
	public int size(){
		return projects.size();
	}
	
	public long countExtends(){
		return projects
			.stream()
			.mapToLong(project -> project.extendsClassCount())
			.sum();
	}
	
	public long countExtended(){
		return projects
			.stream()
			.mapToLong(project -> project.extendedClassCount())
			.sum();
	}

	public int countFiles() {
		return projects
			.stream()
			.mapToInt(project -> project.units.size())
			.sum();
	}

	public int countClasses() {
		return projects
			.stream()
			.flatMap(project -> project.units.stream())
			.mapToInt(unit -> unit.classCount)
			.sum();
	}

	public long countClassesWithFailures() {
		return projects
			.stream()
			.mapToLong(project -> project.failures().filter(failure -> !failure.failures.isEmpty()).count())
			.sum();
	}
	
	public List<FailureSet> failures(){
		return projects
			.stream()
			.flatMap(project -> project.failures())
			.collect(Collectors.toList());
	}

	public long countClassesWithDowncallFailures() {
		return failures()
			.stream()
			.filter(failureSet -> failureSet
				.failures
				.stream()
				.anyMatch(x -> x.failureType == FailureType.DOWN_CALL))
			.count();
	}

	public long countClassesWithStoringThisFailures() {
		return failures()
			.stream()
			.filter(failureSet -> failureSet
				.failures
				.stream()
				.anyMatch(x -> x.failureType == FailureType.STORING_THIS))
			.count();
	}
	
	private static boolean containsJavaFiles(Path path) {
		try {
			return Files
				.walk(path)
				.filter(f -> f.toString().endsWith(".java"))
				.count() > 0;
		} catch (IOException e) {
			return false;
		}
	}
}
