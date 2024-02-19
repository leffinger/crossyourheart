package io.github.leffinger.crossyourheart;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import io.github.leffinger.crossyourheart.io.PuzFile;

import static io.github.leffinger.crossyourheart.io.AbstractPuzzleFile.ScrambleState.LOCKED;
import static io.github.leffinger.crossyourheart.io.AbstractPuzzleFile.ScrambleState.SCRAMBLED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Testing loading of PUZ files.
 */
@RunWith(Parameterized.class)
public class PuzzleLoaderTest {

    private static final ImmutableList<PuzzleInfo> PUZZLE_INFOS =
            ImmutableList.<PuzzleInfo>builder()
                    .add(PuzzleInfo.builder().setFilename("/3x3.puz").setTitle("3x3")
                                 .setVersionString("1.2\0").addSectionNames("LTIM").build())
                    .add(PuzzleInfo.builder().setFilename("/3x3_filled.puz").setTitle("3x3")
                                 .setVersionString("1.4\0").addSectionNames("LTIM", "RUSR").build())
                    .add(PuzzleInfo.builder().setFilename("/wsj200827.puz")
                                 .setTitle("Financial Sectors").setVersionString("1.4\0")
                                 .setNumRebusSquares(4).addRebuses("STOCK", "BLACK", "FREE", "MASS")
                                 .addSectionNames("GRBS", "RTBL", "LTIM").build())
                    .add(PuzzleInfo.builder().setFilename("/076_ExtremelyOnline.puz")
                                 .setTitle("\"Extremely Online\"").setVersionString("1.3\0")
                                 .setScrambled(LOCKED).build())
                    .add(PuzzleInfo.builder().setFilename("/075_WoodenIdols.puz")
                                 .setTitle("\"Wooden Idols\"").setVersionString("1.4\0")
                                 .setScrambled(LOCKED).addSectionNames("LTIM").setElapsedTime(251)
                                 .build())
                    .add(PuzzleInfo.builder().setFilename("/mgwcc636.puz").setTitle("Team Meta")
                                 .setVersionString("1.2c").setScrambled(SCRAMBLED).build())
                    .add(PuzzleInfo.builder().setFilename("/mgwcc637.puz")
                                 .setTitle("\"Grid...of...Fortune!\"").setVersionString("1.2c")
                                 .setScrambled(SCRAMBLED).build())
                    .add(PuzzleInfo.builder().setFilename("/mgwcc647.puz")
                                 .setTitle("Time to Reorder").setVersionString("1.2c")
                                 .setScrambled(SCRAMBLED).build())
                    .add(PuzzleInfo.builder().setFilename("/1287UpWithPeople.puz")
                                 .setTitle("UP WITH PEOPLE").setVersionString("1.3\0").build())
                    .add(PuzzleInfo.builder().setFilename("/Mar2920.puz")
                                 .setTitle("NY Times, Sunday, March 29, 2020 Keep The Change")
                                 .setVersionString("1.3\0").addCircledSquare(8, 7)
                                 .addCircledSquare(8, 15).addCircledSquare(9, 8)
                                 .addCircledSquare(9, 16).addCircledSquare(10, 4)
                                 .addCircledSquare(10, 12).addCircledSquare(11, 6)
                                 .addCircledSquare(11, 14).addCircledSquare(12, 5)
                                 .addCircledSquare(12, 13).addSectionNames("GEXT").build())
                    .add(PuzzleInfo.builder().setFilename("/Sep0520.puz")
                                 .setTitle("NY Times, Saturday, September 5, 2020 ")
                                 .setVersionString("1.3\0").build())
                    .add(PuzzleInfo.builder().setFilename("/Nov0596.puz")
                                 .setTitle("NY Times, Tuesday, November 5, 1996 ")
                                 .setVersionString("1.3\0").addSectionNames("GEXT").build())
                    .add(PuzzleInfo.builder().setFilename("/2020-10-9-Newsday.puz")
                                 .setTitle("BEAR WITH US").setVersionString("1.2\0").build())
                    .add(PuzzleInfo.builder().setFilename("/wp210620.puz")
                                 .setTitle("Drawing a Blank (PLEASE SEE NOTEPAD BEFORE SOLVING)")
                                 .setVersionString("2.0\0").addSectionNames("LTIM").build())
                    .add(PuzzleInfo.builder().setFilename("/lollapuzzoola2.puz")
                                 .setTitle("Puzzle 2: Tag! [Go ahead and call out the answers!]")
                                 .setVersionString("1.3\0").build())
                    .add(PuzzleInfo.builder().setFilename("/THemeless 19.puz")
                                 .setTitle("Themeless #19").setVersionString("1.4\0")
                                 .addSectionNames("GRBS", "LTIM").build()).build();
    private final boolean mSerializeFirst;
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    private PuzzleInfo mPuzzleInfo;
    private PuzFile mPuzFile;

    public PuzzleLoaderTest(PuzzleInfo puzzleInfo, boolean serializeFirst) {
        mPuzzleInfo = puzzleInfo;
        mSerializeFirst = serializeFirst;
    }

    @Parameters(name = "{0}_serializeFirst_{1}")
    public static List<Object[]> parameters() {
        ImmutableList.Builder<Object[]> params = ImmutableList.builder();
        for (PuzzleInfo puzzleInfo : PUZZLE_INFOS) {
            for (boolean serializeFirst : new boolean[]{false, true}) {
                params.add(new Object[]{puzzleInfo, serializeFirst});
            }
        }
        return params.build();
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
        InputStream file = PuzzleLoaderTest.class.getResourceAsStream(mPuzzleInfo.filename());
        mPuzFile = new PuzFile(file);
        if (mSerializeFirst) {
            File savedFile = mTemporaryFolder.newFile();
            mPuzFile.savePuzzleFile(savedFile);
            try (FileInputStream inputStream = new FileInputStream(savedFile)) {
                mPuzFile = PuzFile.verifyPuzFile(inputStream);
            }

            if (!mPuzzleInfo.sectionNames().contains("LTIM")) {
                mPuzzleInfo = mPuzzleInfo.toBuilder().addSectionNames("LTIM").build();
            }
        }
    }

    @Test
    public void verifyFile() throws IOException {
        PuzFile.verifyPuzFile(PuzzleLoaderTest.class.getResourceAsStream(mPuzzleInfo.filename()));
    }

    @Test
    public void verifyTitle() {
        assertEquals(mPuzzleInfo.title(), mPuzFile.getTitle());
    }

    @Test
    public void verifyVersionString() {
        assertEquals(mPuzzleInfo.versionString(), mPuzFile.getVersionString());
    }

    @Test
    public void verifyHeaderChecksum() {
        assertHexEquals(mPuzFile.getHeaderChecksum(), mPuzFile.computeHeaderChecksum());
    }

    @Test
    public void verifyMaskedChecksumBit0() {
        byte[] maskedChecksums = mPuzFile.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzFile.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[0], computedMaskedChecksums[0]);
    }

    @Test
    public void verifyMaskedChecksumBit1() {
        byte[] maskedChecksums = mPuzFile.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzFile.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[1], computedMaskedChecksums[1]);
    }

    @Test
    public void verifyMaskedChecksumBit2() {
        byte[] maskedChecksums = mPuzFile.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzFile.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[2], computedMaskedChecksums[2]);
    }

    @Test
    public void verifyMaskedChecksumBit3() {
        byte[] maskedChecksums = mPuzFile.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzFile.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[3], computedMaskedChecksums[3]);
    }

    @Test
    public void verifyMaskedChecksumBit4() {
        byte[] maskedChecksums = mPuzFile.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzFile.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[4], computedMaskedChecksums[4]);
    }

    @Test
    public void verifyMaskedChecksumBit5() {
        byte[] maskedChecksums = mPuzFile.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzFile.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[5], computedMaskedChecksums[5]);
    }

    @Test
    public void verifyMaskedChecksumBit6() {
        byte[] maskedChecksums = mPuzFile.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzFile.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[6], computedMaskedChecksums[6]);
    }

    @Test
    public void verifyMaskedChecksumBit7() {
        byte[] maskedChecksums = mPuzFile.getMaskedChecksums();
        byte[] computedMaskedChecksums = mPuzFile.computeMaskedChecksums();
        assertHexEquals(maskedChecksums[7], computedMaskedChecksums[7]);
    }

    @Test
    public void verifyFileChecksum() {
        assertHexEquals(mPuzFile.getFileChecksum(), mPuzFile.computeFileChecksum());
    }

    @Test
    public void verifySavedFile() throws IOException {
        File savedFile = mTemporaryFolder.newFile();
        mPuzFile.savePuzzleFile(savedFile);
        PuzFile savedPuzzle;
        try (FileInputStream inputStream = new FileInputStream(savedFile)) {
            savedPuzzle = PuzFile.verifyPuzFile(inputStream);
        }
        assertEquals(mPuzFile.getWidth(), savedPuzzle.getWidth());
        assertEquals(mPuzFile.getHeight(), savedPuzzle.getHeight());
        assertHexEquals(mPuzFile.getNumClues(), savedPuzzle.getNumClues());
        assertHexEquals(mPuzFile.getUnknownBitmask(), savedPuzzle.getUnknownBitmask());
        assertHexEquals(mPuzFile.getScrambledTag(), savedPuzzle.getScrambledTag());
        assertHexEquals(mPuzFile.getHeaderChecksum(), savedPuzzle.getHeaderChecksum());
        assertEquals(mPuzFile.getTitle(), savedPuzzle.getTitle());
        assertEquals(mPuzFile.getAuthor(), savedPuzzle.getAuthor());
        assertTrue(mPuzFile.checkDuplicate(savedPuzzle));
        assertTrue(savedPuzzle.checkDuplicate(mPuzFile));
    }

    @Test
    public void verifyScrambledState() {
        assertEquals(mPuzzleInfo.scrambled(), mPuzFile.getScrambleState());
    }

    @Test
    public void verifyRebusSquares() {
        assertNotNull(mPuzFile.getSectionAsText("GRBS"));
        assertEquals(mPuzzleInfo.numRebusSquares(), mPuzFile.getNumRebusSquares());
        for (int i = 0; i < mPuzzleInfo.numRebusSquares(); i++) {
            assertEquals(mPuzzleInfo.rebuses().get(i), mPuzFile.getRebus(i));
        }
    }

    @Test
    public void verifyCircles() {
        for (int row = 0; row < mPuzFile.getHeight(); row++) {
            for (int col = 0; col < mPuzFile.getWidth(); col++) {
                if (mPuzzleInfo.circledSquares().containsEntry(row, col)) {
                    assertTrue("not circled: row=" + row + ",col=" + col,
                               mPuzFile.isCircled(row, col));
                } else {
                    assertFalse("circled: row=" + row + ",col=" + col,
                                mPuzFile.isCircled(row, col));
                }
            }
        }
    }

    @Test
    public void verifyGextValues() {
        for (int row = 0; row < mPuzFile.getHeight(); row++) {
            for (int col = 0; col < mPuzFile.getWidth(); col++) {
                if (mPuzzleInfo.circledSquares().containsEntry(row, col)) {
                    assertHexEquals(PuzFile.GEXT_MASK_CIRCLED, mPuzFile.getGextMask(row, col));
                } else {
                    assertHexEquals(PuzFile.GEXT_MASK_NONE, mPuzFile.getGextMask(row, col));
                }
            }
        }
    }

    @Test
    public void verifyExtraSectionNames() {
        assertEquals(mPuzzleInfo.sectionNames(), mPuzFile.getSectionNames());
    }
}