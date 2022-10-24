import java.io.IOException;
import java.util.ArrayList;

import com.jfrog.JarPatching;
import com.jfrog.JarPatching.PatchElement;

import javassist.CannotCompileException;
import javassist.NotFoundException;

public class Text4ShellPatch {

    private static void printMenu() {
        /*
         * Usage:
            java -jar Text4ShellPatch.jar TARGET_JAR [PATCHING_MODE]
            Where TARGET_JAR is the application to patch and PATCHING_MODE is
              0 (default): Patch Script lookup
              1:           Patch Script + DNS + URL lookups
            [Note: The original Jar will be kept in the same folder with the .orig.jar extension]
         */
        System.out.println("Usage:");
        System.out.println("  java -jar Text4ShellPatch.jar TARGET_JAR [PATCHING_MODE]");
        System.out.println("  Where TARGET_JAR is the application to patch and PATCHING_MODE is");
        System.out.println("    0 (default): Patch Script lookup");
        System.out.println("    1:           Patch Script + DNS + URL lookups");
        System.out.println("  [Note: The original Jar will be kept in the same folder with the .orig.jar extension]");
    }

    private static ArrayList<String> serviceToPatchGeneration(Integer mode) {
        ArrayList<String> outputList = new ArrayList<String>();
        if (mode > 2 || mode < 0) {
            System.out.println("Level of patching outside the available range.");
            printMenu();
            System.exit(1);
        } else {
            outputList.add("Script");
                
            if (mode == 1) {
                outputList.add("Dns");
                outputList.add("Url");
            }
        }
        return outputList;
    }

    private static void jarPatching(String pathToJar, ArrayList<String> servicesToPatch) throws IOException, NotFoundException, CannotCompileException {
        ArrayList<PatchElement> PatchElements = new ArrayList<PatchElement>();
        
        for (String serviceName: servicesToPatch) 
        {
            PatchElement servicePatchElement = new PatchElement();
            servicePatchElement.pathToClassInsideJAR = "org/apache/commons/text/lookup/" + serviceName + "StringLookup";
            servicePatchElement.methodName = "lookup";
            servicePatchElement.type = "(Ljava/lang/String;)Ljava/lang/String;";
            servicePatchElement.codeToSetMethod = "return \""+servicePatchElement.pathToClassInsideJAR+"."+servicePatchElement.methodName+" method called; this overrides the output <patch Text4Shell>\";";

            PatchElements.add(servicePatchElement);
        }

        if (JarPatching.modifyJar(pathToJar, PatchElements))
            System.out.println("Jar " + pathToJar +" replaced - patch done.");
        
    }

    public static void main(String[] args) throws IOException, NotFoundException, CannotCompileException {
        ArrayList<String> lookupsToPatch = new ArrayList<String>();

        if (args.length == 0) {
            printMenu();
            System.exit(1);
        } 

        Integer mode = 0;
        try  
        {
            mode = (args.length > 1 ? Integer.parseInt(args[1]) : 0);
        }  catch (NumberFormatException e) {
            System.out.println("PATCHING_MODE given as argument <"+args[1]+"> is not an integer.");
            System.exit(1);
        }
        
        lookupsToPatch = serviceToPatchGeneration(mode);

        String pathToJar = args[0];
        jarPatching(pathToJar, lookupsToPatch);
    } 
}
