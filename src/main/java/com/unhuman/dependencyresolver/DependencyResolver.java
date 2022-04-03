package com.unhuman.dependencyresolver;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DependencyResolver {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String MVN_COMMAND = (IS_WINDOWS) ? "mvn.cmd" : "mvn";
    private String directory;
    private boolean skipPrompts;

    protected DependencyResolver(String directory, boolean skipPrompts) {
        this.directory = directory;
        this.skipPrompts = skipPrompts;
    }

    protected void process() {
        File directoryFile = new File(directory).getAbsoluteFile();
        if (!directoryFile.isDirectory()) {
            throw new RuntimeException(String.format("Directory: %s is not a directory", directory));
        }

        String pomFilePath = directory + File.separator + "pom.xml";
        Path pomPath = Paths.get(pomFilePath);
        if (!Files.isRegularFile(pomPath)) {
            throw new RuntimeException(String.format("Directory: %s does not contain pom.xml", directory));
        }

        executeCommand(directoryFile, MVN_COMMAND, "dependency:tree");

        // strip out exclusions from pom.xml
        // run maven dependency:tree
        // aggregate results
        // resolve conflicts
        // update
    }

    private void executeCommand(File directoryFile, String... commandAndParams) {
        try {
            ProcessBuilder builder = new ProcessBuilder(commandAndParams);
            //builder.inheritIO(); // TODO: Learn what this does - weird things with consuming output
            builder.directory(directoryFile);
            Process process = builder.start();

            int exitValue = process.waitFor();
            if (exitValue != 0) {
                throw new RuntimeException(String.format("Process: %s failed with status code %d",
                        commandAndParams, exitValue));
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = "";
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("Checksum").build()
                .defaultHelp(true)
                .description("Resolve conflicting dependencies (exclusions).");
        parser.addArgument("-s", "--skipPrompts")
                .type(Boolean.class)
                .setDefault(false)
                .help("Specify to skip any prompts");
        parser.addArgument("directory")
                .type(String.class)
                .required(true)
                .help("directory of project to modify");
        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        boolean skipPrompts = ns.getBoolean("skipPrompts");
        String directory = ns.getString("directory");

        DependencyResolver resolver = new DependencyResolver(directory, skipPrompts);
        resolver.process();
    }
}
