package io.github.leffinger.crossyourheart.activities;

import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.leffinger.crossyourheart.R;
import io.github.leffinger.crossyourheart.databinding.FragmentDownloadPuzzlesBinding;
import io.github.leffinger.crossyourheart.databinding.FragmentWebsiteBinding;

public class DownloadPuzzlesFragment extends Fragment {

    private Website[] mWebsites =
            new Website[]{new Website(R.string.url_nytimes, R.string.description_nytimes),
                          new Website(R.string.url_mgwcc, R.string.description_mgwcc),
                          new Website(R.string.url_beq, R.string.description_beq),
                          new Website(R.string.url_fiend, R.string.description_fiend)};

    public DownloadPuzzlesFragment() {
        // Required empty public constructor
    }

    // TODO: Rename and change types and number of parameters
    public static DownloadPuzzlesFragment newInstance() {
        DownloadPuzzlesFragment fragment = new DownloadPuzzlesFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FragmentDownloadPuzzlesBinding binding = DataBindingUtil
                .inflate(inflater, R.layout.fragment_download_puzzles, container, false);
        binding.downloadWebsitesRecyclerView
                .setLayoutManager(new LinearLayoutManager(getActivity()));
        binding.downloadWebsitesRecyclerView.setAdapter(new WebsiteAdapter());
        return binding.getRoot();
    }

    private static class Website {
        int url;
        int description;

        public Website(int url, int description) {
            this.url = url;
            this.description = description;
        }
    }

    private class WebsiteHolder extends RecyclerView.ViewHolder {
        private FragmentWebsiteBinding mBinding;

        public WebsiteHolder(FragmentWebsiteBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
            mBinding.website.setMovementMethod(LinkMovementMethod.getInstance());
        }

        public void bind(Website website) {
            mBinding.website.setText(Html.fromHtml(
                    String.format("<a href=\"%s\">%s</a>", getContext().getString(website.url),
                                  getContext().getString(website.description))));
        }
    }

    private class WebsiteAdapter extends RecyclerView.Adapter<WebsiteHolder> {

        @NonNull
        @Override
        public WebsiteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            FragmentWebsiteBinding binding =
                    DataBindingUtil.inflate(inflater, R.layout.fragment_website, parent, false);
            return new WebsiteHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull WebsiteHolder holder, int position) {
            holder.bind(mWebsites[position]);
        }

        @Override
        public int getItemCount() {
            return mWebsites.length;
        }
    }
}