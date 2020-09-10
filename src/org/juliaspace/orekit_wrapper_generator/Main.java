package org.juliaspace.orekit_wrapper_generator;

import com.google.common.reflect.ClassPath;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        List<URL> jars = new ArrayList<>();
        for (String arg : args) {
            try {
                jars.add(Paths.get(arg).toUri().toURL());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        URLClassLoader cl = new URLClassLoader(jars.toArray(URL[]::new));
        ClassPath cp = ClassPath.from(cl);
        JuliaModule orekit = new JuliaModule(cp, "org.orekit");
        JuliaModule hipparchus = new JuliaModule(cp, "org.hipparchus");

        Path root = Paths.get("gen");
        FileUtils.deleteDirectory(root.toFile());
        Files.createDirectories(root);
        orekit.write(root);
        hipparchus.write(root);
    }
}
