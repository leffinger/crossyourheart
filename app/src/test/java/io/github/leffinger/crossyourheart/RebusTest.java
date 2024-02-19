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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Testing rebus squares.
 */
public class RebusTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void rebusPuzzle_userEntries() throws IOException {
        InputStream file = RebusTest.class.getResourceAsStream("/3x3_filled.puz");
        PuzFile puzFile = new PuzFile(file);

        assertEquals("3x3", puzFile.getTitle());
        assertThat(puzFile.getSectionNames(), Matchers.containsInAnyOrder("LTIM", "RUSR"));

        assertEquals("AA\0BB\0CC\0\0\0\0\0\0\0", puzFile.getSectionAsText("RUSR"));

        // Reading a rebus.
        assertEquals("AA", puzFile.getCellContents(0, 0));

        // Writing a new rebus.
        puzFile.setCellContents(1, 0, "ABC");
        assertEquals("ABC", puzFile.getCellContents(1, 0));

        // Overwriting a rebus.
        puzFile.setCellContents(0, 0, "");
        assertEquals("", puzFile.getCellContents(0, 0));
        puzFile.setCellContents(0, 1, "D");
        assertEquals("D", puzFile.getCellContents(0, 1));
        puzFile.setCellContents(0, 2, "XYZ");
        assertEquals("XYZ", puzFile.getCellContents(0, 2));

        // Write out to a file.
        File savedFile = mTemporaryFolder.newFile();
        puzFile.savePuzzleFile(savedFile);
        PuzFile savedPuzzle;
        try (FileInputStream inputStream = new FileInputStream(savedFile)) {
            savedPuzzle = PuzFile.verifyPuzFile(inputStream);
        }
        assertEquals("\0\0XYZ\0ABC\0\0\0\0\0\0", savedPuzzle.getSectionAsText("RUSR"));
    }

    @Test
    public void rebusEntries_solution() throws IOException {
        InputStream file = RebusTest.class.getResourceAsStream("/wsj200827.puz");
        PuzFile puzFile = new PuzFile(file);

        // Top left corner should be STOCK.
        assertEquals("STOCK", puzFile.getSolution(0,0));

        // Either "STOCK" or "S" should be acceptable as an answer.
        puzFile.setCellContents(0, 0, "STOCK");
        assertTrue((puzFile.isCorrect(0, 0)));
        puzFile.setCellContents(0, 0, "S");
        assertTrue((puzFile.isCorrect(0, 0)));

        // But not "STK".
        puzFile.setCellContents(0, 0, "STK");
        assertFalse((puzFile.isCorrect(0, 0)));
    }
}