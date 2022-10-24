#!/bin/bash

confirm() {
  read -p "$1 (yes/no) " yn

  case $yn in 
    yes ) return 0;;
    no ) return 1;;
    * ) echo invalid response;
      return 1;;
  esac
}

scan_result=$(python3.8 scan_commons_text_versions/python/scan_commons_text_versions.py $1);

IFS='
'
count=0
echo "Files found with scan_commons_text_versions's tool in $1 folder:"
for item in $scan_result;
do
  if [ $count -eq 0 ]; then
    path_to_folder=${item##*Scanning}
    count=$((count+1))
    continue
  fi
  echo $count ${item%>>*}
  count=$((count+1))
done

patch_msg="Do you want to patch them all to avoid potential RCE? (avoid .orig.jar files)"
confirm $patch_msg
confirm_value=$(echo $?)
if [ $confirm_value -eq 1 ]; then
  echo "You can patch them manually with the following command: java -jar ./text_4_shell_patch/Text4ShellPatch.jar TARGET_JAR"
else
  count=0
  for item in $scan_result;
  do
    if [ $count -eq 0 ]; then
      count=$((count+1))
      continue
    fi
    item_to_patch=${item%>>*}
    if [[ "$item_to_patch" == *".orig.jar"* ]]; then
      echo $count "- Backup file, not patched (" $item_to_patch ")"
    else
      echo $count "- Patching" $item_to_patch
      jar_path="$path_to_folder"/"$item_to_patch"
      eval "java -jar ./text_4_shell_patch/Text4ShellPatch.jar $jar_path"
    fi
    count=$((count+1))
    echo "-----"   
  done 
fi
