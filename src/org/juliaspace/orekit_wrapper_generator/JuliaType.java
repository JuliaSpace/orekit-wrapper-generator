package org.juliaspace.orekit_wrapper_generator;

import java.util.ArrayList;
import java.util.List;

public class JuliaType {
    private final String name;
    private final List<String> imports = new ArrayList<>();

    public JuliaType(Class<?> cls) {
        String javaName = cls.getSimpleName();
        if (cls.isArray()) {
            Class<?> eltype = cls.getComponentType();
            while (eltype.isArray()) {
                eltype = eltype.getComponentType();
            }
            addImport(eltype);
            name = Namer.translateArrayName(cls);
        } else if (cls.getEnclosingClass() != null) {
            String parentName = Namer.translateName(cls.getEnclosingClass());
            name = parentName + "_" + Namer.translateName(javaName);
            addImport(name, cls.getName());
        } else {
            name = Namer.translateName(javaName);
            addImport(cls);
        }
    }

    public String getName() {
        return name;
    }

    public List<String> getImports() {
        return imports;
    }

    private void addImport(Class<?> type) {
        if (type.isPrimitive()) return;
        addImport(Namer.translateName(type.getSimpleName()), type.getName());
    }

    private void addImport(String name, String javaName) {
        String importStmt = "const " +
                name +
                " = @jimport " +
                javaName;
        imports.add(importStmt);
    }

    public String toString() {
        return getName();
    }
}
