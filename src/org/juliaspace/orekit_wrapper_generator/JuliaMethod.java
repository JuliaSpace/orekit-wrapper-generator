package org.juliaspace.orekit_wrapper_generator;

import com.google.common.base.Joiner;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

public class JuliaMethod {
    private final String body;
    private final List<String> imports = new ArrayList<>();
    private final String name;

    public JuliaMethod(Executable exe) {
        boolean isMethod = exe instanceof Method;
        boolean isStatic = isMethod && Modifier.isStatic(exe.getModifiers());
        this.name = Namer.name(exe);
        Class<?> cls = exe.getDeclaringClass();
        JuliaType clsType = new JuliaType(cls);
        imports.addAll(clsType.getImports());
        String className = Namer.name(cls);
        Map<String, JuliaType> params = Arrays.stream(exe.getParameters())
                .collect(Collectors.toMap(Parameter::getName, p -> new JuliaType(p.getType()),
                        (o1, o2) -> o1, TreeMap::new));
        params.values()
                .forEach(p -> imports.addAll(p.getImports()));
        Joiner.MapJoiner sigJoiner = Joiner.on(", ").withKeyValueSeparator("::");
        String signature = sigJoiner.join(params);
        JuliaType returnType = null;
        if (isMethod) {
            String objSig;
            if (!isStatic) {
                objSig = "obj::" + className;
            } else {
                objSig = "::Type{" + className + "}";
            }
            signature = signature.isEmpty() ? objSig : objSig + ", " + signature;
            returnType = new JuliaType(((Method) exe).getReturnType());
            imports.addAll(returnType.getImports());
        }
        Joiner tupleJoiner = Joiner.on(", ");
        String types = tupleJoiner.join(params.values());
        if (!types.isEmpty() && !types.contains(",")) {
            types += ",";
        }
        String arguments = tupleJoiner.join(params.keySet());
        StringBuilder sb = new StringBuilder();
        sb.append("function ")
                .append(name)
                .append("(")
                .append(signature)
                .append(")\n    ");
        if (isMethod) {
            sb.append("return jcall(");
            if (!isStatic) {
                sb.append("obj, \"");
            } else {
                sb.append(className).append(", \"");
            }
            sb.append(exe.getName())
                    .append("\", ")
                    .append(returnType)
                    .append(", (");
        } else {
            sb.append("return ").append(name).append("((");
        }
        if (!types.isEmpty()) {
            sb.append(types);
        }
        sb.append(")");
        if (!arguments.isEmpty()) {
            sb.append(", ").append(arguments);
        }
        sb.append(")\nend\n\n");
        body = sb.toString();
    }

    public static boolean isWrappable(Executable exe) {
        return Modifier.isPublic(exe.getModifiers()) &&
                !exe.isAnnotationPresent(Deprecated.class);
    }

    public static boolean isWrappable(Method method) {
        return isWrappable((Executable) method) && !method.isBridge();
    }

    public String getBody() {
        return body;
    }

    public List<String> getImports() {
        return imports;
    }

    public String getName() {
        return name;
    }
}
