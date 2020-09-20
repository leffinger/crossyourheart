package io.github.leffinger.crossyourheart;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile;
import io.github.leffinger.crossyourheart.io.PuzFile;
import io.github.leffinger.crossyourheart.viewmodels.CellViewModel;
import io.github.leffinger.crossyourheart.viewmodels.ClueViewModel;
import io.github.leffinger.crossyourheart.viewmodels.PuzzleViewModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class PuzzleViewModelTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("io.github.leffinger.crossyourheart", appContext.getPackageName());
    }

    @Test
    public void buildPuzzleModel() throws IOException {
        InputStream inputStream = PuzzleViewModelTest.class.getResourceAsStream("/3x3.puz");
        assertNotNull(inputStream);
        AbstractPuzzleFile puzzleFile = PuzFile.verifyPuzFile(inputStream);
        PuzzleViewModel puzzleViewModel =
                new PuzzleViewModel(puzzleFile, mTemporaryFolder.newFile(), false);
        assertEquals(3, puzzleViewModel.getNumRows());
        assertEquals(3, puzzleViewModel.getNumColumns());
        assertEquals("3x3", puzzleViewModel.getTitle());
        assertEquals("  Laura  ", puzzleViewModel.getAuthor());

        assertNull(puzzleViewModel.getCellViewModel(1, 0));
        assertNull(puzzleViewModel.getCellViewModel(1, 1));
        assertNull(puzzleViewModel.getCellViewModel(2, 0));
        assertNull(puzzleViewModel.getCellViewModel(2, 1));

        CellViewModel cell00 = puzzleViewModel.getCellViewModel(0, 0);
        ClueViewModel across00 = cell00.getAcrossClue();
        assertEquals(1, across00.getNumber());
        assertEquals("A", across00.getText());
        assertNull(cell00.getDownClue());

        CellViewModel cell01 = puzzleViewModel.getCellViewModel(0, 1);
        ClueViewModel across01 = cell01.getAcrossClue();
        assertEquals(1, across01.getNumber());
        assertEquals("A", across01.getText());
        assertNull(cell01.getDownClue());

        CellViewModel cell02 = puzzleViewModel.getCellViewModel(0, 2);
        ClueViewModel across02 = cell02.getAcrossClue();
        assertEquals(1, across02.getNumber());
        assertEquals("A", across02.getText());
        ClueViewModel down02 = cell02.getDownClue();
        assertEquals(2, down02.getNumber());
        assertEquals("B", down02.getText());

        CellViewModel cell12 = puzzleViewModel.getCellViewModel(1, 2);
        assertNull(cell12.getAcrossClue());
        ClueViewModel down12 = cell12.getDownClue();
        assertEquals(2, down12.getNumber());
        assertEquals("B", down12.getText());
    }
}