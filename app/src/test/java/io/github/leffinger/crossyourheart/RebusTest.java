package io.github.leffinger.crossyourheart;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.github.leffinger.crossyourheart.io.PuzFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Testing loading of PUZ files.
 */
public class RebusTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    private PuzFile mPuzzleLoader;

    @Test
    public void rebusPuzzle() throws IOException {
        InputStream file = RebusTest.class.getResourceAsStream("/3x3_filled.puz");
        mPuzzleLoader = PuzFile.loadPuzFile(file);

        assertEquals("3x3", mPuzzleLoader.getTitle());
        assertThat(mPuzzleLoader.getSectionNames(), Matchers.containsInAnyOrder("LTIM", "RUSR"));

        assertEquals("AA\0BB\0CC\0\0\0\0\0\0\0", mPuzzleLoader.getSectionAsText("RUSR"));

        // Reading a rebus.
        assertEquals("AA", mPuzzleLoader.getCellContents(0, 0));

        // Writing a new rebus.
        mPuzzleLoader.setCellContents(1, 0, "ABC");
        assertEquals("ABC", mPuzzleLoader.getCellContents(1, 0));

        // Overwriting a rebus.
        mPuzzleLoader.setCellContents(0, 0, "");
        assertEquals("", mPuzzleLoader.getCellContents(0, 0));
        mPuzzleLoader.setCellContents(0, 1, "D");
        assertEquals("D", mPuzzleLoader.getCellContents(0, 1));
        mPuzzleLoader.setCellContents(0, 2, "XYZ");
        assertEquals("XYZ", mPuzzleLoader.getCellContents(0, 2));

        // Write out to a file.
        File savedFile = mTemporaryFolder.newFile();
        try (FileOutputStream outputStream = new FileOutputStream(savedFile)) {
            mPuzzleLoader.savePuzzleFile(outputStream);
        }
        PuzFile savedPuzzle;
        try (FileInputStream inputStream = new FileInputStream(savedFile)) {
            savedPuzzle = PuzFile.verifyPuzFile(inputStream);
        }
        assertEquals("\0\0XYZ\0ABC\0\0\0\0\0\0", savedPuzzle.getSectionAsText("RUSR"));
    }
}