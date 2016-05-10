package analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import antlr.JavaParser.CompilationUnitContext;

public class Project implements Iterable<CompilationUnitContext>{
	public final Map<Path, CompilationUnitContext> compilationUnits;
	public final Set<String> extendedClasses = new HashSet<String>();
	
	public static Project from(Path path){
		try {
			return new Project(
					Files
					.walk(path)
					.filter(f -> f.toString().endsWith(".java"))
					.map(f -> new Pair<Path, CompilationUnitContext>(f, Analysis.getCompilationUnit(f)))
					.filter(pair -> pair.second() != null)
					.collect(Collectors.toMap(pair -> pair.first(), pair -> pair.second()))
				);
		} catch (IOException e) {
			return null;
		}
	}

	public static Project from(File file) {
		Path p = file.toPath();
		System.out.println(p);
		Map<Path, CompilationUnitContext> map = new HashMap<Path, CompilationUnitContext>();
		map.put(p, Analysis.getCompilationUnit(p));
		return new Project(map);
	}
	
	public Stream<CompilationUnitContext> streamUnits(){
		return compilationUnits.values().stream();
	}
	
	public Stream<Map.Entry<Path, CompilationUnitContext>> streamEntries(){
		return compilationUnits.entrySet().stream();
	}
	
	public Project(Map<Path, CompilationUnitContext> compilationUnits){
		this.compilationUnits = compilationUnits;
	}
	
	public CompilationUnitContext singleUnit(){
		return new ArrayList<CompilationUnitContext>(compilationUnits.values()).get(0);
	}
	
	@Override
	public Iterator<CompilationUnitContext> iterator() {
		return compilationUnits.values().iterator();
	}
}
