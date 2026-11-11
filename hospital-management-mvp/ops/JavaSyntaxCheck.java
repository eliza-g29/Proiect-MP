import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Parses project Java sources with the JDK, without resolving external dependencies. */
public class JavaSyntaxCheck {
  public static void main(String[] args) throws Exception {
    Path root=Path.of(".").toRealPath();List<Path> sources=new ArrayList<>();
    for(String arg:args) {
      Path source=Path.of(arg);
      if(Files.isDirectory(source)) {
        try(var paths=Files.walk(source)) {paths.filter(p->p.toString().endsWith(".java")).forEach(sources::add);}
      } else sources.add(source);
    }
    for(Path source:sources)if(!source.toRealPath().startsWith(root))throw new IllegalArgumentException("Source outside project: "+source);
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
    if(compiler==null)throw new IllegalStateException("JDK 21 or newer required");
    DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
    try(var files=compiler.getStandardFileManager(diagnostics,null,java.nio.charset.StandardCharsets.UTF_8)) {
      files.setLocationFromPaths(StandardLocation.CLASS_PATH,List.of());
      var task=(JavacTask)compiler.getTask(null,files,diagnostics,List.of("-proc:none","--release","21"),null,files.getJavaFileObjectsFromPaths(sources));
      task.parse();
      boolean failed=false;
      for(var d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR){System.err.println(d);failed=true;}
      if(failed)System.exit(1);
      System.out.println("Java syntax OK: "+sources.size()+" source files. External types were not checked.");
    }
  }
}
