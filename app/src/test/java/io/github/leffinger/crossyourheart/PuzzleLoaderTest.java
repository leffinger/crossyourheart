package io.github.leffinger.crossyourheart;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile.ScrambleState;
import io.github.leffinger.crossyourheart.io.PuzFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Testing loading of PUZ files.
 */
@RunWith(Parameterized.class)
public class PuzzleLoaderTest {

    private final String mFilename;
    private final String mTitle;
    private final String mVersionString;
    private final ScrambleState mScrambled;
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    private PuzFile mPuzzleLoader;

    public PuzzleLoaderTest(String filename, String title, String versionString,
                            ScrambleState scrambled) {
        mFilename = filename;
        mTitle = title;
        mVersionString = versionString;
        mScrambled = scrambled;
    }

    @Parameters(name = "{0}")
    public static List<Object[]> parameters() {
        return Arrays.asList(new Object[]{"/3x3.puz", "3x3", "1.2\0", ScrambleState.UNSCRAMBLED},
                             new Object[]{"/3x4.puz", "3x4", "1.2\0", ScrambleState.UNSCRAMBLED},
                             new Object[]{"/076_ExtremelyOnline.puz", "\"Extremely Online\"",
                                          "1.3\0", ScrambleState.LOCKED},
                             new Object[]{"/075_WoodenIdols.puz", "\"Wooden Idols\"", "1.4\0",
                                          ScrambleState.LOCKED},
                             new Object[]{"/mgwcc636.puz", "Team Meta", "1.2c",
                                          ScrambleState.SCRAMBLED},
                             new Object[]{"/mgwcc637.puz", "\"Grid...of...Fortune!\"", "1.2c",
                                          ScrambleState.SCRAMBLED},
                             new Object[]{"/1287UpWithPeople.puz", "UP WITH PEOPLE", "1.3\0",
                                          ScrambleState.UNSCRAMBLED},
                             new Object[]{"/Sep0520.puz", "NY Times, Saturday, September 5, 2020 ",
                                          "1.3\0", ScrambleState.UNSCRAMBLED});
    }

    private static void assertHexEquals(byte expected, byte actual) {
        assertEquals(String.format("\nExpected :0x%02X\nActual   :0x%02X", expected, actual),
                     expected, actual);
    }

    private static void assertHexEquals(int expected, int actual) {
        assertEquals(String.format("\nExpected :0x%04X\nActual   :0x%04X", expected, actual),
                     expected, actual);
    }

    @Before
    public void loadPuzzle() throws IOException {
        InputStream file = PuzzleLoaderTest.class.getResourceAsStream(mFilename);
        mPuzzleLoader = PuzFile.loadPuzFile(file);
    }

    @Test
    public void verifyFile() throws IOException {
        PuzFile.verifyPuzFile(PuzzleLoaderTest.class.getResourceAsStream(mFilename));
    }

    @Test
    public void verifyTitle() {
        assertEquals(mTitle, mPuzzleLoader.getTitle());
    }

    @Test
    public void verifyMagic() {
        assertEquals("ACROSS&DOWN", mPuzzleLoader.getMagic());
    }

    @Test
    public void verifyVersionString() {
        assertEquals(mVersionString, mPuzzleLoader.getVersionString());
    }

    @Test
    public void verifyHeaderChecksum() {
        assertHexEquals(mPuzzleLoader.getHeaderChecksum(), mPuzzleLoader.computeHeaderChecksum());
    }

    @Test
    public void verifyMaskedChecksumBit0() {
        byte[] maskedChecksums = mPuzzleLoader.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzzleLoader.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[0], computedMaskedChecksums[0]);
    }

    @Test
    public void verifyMaskedChecksumBit1() {
        byte[] maskedChecksums = mPuzzleLoader.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzzleLoader.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[1], computedMaskedChecksums[1]);
    }

    @Test
    public void verifyMaskedChecksumBit2() {
        byte[] maskedChecksums = mPuzzleLoader.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzzleLoader.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[2], computedMaskedChecksums[2]);
    }

    @Test
    public void verifyMaskedChecksumBit3() {
        byte[] maskedChecksums = mPuzzleLoader.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzzleLoader.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[3], computedMaskedChecksums[3]);
    }

    @Test
    public void verifyMaskedChecksumBit4() {
        byte[] maskedChecksums = mPuzzleLoader.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzzleLoader.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[4], computedMaskedChecksums[4]);
    }

    @Test
    public void verifyMaskedChecksumBit5() {
        byte[] maskedChecksums = mPuzzleLoader.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzzleLoader.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[5], computedMaskedChecksums[5]);
    }

    @Test
    public void verifyMaskedChecksumBit6() {
        byte[] maskedChecksums = mPuzzleLoader.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzzleLoader.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[6], computedMaskedChecksums[6]);
    }

    @Test
    public void verifyMaskedChecksumBit7() {
        byte[] maskedChecksums = mPuzzleLoader.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzzleLoader.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[7], computedMaskedChecksums[7]);
    }

    @Test
    public void verifyFileChecksum() {
        assertHexEquals(mPuzzleLoader.getFileChecksum(), mPuzzleLoader.computeFileChecksum());
    }

    @Test
    public void verifySavedFile() throws IOException {
        File savedFile = mTemporaryFolder.newFile();
        try (FileOutputStream outputStream = new FileOutputStream(savedFile)) {
            mPuzzleLoader.savePuzzleFile(outputStream);
        }
        PuzFile savedPuzzle;
        try (FileInputStream inputStream = new FileInputStream(savedFile)) {
            savedPuzzle = PuzFile.verifyPuzFile(inputStream);
        }
        assertEquals(mPuzzleLoader.getWidth(), savedPuzzle.getWidth());
        assertEquals(mPuzzleLoader.getHeight(), savedPuzzle.getHeight());
        assertHexEquals(mPuzzleLoader.getNumClues(), savedPuzzle.getNumClues());
        assertHexEquals(mPuzzleLoader.getUnknownBitmask(), savedPuzzle.getUnknownBitmask());
        assertHexEquals(mPuzzleLoader.getScrambledTag(), savedPuzzle.getScrambledTag());
        assertHexEquals(mPuzzleLoader.getHeaderChecksum(), savedPuzzle.getHeaderChecksum());
        assertEquals(mPuzzleLoader.getTitle(), savedPuzzle.getTitle());
        assertEquals(mPuzzleLoader.getAuthor(), savedPuzzle.getAuthor());
        assertTrue(mPuzzleLoader.checkDuplicate(savedPuzzle));
        assertTrue(savedPuzzle.checkDuplicate(mPuzzleLoader));
    }

    @Test
    public void verifyScrambledState() {
        assertEquals(mScrambled, mPuzzleLoader.getScrambleState());
    }
}