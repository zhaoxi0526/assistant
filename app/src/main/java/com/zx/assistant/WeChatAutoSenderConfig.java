package com.zx.assistant;

import android.content.Context;
import android.content.SharedPreferences;

public class WeChatAutoSenderConfig {
    private static WeChatAutoSenderConfig instance;
    private SharedPreferences sharedPreferences;

    // 配置键名
    private static final String KEY_KEYWORD = "keyword";
    private static final String KEY_WECHAT_ID = "wechat_id";
    private static final String KEY_IS_ENABLED = "is_enabled";
    private static final String KEY_LAST_SMS_CONTENT = "last_sms_content";
    private static final String KEY_TARGET_CONTACT = "target_contact";

    private WeChatAutoSenderConfig(Context context) {
        this.sharedPreferences = context.getSharedPreferences("wechat_auto_sender_prefs", Context.MODE_PRIVATE);
    }

    public static synchronized WeChatAutoSenderConfig getInstance(Context context) {
        if (instance == null) {
            instance = new WeChatAutoSenderConfig(context.getApplicationContext());
        }
        return instance;
    }

    // 获取和设置关键词
    public String getTriggerKeyword() {
        return sharedPreferences.getString(KEY_KEYWORD, "");
    }

    public void setTriggerKeyword(String value) {
        sharedPreferences.edit().putString(KEY_KEYWORD, value).apply();
    }

    // 获取和设置目标微信ID（兼容旧方法名）
    public String getTargetWeChatId() {
        return sharedPreferences.getString(KEY_WECHAT_ID, "zxupuper");
    }

    public void setTargetWeChatId(String value) {
        sharedPreferences.edit().putString(KEY_WECHAT_ID, value).apply();
    }

    // 获取和设置目标联系人（新方法名，用于区分微信ID和联系人）
    public String getTargetContact() {
        String contact = sharedPreferences.getString(KEY_TARGET_CONTACT, "");
        // 如果没有设置目标联系人，使用微信ID作为默认值
        return contact.isEmpty() ? getTargetWeChatId() : contact;
    }

    public void setTargetContact(String contact) {
        sharedPreferences.edit().putString(KEY_TARGET_CONTACT, contact).apply();
    }

    // 获取和设置功能启用状态
    public boolean isEnabled() {
        return sharedPreferences.getBoolean(KEY_IS_ENABLED, true);
    }

    public void setEnabled(boolean value) {
        sharedPreferences.edit().putBoolean(KEY_IS_ENABLED, value).apply();
    }

    // 获取和设置最后收到的短信内容
    public String getLastSmsContent() {
        return sharedPreferences.getString(KEY_LAST_SMS_CONTENT, "");
    }

    public void setLastSmsContent(String content) {
        sharedPreferences.edit().putString(KEY_LAST_SMS_CONTENT, content).apply();
    }

    // 重置为默认设置
    public void resetToDefault() {
        sharedPreferences.edit()
            .putString(KEY_KEYWORD, "")
            .putString(KEY_WECHAT_ID, "zxupuper")
            .putString(KEY_TARGET_CONTACT, "")
            .putBoolean(KEY_IS_ENABLED, true)
            .putString(KEY_LAST_SMS_CONTENT, "")
            .apply();
    }
}
