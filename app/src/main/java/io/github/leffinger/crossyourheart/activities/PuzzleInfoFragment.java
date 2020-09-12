package io.github.leffinger.crossyourheart.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.DialogPuzzleInfoBinding;
import io.github.leffinger.crossyourheart.viewmodels.PuzzleInfoViewModel;

public class PuzzleInfoFragment extends DialogFragment {

    private PuzzleInfoFragment() {
    }

    public static PuzzleInfoFragment newInstance(PuzzleInfoViewModel viewModel) {
        Bundle args = new Bundle();
        args.putSerializable("viewModel", viewModel);
        PuzzleInfoFragment fragment = new PuzzleInfoFragment();
        fragment.setArguments(args);
        return fragment;
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        DialogPuzzleInfoBinding binding = DataBindingUtil
                .inflate(LayoutInflater.from(getActivity()), R.layout.dialog_puzzle_info, null,
                         false);
        PuzzleInfoViewModel viewModel =
                (PuzzleInfoViewModel) getArguments().getSerializable("viewModel");
        binding.setViewModel(viewModel);
        return new AlertDialog.Builder(getActivity()).setView(binding.getRoot())
                .setPositiveButton(android.R.string.ok, null).create();
    }
}
