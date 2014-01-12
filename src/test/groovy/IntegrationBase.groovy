class IntegrationBase {

  void checkExistenceAndContentOfAFile(file, contents) {
    if (!file.canRead()) {
        throw new FileNotFoundException( "Could not find the " + file);
    }

    def lines_to_check_in_unix_script_marker = [:];
    (0..contents.size()).each { index ->
        lines_to_check_in_unix_script_marker[index] = false
    }

    file.eachLine { file_content, file_line ->
        contents.eachWithIndex { contents_expected, index ->
      if (file_content.equals(contents_expected)) {
          lines_to_check_in_unix_script_marker[index] = true;
      }
        }
    }

    contents.eachWithIndex { value, index ->
      if ( lines_to_check_in_unix_script_marker[index] == false ) {
        throw new Exception("The expected content in " + file + " couldn't be found." + contents[index]);
      }
    }
  }
}
