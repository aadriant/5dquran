package com.dquran.app5d;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private final Context context;
    private final List<ChatMessage> messages;

    public ChatAdapter(Context context, List<ChatMessage> messages) {
        this.context = context;
        this.messages = messages;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat_bubble, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);

        if (msg.isAdmin()) {
            holder.layoutAdminBubble.setVisibility(View.VISIBLE);
            holder.layoutUserBubble.setVisibility(View.GONE);

            holder.tvAdminSender.setText(msg.getUserName() != null && !msg.getUserName().isEmpty() ? msg.getUserName() : "Tim Pengembang 5D Quran");
            holder.tvAdminMessage.setText(msg.getPesan());
            holder.tvAdminTime.setText(msg.getCreatedAt());
        } else {
            holder.layoutAdminBubble.setVisibility(View.GONE);
            holder.layoutUserBubble.setVisibility(View.VISIBLE);

            String tipe = msg.getTipe() != null ? msg.getTipe() : "usulan";
            if ("kesalahan".equalsIgnoreCase(tipe)) {
                holder.tvUserCategory.setText("🚨 Kesalahan / Bug");
                holder.tvUserCategory.setTextColor(context.getResources().getColor(R.color.red_error));
            } else if ("tanya".equalsIgnoreCase(tipe)) {
                holder.tvUserCategory.setText("💬 Pertanyaan");
                holder.tvUserCategory.setTextColor(context.getResources().getColor(R.color.cyan_accent));
            } else {
                holder.tvUserCategory.setText("💡 Ide Usulan");
                holder.tvUserCategory.setTextColor(context.getResources().getColor(R.color.gold_light));
            }

            holder.tvUserMessage.setText(msg.getPesan());
            holder.tvUserTime.setText(msg.getCreatedAt());

            if (msg.isSync()) {
                holder.tvUserSyncStatus.setText("✓✓");
                holder.tvUserSyncStatus.setTextColor(context.getResources().getColor(R.color.emerald));
            } else {
                holder.tvUserSyncStatus.setText("✓");
                holder.tvUserSyncStatus.setTextColor(context.getResources().getColor(R.color.text_muted));
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View layoutUserBubble, layoutAdminBubble;
        TextView tvUserCategory, tvUserMessage, tvUserTime, tvUserSyncStatus;
        TextView tvAdminSender, tvAdminMessage, tvAdminTime;

        ViewHolder(View itemView) {
            super(itemView);
            layoutUserBubble   = itemView.findViewById(R.id.layout_user_bubble);
            tvUserCategory     = itemView.findViewById(R.id.tv_user_category);
            tvUserMessage      = itemView.findViewById(R.id.tv_user_message);
            tvUserTime         = itemView.findViewById(R.id.tv_user_time);
            tvUserSyncStatus   = itemView.findViewById(R.id.tv_user_sync_status);

            layoutAdminBubble  = itemView.findViewById(R.id.layout_admin_bubble);
            tvAdminSender      = itemView.findViewById(R.id.tv_admin_sender);
            tvAdminMessage     = itemView.findViewById(R.id.tv_admin_message);
            tvAdminTime        = itemView.findViewById(R.id.tv_admin_time);
        }
    }
}
