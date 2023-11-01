package org.checkerframework.specimin;

import com.google.common.base.Splitter;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.signature.qual.ClassGetSimpleName;
import org.checkerframework.checker.signature.qual.DotSeparatedIdentifiers;

/**
 * This class uses javac to analyze files. If there are any incompatible type errors in those files,
 * this class can suggest changes to be made to the existing types in the input files to solve all
 * the type errors. Note: This class can only solve type errors where there are mismatch between
 * types. For cases where there is type inference failed or unfound symbol, try to use
 * UnsolvedSymbolVisitor to add the missing files to the input first. {@link UnsolvedSymbolVisitor}
 */
class JavaTypeCorrect {

  /** List of the files to correct the types */
  public Set<String> fileNameList;

  /**
   * The root directory of the files in the fileNameList. If there are more than one root
   * directories involved, consider using more than one instances of JavaTypeCorrect
   */
  public String sourcePath;

  /**
   * This map is for type correcting. The key is the name of the current incorrect type, and the
   * value is the name of the desired correct type.
   */
  private Map<@ClassGetSimpleName String, @ClassGetSimpleName String> typeToChange;

  /**
   * Create a new JavaTypeCorrect instance. The directories of files in fileNameList are relative to
   * rootDirectory, and rootDirectory is an absolute path
   *
   * @param rootDirectory the root directory of the files to correct types
   * @param fileNameList the list of the relative directory of the files to correct types
   */
  public JavaTypeCorrect(String rootDirectory, Set<String> fileNameList) {
    this.fileNameList = fileNameList;
    this.sourcePath = new File(rootDirectory).getAbsolutePath();
    this.typeToChange = new HashMap<>();
  }

  /**
   * Get the value of typeToChange
   *
   * @return the value of typeToChange
   */
  public Map<@ClassGetSimpleName String, @ClassGetSimpleName String> getTypeToChange() {
    return typeToChange;
  }

  /**
   * This method updates typeToChange by using javac to run all the files in fileNameList and
   * analyzing the error messages returned by javac
   */
  public void correctTypesForAllFiles() {
    for (String fileName : fileNameList) {
      runJavacAndUpdateTypes(fileName);
    }
  }

  /**
   * This method uses javac to run a file and updates typeToChange if that file has any incompatible
   * type error
   *
   * @param filePath the directory of the file to be analyzed
   */
  public void runJavacAndUpdateTypes(String filePath) {
    try {
      String command = "javac";
      String[] arguments = {command, "-sourcepath", sourcePath, sourcePath + "/" + filePath};
      ProcessBuilder processBuilder = new ProcessBuilder(arguments);
      processBuilder.redirectErrorStream(true);
      Process process = processBuilder.start();
      BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.contains("error: incompatible types")) {
          updateTypeToChange(line);
        }
      }
    } catch (Exception e) {
      System.out.println(e);
    }
  }

  /**
   * This method updates typeToChange by relying on the error messages from javac
   *
   * @param errorMessage the error message to be analyzed
   */
  private void updateTypeToChange(String errorMessage) {
    List<String> splitErrorMessage = Splitter.onPattern("\\s+").splitToList(errorMessage);
    if (splitErrorMessage.size() < 7) {
      throw new RuntimeException("Unexpected type error messages: " + errorMessage);
    }
    /* There are two possible forms of error messages here:
     * 1. error: incompatible types: <type1> cannot be converted to <type2>
     * 2. error: incompatible types: found <type1> required <type2>
     */
    if (errorMessage.contains("cannot be converted to")) {
      // since this is from javac, we know that these will be dot-separated identifiers.
      @SuppressWarnings("signature")
      @DotSeparatedIdentifiers String incorrectType = splitErrorMessage.get(4);
      @SuppressWarnings("signature")
      @DotSeparatedIdentifiers String correctType = splitErrorMessage.get(splitErrorMessage.size() - 1);
      typeToChange.put(toSimpleName(incorrectType), toSimpleName(correctType));
    } else {
      @SuppressWarnings("signature")
      @DotSeparatedIdentifiers String incorrectType = splitErrorMessage.get(5);
      @SuppressWarnings("signature")
      @DotSeparatedIdentifiers String correctType = splitErrorMessage.get(splitErrorMessage.size() - 1);
      typeToChange.put(toSimpleName(incorrectType), toSimpleName(correctType));
    }
  }

  /**
   * This method takes the name of a class and converts it to the @ClassGetSimpleName type according
   * to Checker Framework. If the name is already in the @ClassGetSimpleName form, this method will
   * not make any changes
   *
   * @param className the name of the class to be converted
   * @return the simple name of the class
   */
  // the code is self-explanatory, essentially the last element of a class name is the simple name
  // of that class. This method takes the input from the error message of javac, so we know that
  // className will be a dot-separated identifier.
  @SuppressWarnings("signature")
  public static @ClassGetSimpleName String toSimpleName(@DotSeparatedIdentifiers String className) {
    List<String> classNameParts = Splitter.onPattern("[.]").splitToList(className);
    if (classNameParts.size() < 2) {
      return className;
    }
    return classNameParts.get(classNameParts.size() - 1);
  }
}
