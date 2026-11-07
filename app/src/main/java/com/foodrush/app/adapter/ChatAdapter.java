package com.foodrush.app.adapter;

import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrush.app.R;
import com.foodrush.app.databinding.ItemChatMessageBinding;
import com.foodrush.app.model.ChatMessage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Chat bubbles: mine on the right in amber, theirs on the left. */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.Holder> {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final String myUid;

    public ChatAdapter(String myUid) {
        this.myUid = myUid;
    }

    public void submit(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemChatMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ChatMessage message = messages.get(position);
        boolean mine = myUid.equals(message.fromUid);
        holder.binding.messageRow.setGravity(mine ? Gravity.END : Gravity.START);
        holder.binding.messageText.setText(message.text);
        holder.binding.messageText.setBackgroundResource(mine ? R.drawable.bg_bubble_mine : R.drawable.bg_bubble_theirs);
        holder.binding.messageText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                mine ? R.color.on_amber : R.color.text_primary));
        holder.binding.messageTime.setText(DateFormat.getTimeFormat(holder.itemView.getContext())
                .format(new Date(message.sentAt)));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemChatMessageBinding binding;

        Holder(ItemChatMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
