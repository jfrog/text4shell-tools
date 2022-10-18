# commons-text-tools

### Overview

CVE-2022-42889 may pose a serious threat to a wide range of Java-based applications. The important questions a developer may ask in this context are:

### 1. Does my code include `commons-text`? Which versions?

Does the released code include `commons-text`? Which version of the library is included there? Answering these questions may not be immediate due to two factors:

1) Transitive dependencies: while `commons-text` may not be in the direct dependency list of the project, it may be used indirectly by some other dependency.

2) The code of this library may not appear directly as a separate file, but rather be bundled in some other code jar file.

JFrog is releasing a tool to help resolve this problem: [`scan_commons_text_versions`](#scan_commons_text_versionspy). The tool looks for the **class code** of `StringLookupFactory`  **(regardless of containing `.jar` file names and content of `pom.xml` files)**, and attempts to fingerprint the versions of the objects to report whether the included version of `commons-text` is vulnerable.

### 2. Does my code use vulnerable `commons-text` functions? 

The question is relevant for the cases where the developer would like to verify if the calls to `commons-text` in the codebase may pass potentially attacker-controlled data. While the safest way to fix the vulnerability, as discussed in the advisories, is to apply the appropriate patches, controlling for and verifying the potential impact under assumption of unpatched `commons-text` may be valuable in many situations. 

[`scan_commons_text_calls_jar.py`](#scan_commons_text_calls_jarpy), which locates the calls to the vulnerable functions in *compiled .jar*s, and reports the findings as class name and method names in which each call appears.


##### Usage
### `scan_commons_text_versions.py`

```
python scan_commons_text_versions.py root-folder [-quiet] [-exclude folder1 folder2 ..]
```

The tool will scan `root_folder` recursively for `.jar` and `.war` files; in each located file the tool looks for a `StringLookupFactory.class` (recursively in each `.jar` file). If at least one of the classes is found, the tool attempts to fingerprint its version (including some variations found in patches and backport patches) in order to report whether the code is vulnerable.

With `-quiet` flag, only version conclusions are printed out, and other messages (files not found/ archives failed to open/ password protected archives) are muted.

Folders appearing after `-exclude` (optional) are skipped.

------

### `scan_commons_text_calls_jar.py`

The tool requires python 3 and the following 3rd party libraries: `jawa`, `tqdm`, `easyargs`, `colorama`

##### Dependencies installation

```
pip install -r requirements.txt
```

##### Usage

The default use case:

```
python scan_commons_text_calls_jar.py root-folder
```

will recursively scan all `.jar` files in `root-folder`, for each printing out locations (class name and method name) of calls to `lookup`/`replace`/`replaceIn` methods of `StringSubstitutor`/`StringLookup`. 

The tool may be configured for additional use cases using the following command line flags.

| Flag                  | Default value        | Use                                                          |
| --------------------- | -------------------- | ------------------------------------------------------------ |
| `--class_regex`       | (.*StringSubstitutor&#124;.*StringLookup)       | Regular expression for required class name                   |
| `--method_regex`      | (lookup&#124;replace&#124;replaceIn)                 | Regular expression for required method name                  |
| `--quickmatch_string` | (StringLookup&#124;StringSubstitutor)                | Pre-condition for file analysis: .jar files not containing the specified regex will be ignored |
| `--class_existence`   | Not set              | When not set, look for calls to class::method as  specified by regexes. When set, `--method_regex` is ignored, and the tool will look for *existence* of classes specified by `--class_regex` in the jar. |
| `--no_quickmatch`     | Not set              | When set, the value of `--quickmatch_string` is ignored and all jar files are analyzed |
| `--caller_block`      | .*org/apache/commons/text | If caller class matches this regex, it will *not* be displayed |


