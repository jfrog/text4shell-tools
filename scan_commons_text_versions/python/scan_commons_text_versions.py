import os
import sys
from collections import namedtuple
from enum import Enum, IntEnum
from zipfile import BadZipFile, ZipFile
from tarfile import open as tar_open
from tarfile import CompressionError, ReadError
import zlib

TARGET_CLASS_NAME = "StringLookupFactory.class"
PATCH_STRING = b"defaultStringLookups"
PATCH_STRING_2 = b"base64StringLookup"

RED = "\x1b[31m"
GREEN = "\x1b[32m"
YELLOW = "\x1b[33m"
RESET_ALL = "\x1b[0m"

ZIP_EXTENSIONS = {".jar", ".war", ".sar", ".ear", ".par", ".zip", ".apk"}
TAR_EXTENSIONS = {".gz", ".tar"}


class StringLookupFactoryVer(IntEnum):
    NOT_FOUND = 0
    v1_10 = 1
    v1_4_or_below = 2
    v1_5_to_1_9 = 3


class Status(Enum):
    INCONSISTENT = 0
    VULN = 1
    PARTIAL = 2
    FIX = 3


Diag = namedtuple("Diag", ["status", "note"])


DIAGNOSIS_TABLE = {
    StringLookupFactoryVer.v1_10: Diag(Status.FIX, "1.10 or above"),
    StringLookupFactoryVer.v1_4_or_below: Diag(Status.FIX, "1.4 or below"),
    StringLookupFactoryVer.v1_5_to_1_9: Diag(Status.VULN, "1.5 .. 1.9"),
}


def confusion_message(filename: str, classname: str):
    print(
        "Warning: "
        + filename
        + " contains multiple copies of "
        + classname
        + "; result may be invalid"
    )


def version_message(filename: str, diagnosis: Diag):
    messages = {
        Status.FIX: GREEN + "fixed" + RESET_ALL,
        Status.VULN: RED + "vulnerable" + RESET_ALL,
        Status.PARTIAL: YELLOW + "mitigated" + RESET_ALL,
        Status.INCONSISTENT: RED + "inconsistent" + RESET_ALL,
    }
    print(filename + " >> " + messages[diagnosis.status] + " " + diagnosis.note)


def class_version_string_lookup_factory(
    classfile_content: bytes,
) -> StringLookupFactoryVer:
    if PATCH_STRING in classfile_content:
        return StringLookupFactoryVer.v1_10
    if PATCH_STRING_2 in classfile_content:
        return StringLookupFactoryVer.v1_5_to_1_9

    return StringLookupFactoryVer.v1_4_or_below


def zip_file(file, rel_path: str, silent_mode: bool):
    try:
        with ZipFile(file) as jarfile:
            string_lookup_factory_status = StringLookupFactoryVer.NOT_FOUND

            for file_name in jarfile.namelist():
                if acceptable_filename(file_name):
                    next_file = jarfile.open(file_name, "r")
                    test_file(next_file, os.path.join(rel_path, file_name), silent_mode)
                    continue

                if file_name.endswith(TARGET_CLASS_NAME):
                    if string_lookup_factory_status != StringLookupFactoryVer.NOT_FOUND:
                        if not silent_mode:
                            confusion_message(file_name, TARGET_CLASS_NAME)
                    classfile_content = jarfile.read(file_name)
                    string_lookup_factory_status = class_version_string_lookup_factory(
                        classfile_content
                    )

            # went over all the files in the current layer; draw conclusions
            if string_lookup_factory_status:
                diagnosis = DIAGNOSIS_TABLE.get(
                    string_lookup_factory_status,
                    Diag(
                        Status.INCONSISTENT,
                        "StringLookupFactory: " + string_lookup_factory_status.name,
                    ),
                )
                version_message(rel_path, diagnosis)
    except (IOError, BadZipFile, UnicodeDecodeError, zlib.error, RuntimeError) as e:
        if not silent_mode:
            print(rel_path + ": " + str(e))
        return


def tar_file(file, rel_path: str, silent_mode: bool):
    try:
        with tar_open(fileobj=file) as tarfile:
            for item in tarfile.getmembers():
                if "../" in item.name:
                    continue
                if item.isfile() and acceptable_filename(item.name):
                    fileobj = tarfile.extractfile(item)
                    new_path = rel_path + "/" + item.name
                    test_file(fileobj, new_path, silent_mode)
                item = tarfile.next()
    except (
        IOError,
        FileExistsError,
        CompressionError,
        ReadError,
        RuntimeError,
        UnicodeDecodeError,
        zlib.error,
    ) as e:
        if not silent_mode:
            print(rel_path + ": " + str(e))
        return


def test_file(file, rel_path: str, silent_mode: bool):
    if any(rel_path.endswith(ext) for ext in ZIP_EXTENSIONS):
        zip_file(file, rel_path, silent_mode)

    elif any(rel_path.endswith(ext) for ext in TAR_EXTENSIONS):
        tar_file(file, rel_path, silent_mode)


def acceptable_filename(filename: str):
    return any(filename.endswith(ext) for ext in ZIP_EXTENSIONS | TAR_EXTENSIONS)


def run_scanner(root_dir: str, exclude_dirs, silent_mode: bool):
    if os.path.isdir(root_dir):
        for directory, dirs, files in os.walk(root_dir, topdown=True):
            [
                dirs.remove(excluded_dir)
                for excluded_dir in list(dirs)
                if os.path.join(directory, excluded_dir) in exclude_dirs
            ]

            for filename in files:
                if acceptable_filename(filename):
                    full_path = os.path.join(directory, filename)
                    rel_path = os.path.relpath(full_path, root_dir)
                    try:
                        with open(full_path, "rb") as file:
                            test_file(file, rel_path, silent_mode)
                    except FileNotFoundError as fnf_error:
                        if not silent_mode:
                            print(fnf_error)
    elif os.path.isfile(root_dir):
        if acceptable_filename(root_dir):
            with open(root_dir, "rb") as file:
                test_file(file, "", silent_mode)


def print_usage():
    print(
        "Usage: "
        + sys.argv[0]
        + " <root_folder> [-quiet] [-exclude <folder1> <folder2> ...]"
    )
    print("or: " + sys.argv[0] + "<archive_file> [-quiet]")
    exit()


def parse_command_line():
    if len(sys.argv) < 2:
        print_usage()

    root_dir = sys.argv[1]
    exclude_folders = []

    silent = len(sys.argv) > 2 and sys.argv[2] == "-quiet"
    exclude_start = 3 if silent else 2
    if len(sys.argv) > exclude_start:
        if not sys.argv[exclude_start] == "-exclude":
            print_usage()
        exclude_folders = sys.argv[exclude_start + 1 :]

    return root_dir, exclude_folders, silent


if __name__ == "__main__":

    root_dir, exclude_dirs, silent_mode = parse_command_line()

    for dir_to_check in exclude_dirs:
        if not os.path.isdir(dir_to_check):
            print(dir_to_check + " is not a directory")
            print_usage()
    if not os.path.isdir(root_dir) and not (
        os.path.isfile(root_dir) and acceptable_filename(root_dir)
    ):
        print(root_dir + " is not a directory or an archive")
        print_usage()

    print("Scanning " + root_dir)
    if exclude_dirs:
        print("Excluded: " + ", ".join(exclude_dirs))

    run_scanner(root_dir, set(exclude_dirs), silent_mode)
