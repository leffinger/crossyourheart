package io.github.leffinger.crossyourheart;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;

import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile.ScrambleState;

import static io.github.leffinger.crossyourheart.io.AbstractPuzzleFile.ScrambleState.UNSCRAMBLED;

@AutoValue
abstract class PuzzleInfo {
    abstract String filename();

    abstract String title();

    abstract String versionString();

    abstract ScrambleState scrambled();

    abstract int numRebusSquares();

    abstract ImmutableList<String> rebuses();

    abstract ImmutableMultimap<Integer, Integer> circledSquares();

    abstract ImmutableSet<String> sectionNames();

    @Override
    public String toString() {
        return filename();
    }

    public static Builder builder() {
        return new AutoValue_PuzzleInfo.Builder().setScrambled(UNSCRAMBLED).setNumRebusSquares(0);
    }

    @AutoValue.Builder
    public abstract static class Builder {
        abstract Builder setFilename(String filename);
        abstract Builder setTitle(String title);
        abstract Builder setVersionString(String versionString);
        abstract Builder setScrambled(ScrambleState scrambled);
        abstract Builder setNumRebusSquares(int numRebusSquares);
        abstract ImmutableList.Builder<String> rebusesBuilder();
        Builder addRebuses(String... rebuses) {
            rebusesBuilder().add(rebuses);
            return this;
        }
        abstract ImmutableMultimap.Builder<Integer, Integer> circledSquaresBuilder();
        Builder addCircledSquare(int row, int col) {
            circledSquaresBuilder().put(row, col);
            return this;
        }
        abstract ImmutableSet.Builder<String> sectionNamesBuilder();
        Builder addSectionNames(String... sectionNames) {
            sectionNamesBuilder().add(sectionNames);
            return this;
        }
        abstract PuzzleInfo build();
    }
}
