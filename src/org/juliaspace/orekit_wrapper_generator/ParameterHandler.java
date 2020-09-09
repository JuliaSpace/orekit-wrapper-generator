package org.juliaspace.orekit_wrapper_generator;

import com.google.common.base.Joiner;

import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;

public class ParameterHandler {

    private final Map<String, String> newImports = new HashMap<>();
    private final String signature;
    private final String types;
    private final String args;

    public Map<String, String> getNewImports() {
        return newImports;
    }

    public String getSignature() {
        return signature;
    }

    public String getTypes() {
        return types;
    }

    public String getArgs() {
        return args;
    }

    public ParameterHandler(Parameter[] parameters, final Map<String, String> imports) {
        Map<String, String> params = new LinkedHashMap<>();
        for (Parameter parameter : parameters) {
            Class<?> type = parameter.getType();
            String name = type.getSimpleName();
            boolean imported = imports.containsKey(type.getSimpleName());
            if (type.isArray()) {
                Class<?> eltype = type.getComponentType();
                if (!imports.containsKey(eltype.getSimpleName()) && isNotPrimitive(eltype)) {
                    newImports.put(eltype.getSimpleName(), eltype.getName());
                }
            } else if (type.getEnclosingClass() != null && Modifier.isStatic(type.getModifiers())) {
                String parentName = type.getEnclosingClass().getSimpleName();
                name = parentName + "_" + name;
                if (!imports.containsKey(name)) {
                    newImports.put(name, type.getName());
                }
            } else if (!imported && isNotPrimitive(type)) {
                newImports.put(type.getSimpleName(), type.getName());
            }
            String typeName = translateName(name);
            params.put(parameter.getName(), typeName);
        }
        Joiner.MapJoiner sigJoiner = Joiner.on(", ").withKeyValueSeparator("::");
        signature = sigJoiner.join(params);
        Joiner tupJoiner = Joiner.on(", ");
        String types1 = tupJoiner.join(params.values());
        if (!types1.contains(",") && !types1.isEmpty()) {
            types = types1 + ",";
        } else {
            types = types1;
        }
        args = tupJoiner.join(params.keySet());
    }

    public static String translateName(String in) {
        if (in.endsWith("[]")) return translateArrayName(in);

        return switch (in) {
            case "byte" -> "jbyte";
            case "boolean" -> "jboolean";
            case "char" -> "jchar";
            case "int" -> "jint";
            case "long" -> "jlong";
            case "float" -> "jfloat";
            case "double" -> "jdouble";
            case "String" -> "JString";
            case "Complex" -> "JComplex";
            default -> in;
        };
    }

    private static String translateArrayName(String in) {
        boolean isMatrix = in.endsWith("[][]");
        String type = in.replaceAll("[\\[\\]]", "");
        String jType = translateName(type);
        StringBuilder buf = new StringBuilder();
        if (isMatrix) {
            buf.append("Vector{Vector{").append(jType).append("}}");
        } else {
            buf.append("Vector{").append(jType).append("}");
        }
        return buf.toString();
    }

    public static boolean isNotPrimitive(Class<?> type) {
        return !type.isPrimitive() && !type.isArray();
    }
}
