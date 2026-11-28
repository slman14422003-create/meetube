package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple append-only adapter for {@link LiveChatSheet}: an avatar plus a single line of
 * "<b>author</b> message". New messages are added at the end via {@link #addItem(ChatItem)} so the
 * sheet can auto-scroll to the newest.
 */
public class LiveChatAdapter extends RecyclerView.Adapter<LiveChatAdapter.VH> {

    /**
     * PERF: a live chat is endless (hours of stream, hundreds of messages a minute on a busy one).
     * Unbounded, the list - and every ChatItem with its Spannable-ready strings - only grew, and
     * the RecyclerView's scroll range with it. Only the newest rows are ever read, so the oldest
     * are dropped once the list passes this size.
     */
    private static final int MAX_ITEMS = 500;
    /** Rows dropped in one go, so a busy chat trims once per batch instead of on every message. */
    private static final int TRIM_BATCH = 100;

    private final List<ChatItem> mItems = new ArrayList<>();

    public void setItems(List<ChatItem> items) {
        mItems.clear();
        if (items != null) {
            mItems.addAll(items);
            int extra = mItems.size() - MAX_ITEMS;
            if (extra > 0) {
                mItems.subList(0, extra).clear();
            }
        }
        notifyDataSetChanged();
    }

    /** Append a message; returns the new item count (so the caller can scroll to the last row). */
    public int addItem(ChatItem item) {
        if (item == null) {
            return mItems.size();
        }
        mItems.add(item);
        notifyItemInserted(mItems.size() - 1);
        if (mItems.size() > MAX_ITEMS + TRIM_BATCH) {
            int drop = mItems.size() - MAX_ITEMS;
            mItems.subList(0, drop).clear();
            notifyItemRangeRemoved(0, drop);
        }
        return mItems.size();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mobile_chat, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(mItems.get(position));
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        holder.recycle();
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ImageView mAvatar;
        private final TextView mMessage;

        VH(@NonNull View itemView) {
            super(itemView);
            mAvatar = itemView.findViewById(R.id.chat_avatar);
            mMessage = itemView.findViewById(R.id.chat_message);
        }

        void bind(ChatItem item) {
            Context context = itemView.getContext();

            // An Arabic or Hebrew name is set apart (a bidi isolate): it reads right to left on its
            // own instead of turning the row over, which put the name at the far end of the line.
            String author = item.getAuthorName() != null
                    ? androidx.core.text.BidiFormatter.getInstance().unicodeWrap(item.getAuthorName()) : "";
            String message = item.getMessage() != null ? item.getMessage() : "";

            SpannableStringBuilder sb = new SpannableStringBuilder();
            if (!author.isEmpty()) {
                sb.append(author);
                // MEETUBE(live chat, issue #32): YouTube's rows - the name in grey, the message white.
                sb.setSpan(new android.text.style.ForegroundColorSpan(androidx.core.content.ContextCompat.getColor(
                        context, R.color.mobile_color_on_surface_secondary)), 0, author.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append("  ");
            }
            sb.append(message);
            mMessage.setText(sb);

            String photo = item.getAuthorPhoto();
            if (TextUtils.isEmpty(photo)) {
                mAvatar.setImageResource(R.drawable.ic_watch_channel_placeholder);
            } else {
                Glide.with(context)
                        .load(photo)
                        .circleCrop()
                        .placeholder(R.drawable.ic_watch_channel_placeholder)
                        .error(R.drawable.ic_watch_channel_placeholder)
                        .into(mAvatar);
            }
        }

        void recycle() {
            Glide.with(itemView.getContext().getApplicationContext()).clear(mAvatar);
        }
    }
}