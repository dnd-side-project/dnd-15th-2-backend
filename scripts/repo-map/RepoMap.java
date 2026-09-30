import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Comparator;
import javax.lang.model.element.Modifier;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/** Parse-only Java declarations. Never analyzes, generates, or visits executable bodies. */
class RepoMap {
    static String type(Tree tree) {
        if (tree == null) return "";
        return switch (tree.getKind()) {
            case IDENTIFIER -> ((IdentifierTree) tree).getName().toString();
            case MEMBER_SELECT -> type(((MemberSelectTree) tree).getExpression()) + "." + ((MemberSelectTree) tree).getIdentifier();
            case PRIMITIVE_TYPE -> ((PrimitiveTypeTree) tree).getPrimitiveTypeKind().name().toLowerCase(java.util.Locale.ROOT);
            case ARRAY_TYPE -> type(((ArrayTypeTree) tree).getType()) + "[]";
            case PARAMETERIZED_TYPE -> type(((ParameterizedTypeTree) tree).getType()) + "<" + join(((ParameterizedTypeTree) tree).getTypeArguments(), ",") + ">";
            case ANNOTATED_TYPE -> type(((AnnotatedTypeTree) tree).getUnderlyingType());
            case UNBOUNDED_WILDCARD -> "?";
            case EXTENDS_WILDCARD -> "? extends " + type(((WildcardTree) tree).getBound());
            case SUPER_WILDCARD -> "? super " + type(((WildcardTree) tree).getBound());
            case INTERSECTION_TYPE -> join(((IntersectionTypeTree) tree).getBounds(), " & ");
            case UNION_TYPE -> join(((UnionTypeTree) tree).getTypeAlternatives(), " | ");
            default -> throw new IllegalArgumentException("unsupported type syntax");
        };
    }

    static String join(List<? extends Tree> trees, String delimiter) {
        return trees.stream().map(RepoMap::type).collect(Collectors.joining(delimiter));
    }

    static String parameters(List<? extends TypeParameterTree> parameters) {
        if (parameters.isEmpty()) return "";
        return "<" + parameters.stream().map(p -> p.getName() + (p.getBounds().isEmpty() ? "" : " extends " + join(p.getBounds(), " & "))).collect(Collectors.joining(",")) + ">";
    }

    static Map<String, Object> row(String file, String owner, String kind, String symbol, String signature, long line) {
        var row = new LinkedHashMap<String, Object>();
        row.put("file", file); row.put("type", owner); row.put("kind", kind);
        row.put("symbol", symbol); row.put("signature", signature); row.put("line", line);
        return row;
    }

    static String named(Tree tree) {
        return switch (tree.getKind()) {
            case IDENTIFIER -> ((IdentifierTree) tree).getName().toString();
            case MEMBER_SELECT -> named(((MemberSelectTree) tree).getExpression()) + "." + ((MemberSelectTree) tree).getIdentifier();
            case PARAMETERIZED_TYPE -> named(((ParameterizedTypeTree) tree).getType());
            case ANNOTATED_TYPE -> named(((AnnotatedTypeTree) tree).getUnderlyingType());
            default -> throw new IllegalArgumentException("unsupported named type");
        };
    }

    // A member type may carry arguments on its owner, e.g. Outer<Value>.Inner<Port>.
    static void ownerArguments(Tree tree, Set<String> parameters, Set<String> names) {
        if (tree instanceof ParameterizedTypeTree parameterized) {
            parameterized.getTypeArguments().forEach(t -> references(t, parameters, names));
            ownerArguments(parameterized.getType(), parameters, names);
        } else if (tree instanceof MemberSelectTree member) {
            ownerArguments(member.getExpression(), parameters, names);
        } else if (tree instanceof AnnotatedTypeTree annotated) {
            ownerArguments(annotated.getUnderlyingType(), parameters, names);
        }
    }

    static void references(Tree tree, Set<String> parameters, Set<String> names) {
        if (tree == null) return;
        switch (tree.getKind()) {
            case IDENTIFIER, MEMBER_SELECT -> {
                String name = named(tree);
                if (!parameters.contains(name)) names.add(name);
                ownerArguments(tree, parameters, names);
            }
            case PARAMETERIZED_TYPE -> {
                var parameterized = (ParameterizedTypeTree) tree;
                references(parameterized.getType(), parameters, names);
                parameterized.getTypeArguments().forEach(t -> references(t, parameters, names));
            }
            case ANNOTATED_TYPE -> references(((AnnotatedTypeTree) tree).getUnderlyingType(), parameters, names);
            case ARRAY_TYPE -> references(((ArrayTypeTree) tree).getType(), parameters, names);
            case EXTENDS_WILDCARD, SUPER_WILDCARD -> references(((WildcardTree) tree).getBound(), parameters, names);
            case INTERSECTION_TYPE -> ((IntersectionTypeTree) tree).getBounds().forEach(t -> references(t, parameters, names));
            case UNION_TYPE -> ((UnionTypeTree) tree).getTypeAlternatives().forEach(t -> references(t, parameters, names));
            case PRIMITIVE_TYPE, UNBOUNDED_WILDCARD -> { }
            default -> throw new IllegalArgumentException("unsupported reference syntax");
        }
    }

    static void dependency(VariableTree variable, String source, Set<String> parameters,
                           CompilationUnitTree unit, Trees trees, List<Map<String, Object>> evidence) {
        // Enum constant types are compiler-created, not a written type declaration.
        if (trees.getSourcePositions().getEndPosition(unit, variable.getType()) < 0) return;
        var names = new TreeSet<String>();
        references(variable.getType(), parameters, names);
        long line = unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit, variable));
        for (String name : names) {
            var item = new LinkedHashMap<String, Object>();
            item.put("dependency", name); item.put("source", source);
            item.put("name", variable.getName().toString()); item.put("line", line);
            if (!evidence.contains(item)) evidence.add(item);
        }
    }

    static void declaration(ClassTree node, String enclosing, CompilationUnitTree unit, Trees trees,
                            String file, List<Map<String, Object>> rows, Set<String> inheritedParameters) {
        String name = node.getSimpleName().toString();
        String owner = enclosing.isEmpty() ? name : enclosing + "." + name;
        long line = unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit, node));
        var row = row(file, owner, node.getKind().name().toLowerCase(java.util.Locale.ROOT), name,
                      owner + parameters(node.getTypeParameters()), line);
        row.put("extends", type(node.getExtendsClause()));
        row.put("implements", node.getImplementsClause().stream().map(RepoMap::type).toList());
        row.put("permits", node.getPermitsClause().stream().map(RepoMap::type).toList());
        var scopedParameters = new HashSet<String>(inheritedParameters);
        if (node.getModifiers().getFlags().contains(Modifier.STATIC)
                || Set.of(Tree.Kind.RECORD, Tree.Kind.ENUM, Tree.Kind.INTERFACE, Tree.Kind.ANNOTATION_TYPE).contains(node.getKind())) scopedParameters.clear();
        // A type and its member types shadow inherited parameters throughout the body,
        // even before the member declaration. Local class/constructor parameters win later.
        scopedParameters.remove(name);
        node.getMembers().stream().filter(ClassTree.class::isInstance).map(ClassTree.class::cast)
            .forEach(member -> scopedParameters.remove(member.getSimpleName().toString()));
        node.getTypeParameters().forEach(p -> scopedParameters.add(p.getName().toString()));
        var symbols = new TreeSet<String>();
        var evidence = new ArrayList<Map<String, Object>>();
        rows.add(row);
        for (Tree member : node.getMembers()) {
            if (member instanceof ClassTree nested) declaration(nested, owner, unit, trees, file, rows, scopedParameters);
            if (member instanceof VariableTree field) dependency(field, "field", scopedParameters, unit, trees, evidence);
            if (member instanceof MethodTree method) {
                if (method.getReturnType() != null) symbols.add(method.getName().toString());
                else {
                    var constructorParameters = new HashSet<String>(scopedParameters);
                    method.getTypeParameters().forEach(p -> constructorParameters.add(p.getName().toString()));
                    long start = trees.getSourcePositions().getStartPosition(unit, method);
                    for (var parameter : method.getParameters()) {
                        // Compact record constructor parameters come from the header, not written parameters.
                        if (trees.getSourcePositions().getStartPosition(unit, parameter) >= start)
                            dependency(parameter, "constructor_parameter", constructorParameters, unit, trees, evidence);
                    }
                }
                String methodName = method.getName().contentEquals("<init>") ? name : method.getName().toString();
                String signature = parameters(method.getTypeParameters()) + methodName + "(" + method.getParameters().stream()
                    .map(p -> type(p.getType())).collect(Collectors.joining(",")) + ")";
                if (method.getReturnType() != null) signature += ":" + type(method.getReturnType());
                if (!method.getThrows().isEmpty()) signature += " throws " + join(method.getThrows(), ",");
                long methodLine = unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit, method));
                rows.add(row(file, owner, "method", methodName, signature, methodLine));
            }
        }
        evidence.sort(Comparator.comparing((Map<String, Object> e) -> (String)e.get("dependency"))
            .thenComparing(e -> (String)e.get("source")).thenComparing(e -> (String)e.get("name"))
            .thenComparingLong(e -> (Long)e.get("line")));
        row.put("symbols", new ArrayList<>(symbols));
        row.put("dependencies", evidence.stream().map(e -> (String)e.get("dependency")).distinct().toList());
        row.put("dependency_evidence", evidence);
    }

    static String json(Object value) {
        if (value instanceof String string) {
            var out = new StringBuilder("\"");
            for (char c : string.toCharArray()) {
                if (c == '"' || c == '\\') out.append('\\').append(c);
                else if (c < 32) out.append(String.format("\\u%04x", (int)c));
                else out.append(c);
            }
            return out.append('"').toString();
        }
        if (value instanceof Map<?, ?> map) return "{" + map.entrySet().stream().map(e -> json(e.getKey()) + ":" + json(e.getValue())).collect(Collectors.joining(",")) + "}";
        if (value instanceof List<?> list) return "[" + list.stream().map(RepoMap::json).collect(Collectors.joining(",")) + "]";
        return String.valueOf(value);
    }

    public static void main(String[] args) {
        try {
            if (Runtime.version().feature() != 21) throw new IllegalArgumentException("JDK 21 required");
            var compiler = ToolProvider.getSystemJavaCompiler();
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            var rows = new ArrayList<Map<String, Object>>();
            Path root = Path.of(args[0]);
            var paths = new ArrayList<Path>();
            for (String name : new String(System.in.readAllBytes(), StandardCharsets.UTF_8).split("\u0000")) {
                if (!name.isEmpty()) paths.add(root.resolve(name));
            }
            try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                if (!paths.isEmpty()) {
                    var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                        List.of("-proc:none", "--release", "21", "-encoding", "UTF-8"), null, manager.getJavaFileObjectsFromPaths(paths));
                    var units = task.parse();
                    var trees = Trees.instance(task);
                    for (var unit : units) {
                        String file = root.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\', '/');
                        String pkg = type(unit.getPackageName());
                        var fileRow = row(file, "", "file", file, "", 1);
                        fileRow.put("package", pkg);
                        fileRow.put("imports", unit.getImports().stream().map(i -> (i.isStatic() ? "static " : "") + type(i.getQualifiedIdentifier())).toList());
                        rows.add(fileRow);
                        for (var node : unit.getTypeDecls()) if (node instanceof ClassTree c) declaration(c, pkg, unit, trees, file, rows, Set.of());
                    }
                    if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) throw new IllegalArgumentException("parse failed");
                }
            }
            System.out.println(json(rows));
        } catch (Exception error) {
            System.err.println("repo-map: Java parse failed (source diagnostics withheld)");
            System.exit(2);
        }
    }
}
