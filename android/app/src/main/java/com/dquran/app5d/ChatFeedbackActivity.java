package com.dquran.app5d;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatFeedbackActivity extends AppCompatActivity {

    private RecyclerView rvMessages;
    private ChatAdapter adapter;
    private final List<ChatMessage> messageList = new ArrayList<>();

    private EditText etInput;
    private View btnSend;
    private ProgressBar pbRefreshing;

    private TextView chipKesalahan, chipUsulan, chipTanya;
    private String selectedTipe = "kesalahan"; // default: kesalahan / bug

    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshRunnable;
    private boolean isResumed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_feedback);

        initViews();
        loadLocalMessages();
        setupCategoryChips();
        setupAutoRefresh();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_back_chat);
        btnBack.setOnClickListener(v -> finish());

        pbRefreshing = findViewById(R.id.pb_chat_refreshing);
        rvMessages   = findViewById(R.id.rv_chat_messages);
        etInput      = findViewById(R.id.et_chat_input);
        btnSend      = findViewById(R.id.btn_chat_send);

        chipKesalahan = findViewById(R.id.chip_tipe_kesalahan);
        chipUsulan    = findViewById(R.id.chip_tipe_usulan);
        chipTanya     = findViewById(R.id.chip_tipe_tanya);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);

        adapter = new ChatAdapter(this, messageList);
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> doSendMessage());
    }

    private void setupCategoryChips() {
        updateChipStyles();

        chipKesalahan.setOnClickListener(v -> {
            selectedTipe = "kesalahan";
            updateChipStyles();
        });

        chipUsulan.setOnClickListener(v -> {
            selectedTipe = "usulan";
            updateChipStyles();
        });

        chipTanya.setOnClickListener(v -> {
            selectedTipe = "tanya";
            updateChipStyles();
        });
    }

    private void updateChipStyles() {
        // Kesalahan
        if ("kesalahan".equals(selectedTipe)) {
            chipKesalahan.setBackgroundResource(R.drawable.bg_badge_number);
            chipKesalahan.setTextColor(getResources().getColor(R.color.red_error));
        } else {
            chipKesalahan.setBackgroundResource(R.drawable.bg_chip_action);
            chipKesalahan.setTextColor(getResources().getColor(R.color.text_muted));
        }

        // Usulan
        if ("usulan".equals(selectedTipe)) {
            chipUsulan.setBackgroundResource(R.drawable.bg_badge_number);
            chipUsulan.setTextColor(getResources().getColor(R.color.gold_primary));
        } else {
            chipUsulan.setBackgroundResource(R.drawable.bg_chip_action);
            chipUsulan.setTextColor(getResources().getColor(R.color.text_muted));
        }

        // Tanya
        if ("tanya".equals(selectedTipe)) {
            chipTanya.setBackgroundResource(R.drawable.bg_badge_number);
            chipTanya.setTextColor(getResources().getColor(R.color.cyan_accent));
        } else {
            chipTanya.setBackgroundResource(R.drawable.bg_chip_action);
            chipTanya.setTextColor(getResources().getColor(R.color.text_muted));
        }
    }

    private void loadLocalMessages() {
        List<ChatMessage> cached = FeedbackManager.getLocalMessages(this);
        messageList.clear();
        messageList.addAll(cached);
        adapter.notifyDataSetChanged();
        scrollToBottom();
    }

    private void doSendMessage() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Silakan tuliskan pesan Anda terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        etInput.setText("");
        FeedbackManager.sendMessage(this, selectedTipe, text, new FeedbackManager.FeedbackCallback() {
            @Override
            public void onSuccess(List<ChatMessage> messages) {
                messageList.clear();
                messageList.addAll(messages);
                adapter.notifyDataSetChanged();
                scrollToBottom();
            }

            @Override
            public void onError(String errorMessage) {
                loadLocalMessages();
            }
        });
    }

    private void setupAutoRefresh() {
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (isResumed) {
                    syncMessagesFromServer();
                    autoRefreshHandler.postDelayed(this, 12000); // Poll setiap 12 detik saat layar aktif
                }
            }
        };
    }

    private void syncMessagesFromServer() {
        if (pbRefreshing != null) pbRefreshing.setVisibility(View.VISIBLE);
        FeedbackManager.fetchServerMessages(this, new FeedbackManager.FeedbackCallback() {
            @Override
            public void onSuccess(List<ChatMessage> messages) {
                if (pbRefreshing != null) pbRefreshing.setVisibility(View.GONE);
                int oldSize = messageList.size();
                messageList.clear();
                messageList.addAll(messages);
                adapter.notifyDataSetChanged();
                if (messageList.size() > oldSize) {
                    scrollToBottom();
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (pbRefreshing != null) pbRefreshing.setVisibility(View.GONE);
            }
        });
    }

    private void scrollToBottom() {
        if (!messageList.isEmpty()) {
            rvMessages.post(() -> rvMessages.smoothScrollToPosition(messageList.size() - 1));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        loadLocalMessages();
        syncMessagesFromServer();
        autoRefreshHandler.postDelayed(autoRefreshRunnable, 12000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }
}
