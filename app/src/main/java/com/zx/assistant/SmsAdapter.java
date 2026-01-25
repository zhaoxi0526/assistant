package com.zx.assistant;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SmsAdapter extends RecyclerView.Adapter<SmsAdapter.SmsViewHolder> {
    private List<SmsModel> smsList;

    public SmsAdapter(List<SmsModel> smsList) {
        this.smsList = smsList;
    }

    public static class SmsViewHolder extends RecyclerView.ViewHolder {
        public TextView addressTextView;
        public TextView bodyTextView;
        public TextView dateTextView;
        public TextView readStatusTextView;

        public SmsViewHolder(View view) {
            super(view);
            addressTextView = view.findViewById(R.id.addressTextView);
            bodyTextView = view.findViewById(R.id.bodyTextView);
            dateTextView = view.findViewById(R.id.dateTextView);
            readStatusTextView = view.findViewById(R.id.readStatusTextView);
        }
    }

    @Override
    public SmsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_sms, parent, false);
        return new SmsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(SmsViewHolder holder, int position) {
        SmsModel sms = smsList.get(position);
        holder.addressTextView.setText(sms.getAddress());
        holder.bodyTextView.setText(sms.getBody());
        holder.dateTextView.setText(sms.getFormattedDate());

        // Set read status indicator
        if (sms.getRead() == 1) {
            holder.readStatusTextView.setText("已读");
            holder.readStatusTextView.setTextColor(android.graphics.Color.GRAY);
        } else {
            holder.readStatusTextView.setText("未读");
            holder.readStatusTextView.setTextColor(android.graphics.Color.BLUE);
        }
    }

    @Override
    public int getItemCount() {
        return smsList.size();
    }
}