package com.zx.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "收到短信广播");

        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            // 检查功能是否启用
            WeChatAutoSenderConfig config = WeChatAutoSenderConfig.getInstance(context);
            Log.d(TAG, "功能启用状态: " + config.isEnabled());
            Log.d(TAG, "触发关键词: " + config.getTriggerKeyword());

            if (!config.isEnabled()) {
                Log.d(TAG, "功能已禁用，跳过处理");
                return;
            }

            android.os.Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    String format = bundle.getString("format");
                    SmsMessage[] messages = new SmsMessage[pdus.length];

                    for (int i = 0; i < pdus.length; i++) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                        } else {
                            messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                        }
                    }

                    if (messages.length > 0 && messages[0] != null) {
                        String sender = messages[0].getDisplayOriginatingAddress();
                        if (sender == null) sender = "";
                        StringBuilder messageBody = new StringBuilder();

                        for (SmsMessage message : messages) {
                            if (message != null) {
                                messageBody.append(message.getMessageBody());
                            }
                        }

                        String fullMessage = messageBody.toString();
                        Log.d(TAG, "发件人: " + sender + ", 内容: " + fullMessage);
                        Log.d(TAG, "关键词匹配结果: " + shouldTriggerWeChat(fullMessage, config));

                        // 检查短信是否满足触发条件
                        if (shouldTriggerWeChat(fullMessage, config)) {
                            Log.d(TAG, "检测到触发条件，准备启动微信");

                            // 保存联系人和消息到配置，供无障碍服务使用
                            config.setTargetContact(config.getTargetWeChatId()); // 设置目标联系人
                            config.setLastSmsContent("来自 " + sender + " 的短信: " + fullMessage); // 设置要发送的内容

                            // 启动微信并发送消息
                            openWeChatAndSendMessage(context, sender, fullMessage);

                            // 阻止其他应用接收此广播（可选）
                            // resultToAbort();
                        } else {
                            Log.d(TAG, "短信内容不满足触发条件");
                        }
                    } else {
                        Log.d(TAG, "无法解析短信内容");
                    }
                } else {
                    Log.d(TAG, "PDUs为null");
                }
            } else {
                Log.d(TAG, "Bundle为null");
            }
        } else {
            Log.d(TAG, "收到非SMS广播: " + intent.getAction());
        }
    }
    
    /**
     * 检查短信内容是否满足触发微信的条件
     */
    private boolean shouldTriggerWeChat(String messageBody, WeChatAutoSenderConfig config) {
        // 如果关键词为空或"*"，则对所有短信触发
        String keyword = config.getTriggerKeyword();
        if (keyword == null || keyword.trim().isEmpty() || "*".equals(keyword.trim())) {
            return true;
        }
        // 检查是否包含配置的关键词（忽略大小写）
        return messageBody.toLowerCase().contains(keyword.toLowerCase());
    }
    
    private void openWeChatAndSendMessage(Context context, String sender, String smsContent) {
        try {
            // 获取配置的目标联系人
            WeChatAutoSenderConfig config = WeChatAutoSenderConfig.getInstance(context);
            String targetContact = config.getTargetContact(); // 这应该是联系人微信号或昵称

            // 构建要发送的消息
            String message = "新短信通知:\n发件人: " + sender + "\n内容: " + smsContent;

            Log.d(TAG, "准备启动微信，目标联系人: " + targetContact + ", 消息: " + message);

            // 尝试使用微信URI scheme直接发送消息
            boolean success = sendWeChatMessage(context, targetContact, message);

            if (!success) {
                Log.d(TAG, "URI scheme启动微信失败，尝试启动微信应用");

                // 如果URI scheme失败，直接启动微信应用
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
                if (launchIntent != null) {
                    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                    Log.d(TAG, "成功启动微信应用");
                } else {
                    Log.e(TAG, "无法启动微信应用");
                }
            } else {
                Log.d(TAG, "通过URI scheme成功启动微信");
            }

            // 同时发送通知作为备用方案
            NotificationHelper.createNotificationChannel(context);
            NotificationHelper.sendNotification(context, sender, smsContent);
        } catch (Exception e) {
            Log.e(TAG, "启动微信失败: " + e.getMessage());
            e.printStackTrace();
            // 作为备选方案，显示一个Toast
            Toast.makeText(context, "收到新短信: " + smsContent, Toast.LENGTH_LONG).show();
        }
    }

    private boolean sendWeChatMessage(Context context, String wxId, String message) {
        try {
            // 首先尝试使用微信URI scheme打开聊天界面
            android.net.Uri uri = android.net.Uri.parse("weixin://dl/chat?chatid=" + wxId);
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, uri);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

            // 检查是否有应用可以处理这个Intent
            android.content.pm.PackageManager pm = context.getPackageManager();
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent);
                Log.d(TAG, "成功通过URI scheme启动微信聊天界面");
                return true;
            } else {
                Log.d(TAG, "设备上没有应用可以处理微信URI scheme");

                // 尝试直接通过组件名启动微信
                try {
                    intent = new android.content.Intent();
                    intent.setComponent(new android.content.ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI"));
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Log.d(TAG, "成功通过组件名启动微信: com.tencent.mm/.ui.LauncherUI");
                    return true;
                } catch (Exception componentException) {
                    Log.d(TAG, "通过组件名启动微信失败: " + componentException.getMessage());
                }

                // 如果直接启动失败，尝试通过包名启动
                intent = pm.getLaunchIntentForPackage("com.tencent.mm");
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Log.d(TAG, "成功通过包名启动微信主界面");
                    return true;
                } else {
                    Log.d(TAG, "无法通过包名启动微信");

                    // 尝试查找所有可能的微信包名
                    java.util.List<android.content.pm.ApplicationInfo> packages = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA);

                    for (android.content.pm.ApplicationInfo packageInfo : packages) {
                        String packageName = packageInfo.packageName.toLowerCase();
                        String appName = pm.getApplicationLabel(packageInfo).toString().toLowerCase();

                        if (packageName.contains("tencent") || packageName.contains("wechat") || appName.contains("微信")) {
                            intent = pm.getLaunchIntentForPackage(packageInfo.packageName);
                            if (intent != null) {
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);
                                Log.d(TAG, "成功通过包名启动微信: " + packageInfo.packageName);
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "启动微信失败: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    private android.content.Intent findWeChatIntent(Context context) {
        // 首先尝试标准的微信包名
        String[] possibleWeChatPackages = {
            "com.tencent.mm",           // 标准微信
            "com.tencent.mm.mini",      // 微信小程序
            "com.tencent.liteapp"       // 微信轻应用
        };

        android.content.pm.PackageManager pm = context.getPackageManager();

        // 尝试已知的微信包名
        for (String packageName : possibleWeChatPackages) {
            android.content.Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent != null) {
                Log.d(TAG, "通过标准包名找到微信: " + packageName);
                return intent;
            } else {
                Log.d(TAG, "标准包名未找到微信: " + packageName);
            }
        }

        // 如果标准包名找不到，尝试从所有应用中查找
        try {
            java.util.List<android.content.pm.ApplicationInfo> packages = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA);

            Log.d(TAG, "正在扫描 " + packages.size() + " 个已安装的应用");

            for (android.content.pm.ApplicationInfo packageInfo : packages) {
                // 检查是否是系统应用或更新的系统应用
                if ((packageInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (packageInfo.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {

                    // 检查应用名称或包名是否包含微信相关关键词
                    String appName = pm.getApplicationLabel(packageInfo).toString().toLowerCase();
                    String packageName = packageInfo.packageName.toLowerCase();

                    Log.d(TAG, "检查应用: " + packageName + " (" + appName + ")");

                    if (appName.contains("微信") || appName.contains("wechat") ||
                        packageName.contains("tencent") || packageName.contains("mm")) {

                        android.content.Intent intent = pm.getLaunchIntentForPackage(packageInfo.packageName);
                        if (intent != null) {
                            Log.d(TAG, "通过关键词找到微信应用: " + packageInfo.packageName + " (" + appName + ")");
                            return intent;
                        }
                    }
                }
            }

            // 如果仍然找不到，列出所有可能的腾讯应用
            StringBuilder tencentApps = new StringBuilder("所有腾讯相关应用:\n");
            for (android.content.pm.ApplicationInfo packageInfo : packages) {
                String packageName = packageInfo.packageName.toLowerCase();
                if (packageName.contains("tencent") || packageName.contains("wechat") || packageName.contains("mm")) {
                    tencentApps.append("  ").append(packageInfo.packageName).append("\n");
                    Log.d(TAG, "发现腾讯相关应用: " + packageInfo.packageName);
                }
            }

            Log.e(TAG, "未找到微信应用。腾讯相关应用:\n" + tencentApps.toString());
        } catch (Exception e) {
            Log.e(TAG, "查找微信应用时出错: " + e.getMessage());
        }

        Log.d(TAG, "未找到微信应用");
        return null;
    }

    private boolean isAccessibilityServiceEnabled(Context context) {
        String service = context.getPackageName() + "/" + WeChatAutoSendService.class.getCanonicalName();
        String enabledServices = android.provider.Settings.Secure.getString(
            context.getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        return enabledServices != null && enabledServices.contains(service);
    }
}