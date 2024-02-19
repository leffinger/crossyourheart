package io.github.leffinger.crossyourheart.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.Executors;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.TimerBinding;
import io.github.leffinger.crossyourheart.io.AbstractPuzzleFile;
import io.github.leffinger.crossyourheart.io.IOUtil;
import io.github.leffinger.crossyourheart.io.PuzFile;
import io.github.leffinger.crossyourheart.room.Database;
import io.github.leffinger.crossyourheart.room.Puzzle;
import io.github.leffinger.crossyourheart.room.PuzzleDao;
import io.github.leffinger.crossyourheart.viewmodels.PuzzleViewModel;

public class PuzzleActivity extends AppCompatActivity implements PuzzleFragment.Callbacks {
    public static final String KEY_PUZZLE = "puzzle";
    private static final String TAG = "PuzzleActivity";
    private Puzzle mPuzzle;
    private TimerBinding mTimerBinding;
    private PuzzleViewModel mPuzzleViewModel;

    public static Intent newIntent(Context context, Puzzle puzzle) {
        Intent intent = new Intent(context, PuzzleActivity.class);
        intent.putExtra(KEY_PUZZLE, puzzle);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_container);
        getSupportFragmentManager().beginTransaction()
                                   .replace(R.id.container, PuzzleLoadingFragment.newInstance())
                                   .commitNow();

        if (savedInstanceState != null) {
            mPuzzle = (Puzzle) savedInstanceState.getSerializable(KEY_PUZZLE);
        } else {
            mPuzzle = (Puzzle) getIntent().getSerializableExtra(KEY_PUZZLE);
        }
        if (mPuzzle == null) {
            finish();
            return;
        }

        mPuzzleViewModel = new ViewModelProvider(PuzzleActivity.this).get(PuzzleViewModel.class);

        LayoutInflater inflater = LayoutInflater.from(this);
        mTimerBinding = DataBindingUtil.inflate(inflater, R.layout.timer, null, false);
        mTimerBinding.setLifecycleOwner(this);

        // Display timer in the action bar.
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setCustomView(mTimerBinding.getRoot());
        mTimerBinding.solved.setVisibility(View.INVISIBLE);

        if (!mPuzzle.opened) {
            // Mark puzzle as opened.
            Database database = Database.getInstance(getApplicationContext());
            AsyncTask.execute(() -> {
                database.puzzleDao()
                        .updateOpened(new PuzzleDao.OpenedUpdate(mPuzzle.filename, true));
            });
        }

        Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean startWithDownClues =
                    preferences.getBoolean(getString(R.string.preference_start_with_down_clues),
                            false);

            File file = IOUtil.getPuzzleFile(PuzzleActivity.this, mPuzzle.filename);
            PuzFile puzFile;
            try (FileInputStream inputStream = new FileInputStream(file)) {
                puzFile = new PuzFile(inputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            handler.post(() -> {
                mPuzzleViewModel.initialize(puzFile, file, startWithDownClues,
                        mPuzzle.downsOnlyMode);
                mPuzzleViewModel.cellViewModelsReady().observe(this, ready -> {
                    if (!ready) return;
                    onViewModelReady();
                });
            });
        });
    }

    private void onViewModelReady() {
        // If the puzzle was not solved to begin with, display a message when it is solved.
        // This also handles situations where the puzzle goes from solved to unsolved, e.g. reset.
        mPuzzleViewModel.isSolved().removeObservers(this);
        mPuzzleViewModel.isSolved().observe(this, new Observer<Boolean>() {
            private boolean shouldCongratulate = false;

            @Override
            public void onChanged(Boolean solved) {
                mTimerBinding.solved.setVisibility(solved ? View.VISIBLE : View.INVISIBLE);

                if (solved && shouldCongratulate) {
                    // Stop the timer and save the final time in the viewModel.
                    mTimerBinding.timer.stop();
                    String time = mTimerBinding.timer.getText().toString();
                    long elapsedTime = Math.round(
                            (SystemClock.elapsedRealtime() - mTimerBinding.timer.getBase()) /
                                    1000.0);
                    Log.i(TAG, "STOPPING TIMER AT " + elapsedTime + " SECONDS");
                    mPuzzleViewModel.getTimerInfo()
                                    .setValue(new AbstractPuzzleFile.TimerInfo(elapsedTime, false));

                    // Show a congratulatory dialog.
                    new AlertDialog.Builder(PuzzleActivity.this).setTitle(
                                                                        getString(R.string.alert_solved, time))
                                                                .setPositiveButton(
                                                                        android.R.string.ok, null)
                                                                .create()
                                                                .show();
                    shouldCongratulate = false;
                } else if (!solved && !shouldCongratulate) {
                    shouldCongratulate = true;
                }
            }
        });

        // Restart the timer when the puzzle is reset.
        mPuzzleViewModel.getTimerInfo().observe(this, timerInfo -> {
            if (timerInfo == null) {
                return;
            }

            if (timerInfo.elapsedTimeSecs == 0L && timerInfo.isRunning) {
                // Puzzle was reset; restart timer from beginning.
                Log.i(TAG, "RESTARTING TIMER AT 0");
                mTimerBinding.timer.setBase(SystemClock.elapsedRealtime());
                mTimerBinding.timer.start();
            }
        });

        PuzzleFragment fragment = PuzzleFragment.newInstance(mPuzzle);
        getSupportFragmentManager().beginTransaction()
                                   .replace(R.id.container, fragment)
                                   .commitNow();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(KEY_PUZZLE, mPuzzle);
    }

    @Override
    public void onClueListViewSelected() {
        getSupportFragmentManager().beginTransaction()
                                   .replace(R.id.container, PuzzleClueListFragment.newInstance())
                                   .addToBackStack(null)
                                   .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Wait for the puzzle file's TimerInfo to be initialized before starting the timer (else
        // we could overwrite the existing value).
        LiveData<AbstractPuzzleFile.TimerInfo> timerInfoLiveData = mPuzzleViewModel.getTimerInfo();
        timerInfoLiveData.observe(this, new Observer<AbstractPuzzleFile.TimerInfo>() {
            @Override
            public void onChanged(AbstractPuzzleFile.TimerInfo timerInfo) {
                if (timerInfo == null) {
                    return;
                }

                Log.i(TAG, "STARTING TIMER AT " + timerInfo.elapsedTimeSecs + " SECONDS");
                mTimerBinding.timer.setBase(
                        SystemClock.elapsedRealtime() - (timerInfo.elapsedTimeSecs * 1000));
                if (timerInfo.isRunning) {
                    mTimerBinding.timer.start();
                }

                timerInfoLiveData.removeObserver(this);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        AbstractPuzzleFile.TimerInfo timerInfo = mPuzzleViewModel.getTimerInfo().getValue();
        if (timerInfo != null && timerInfo.isRunning) {
            long elapsedTime =
                    (SystemClock.elapsedRealtime() - mTimerBinding.timer.getBase()) / 1000;
            Log.i(TAG, "PAUSING TIMER AT " + elapsedTime + " SECONDS");
            mPuzzleViewModel.getTimerInfo()
                            .setValue(new AbstractPuzzleFile.TimerInfo(elapsedTime, true));
            mTimerBinding.timer.stop();
        }
    }
}