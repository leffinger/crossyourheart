package io.github.leffinger.crossyourheart.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.DialogRebusBinding;

import static android.app.Activity.RESULT_OK;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;

public class RebusFragment extends DialogFragment implements DialogInterface.OnClickListener {
    private static final String ARG_CONTENTS = "contents";
    private DialogRebusBinding mBinding;

    public static RebusFragment newInstance(String contents) {
        Bundle args = new Bundle();
        args.putString(ARG_CONTENTS, contents);
        RebusFragment fragment = new RebusFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static String getContents(Intent data) {
        return data.getStringExtra(ARG_CONTENTS);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mBinding = DataBindingUtil
                .inflate(LayoutInflater.from(getActivity()), R.layout.dialog_rebus, null, false);
        mBinding.rebusEdit.setText(getArguments().getString(ARG_CONTENTS));
        mBinding.rebusEdit.selectAll();
        mBinding.rebusEdit.requestFocus();
        AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setView(mBinding.getRoot())
                .setTitle(R.string.rebus_title).setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, null).create();
        alertDialog.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        return alertDialog;
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int i) {
        Intent intent = new Intent();
        intent.putExtra(ARG_CONTENTS, mBinding.rebusEdit.getText().toString());
        getTargetFragment().onActivityResult(getTargetRequestCode(), RESULT_OK, intent);
    }
}
