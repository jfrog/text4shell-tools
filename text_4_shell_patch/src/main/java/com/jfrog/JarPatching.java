package com.jfrog;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

public class JarPatching {
	public static class PatchElement {
        public String pathToClassInsideJAR;
        public String methodName;
        public String type;
        public String codeToSetMethod;
    }
	static class ClassLinkItem {
		Path newClassFile;
		String pathClassInJar;
	}
	public static Boolean modifyJar(String pathToJAR, ArrayList<PatchElement> patchElements) throws IOException, NotFoundException, CannotCompileException {
		try (JarFile jarFile = new JarFile(pathToJAR)) {
			ArrayList<ClassLinkItem> newClassFiles = new ArrayList<ClassLinkItem>();

			for (PatchElement patchElement: patchElements) {
				String pathToClassInsideJAR = patchElement.pathToClassInsideJAR;
				String methodName = patchElement.methodName;
				String type = patchElement.type;
				String codeToSetMethod = patchElement.codeToSetMethod;
			
				ZipEntry zipEntry = jarFile.getEntry(pathToClassInsideJAR + ".class");
				if (zipEntry != null) {
					InputStream fis;
					try {
						fis = jarFile.getInputStream(zipEntry);
					} catch (IOException e) {
						System.out.println(pathToClassInsideJAR + ".class opening failed");
						continue;
					}
					
					ClassPool pool = ClassPool.getDefault();
					CtClass cc = pool.makeClass(fis);

					CtMethod cm;
					try {
						cm = cc.getMethod(methodName, type);
					} catch (NotFoundException e) {
						System.out.println(methodName + "(" + type + ") method not found");
						continue;
					}

					cm.setBody(codeToSetMethod);

					File tempClassFile = File.createTempFile("class-patched-", ".tmp");
					tempClassFile.deleteOnExit();

					DataOutputStream out = new DataOutputStream(new FileOutputStream(tempClassFile));

					cc.getClassFile().write(out);

					fis.close();
					Path externalClassFile = tempClassFile.toPath();
					
					ClassLinkItem classLink = new ClassLinkItem();
					classLink.newClassFile = externalClassFile;
					classLink.pathClassInJar = pathToClassInsideJAR + ".class";
					newClassFiles.add(classLink);
					System.out.println("Class " + pathToClassInsideJAR + " found in the Jar.");
				} else {
					System.out.println("Class " + pathToClassInsideJAR + " not found in the Jar.");
				}
				
			}
			jarFile.close();

			// Create BackUp
			String timestamp = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(new java.util.Date());
			Files.copy( Paths.get(pathToJAR), Paths.get(pathToJAR.substring(0, pathToJAR.length() - 4)+"_"+timestamp+".orig.jar"), StandardCopyOption.REPLACE_EXISTING); 
			// New Jar
			Map<String, String> launchenv = new HashMap<>(); 
			URI launchuri = URI.create("jar:"+new File(pathToJAR).toURI());
			launchenv.put("create", "true");
			
			try (FileSystem zipfs = FileSystems.newFileSystem(launchuri, launchenv)) {
				for (ClassLinkItem classLink: newClassFiles) {
					Path pathInJarfile = zipfs.getPath(classLink.pathClassInJar);
					Files.copy( classLink.newClassFile, pathInJarfile, StandardCopyOption.REPLACE_EXISTING); 
					System.out.println("Class " + classLink.pathClassInJar + " patched.");
				}
			}
			return true;
		} catch (IOException e) {
			System.out.println("Jar file <"+ pathToJAR +"> not found");
			return false;
		}
	}
}