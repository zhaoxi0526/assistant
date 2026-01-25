package com.zx.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class NotificationHelper {
    static final String CHANNEL_ID = "wechat_sms_channel";
    static final int NOTIFICATION_ID = 1;

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "微信短信通知";
            String description = "用于通知新短信并启动微信";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static void sendNotification(Context context, String sender, String smsContent) {
        // 创建启动微信的意图
        Intent weChatIntent = context.getPackageManager()
            .getLaunchIntentForPackage("com.tencent.mm");

        // 创建启动微信的PendingIntent
        PendingIntent weChatPendingIntent = null;
        if (weChatIntent != null) {
            weChatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                weChatPendingIntent = PendingIntent.getActivity(context, 0, weChatIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                weChatPendingIntent = PendingIntent.getActivity(context, 0, weChatIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }

        // 创建复制短信内容到剪贴板的意图
        Intent clipboardIntent = new Intent();
        clipboardIntent.setAction(Intent.ACTION_SEND);
        clipboardIntent.putExtra(Intent.EXTRA_TEXT, "发件人: " + sender + "\n内容: " + smsContent);
        clipboardIntent.setType("text/plain");

        PendingIntent clipboardPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            clipboardPendingIntent = PendingIntent.getActivity(context, 1, clipboardIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            clipboardPendingIntent = PendingIntent.getActivity(context, 1, clipboardIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("微信短信通知")
            .setContentText("新短信: 来自 " + sender)
            .setStyle(new NotificationCompat.BigTextStyle().bigText("发件人: " + sender + "\n内容: " + smsContent))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true);

        // 添加操作按钮
        if (weChatPendingIntent != null) {
            builder.addAction(0, "打开微信", weChatPendingIntent);
        }
        builder.addAction(0, "复制内容", clipboardPendingIntent);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}
