package io.github.leffinger.crossyourheart.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.github.leffinger.crossyourheart.R;

public class PuzzleLoadingFragment extends Fragment {
    public static PuzzleLoadingFragment newInstance() {
        return new PuzzleLoadingFragment();
    }

    private PuzzleLoadingFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_puzzle_loading, container, false);
    }
}
