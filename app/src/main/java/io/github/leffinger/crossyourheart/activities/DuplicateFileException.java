package io.github.leffinger.crossyourheart.activities;

import io.github.leffinger.crossyourheart.room.Puzzle;

class DuplicateFileException extends Exception {
    private final Puzzle mDuplicatePuzzle;
    public DuplicateFileException(Puzzle duplicatePuzzle) {
        super();
        mDuplicatePuzzle = duplicatePuzzle;
    }

    public Puzzle getDuplicatePuzzle() {
        return mDuplicatePuzzle;
    }
}
