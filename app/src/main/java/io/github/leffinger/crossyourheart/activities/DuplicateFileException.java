package io.github.leffinger.crossyourheart.activities;

class DuplicateFileException extends Exception {
    public DuplicateFileException(String duplicateFilename) {
        super(duplicateFilename);
    }
}
