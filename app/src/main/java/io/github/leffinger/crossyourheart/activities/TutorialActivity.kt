package io.github.leffinger.crossyourheart.activities

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import io.github.leffinger.crossyourheart.R

class TutorialActivity : AppIntro() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Call addSlide passing your Fragments.
        var primaryColor = resources.getColor(R.color.colorPrimary, null)
        var puzzleListScreenColor = resources.getColor(R.color.colorRichTeal, null);
        var puzzleSolvingScreenColor = resources.getColor(R.color.colorRichGreen, null);
        addSlide(AppIntroFragment.newInstance(
                title = "Welcome to Cross Your Heart!",
                description = "Let's take a quick tour of the app...",
                imageDrawable = R.drawable.cross_your_heart_logo_large,
                titleColor = Color.WHITE,
                descriptionColor = Color.WHITE,
                backgroundColor = primaryColor,
        ))
        addSlide(AppIntroFragment.newInstance(
                title = "Puzzle List Screen",
                description = "When you open the app, you'll see a list of puzzles. Try the Welcome puzzle for a fun introduction to the app!",
                imageDrawable = R.drawable.puzzle_list_screen,
                titleColor = Color.WHITE,
                descriptionColor = Color.WHITE,
                backgroundColor = puzzleListScreenColor,
        ))
        addSlide(AppIntroFragment.newInstance(
                title = "Downloading Puzzles",
                description = "Press the Download button to find puzzles on the web. You can also open puzzles that are emailed to you, or that you download using your web browser.",
                imageDrawable = R.drawable.puzzle_list_screen_download_button,
                titleColor = Color.WHITE,
                descriptionColor = Color.WHITE,
                backgroundColor = puzzleListScreenColor,
        ))
        addSlide(AppIntroFragment.newInstance(
                title = "Loading Puzzles from Disk",
                description = "If you have downloaded a puzzle, but haven't yet opened it in Cross Your Heart, press the File button to find it using your file manager.",
                imageDrawable = R.drawable.puzzle_list_screen_file_button,
                titleColor = Color.WHITE,
                descriptionColor = Color.WHITE,
                backgroundColor = puzzleListScreenColor,
        ))
        addSlide(AppIntroFragment.newInstance(
                title = "Puzzle Solving Screen",
                description = "When you select a puzzle, you'll see the puzzle solving screen. This is a fairly standard interface for crossword solving, with a few special features...",
                imageDrawable = R.drawable.puzzle_screen,
                titleColor = Color.WHITE,
                descriptionColor = Color.WHITE,
                backgroundColor = puzzleSolvingScreenColor,
        ))
        addSlide(AppIntroFragment.newInstance(
                title = "Puzzle Menu Options",
                description = "The top menu includes the timer, puzzle info, pen/pencil toggle, and more options such as hints and navigation preferences.",
                imageDrawable = R.drawable.puzzle_screen_menu,
                titleColor = Color.WHITE,
                descriptionColor = Color.WHITE,
                backgroundColor = puzzleSolvingScreenColor,
        ))
        addSlide(AppIntroFragment.newInstance(
                title = "Navigating Within the Puzzle",
                description = "In order to move around the puzzle, you can tap the entry you want in the grid (double-tap to switch directions, or just tap the clue text). You can also quickly navigate between clues using the buttons above the keyboard.",
                imageDrawable = R.drawable.puzzle_screen_navigation,
                titleColor = Color.WHITE,
                descriptionColor = Color.WHITE,
                backgroundColor = puzzleSolvingScreenColor,
        ))
        addSlide(AppIntroFragment.newInstance(
                title = "Keyboard",
                description = "Use the bottom-left button to enter a rebus. Use the bottom right button to undo your most recent changes.",
                imageDrawable = R.drawable.puzzle_screen_keyboard,
                titleColor = Color.WHITE,
                descriptionColor = Color.WHITE,
                backgroundColor = puzzleSolvingScreenColor,
        ))
        addSlide(AppIntroFragment.newInstance(
                title = "Have Fun!",
                description = "That's it! Enjoy solving! You can always review this tutorial by selecting \"Show Tutorial\" in the puzzle list screen.",
                imageDrawable = R.drawable.cross_your_heart_logo_large,
                titleColor = Color.WHITE,
                descriptionColor = Color.WHITE,
                backgroundColor = primaryColor,
        ))
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        // Decide what to do when the user clicks on "Skip"
        finish()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        // Decide what to do when the user clicks on "Done"
        finish()
    }
}