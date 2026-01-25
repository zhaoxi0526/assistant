package com.zx.assistant;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView smsRecyclerView;
    private Button requestPermissionButton;
    private EditText keywordEditText;
    private EditText wechatIdEditText;
    private Switch enableSwitch;
    private Button saveConfigButton;
    private SmsAdapter smsAdapter;
    private SmsHelper smsHelper;
    private WeChatAutoSenderConfig config;

    private static final int SMS_PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        smsHelper = new SmsHelper(this);
        config = WeChatAutoSenderConfig.getInstance(this);

        initViews();
        loadConfig();
        checkSmsPermission();
    }

    private void initViews() {
        smsRecyclerView = findViewById(R.id.smsRecyclerView);
        requestPermissionButton = findViewById(R.id.requestPermissionButton);
        keywordEditText = findViewById(R.id.keywordEditText);
        wechatIdEditText = findViewById(R.id.wechatIdEditText);
        enableSwitch = findViewById(R.id.enableSwitch);
        saveConfigButton = findViewById(R.id.saveConfigButton);

        requestPermissionButton.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                requestSmsPermission();
            }
        });

        saveConfigButton.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                saveConfig();
            }
        });

        // 添加测试微信连接按钮
        Button testWeChatButton = findViewById(R.id.testWeChatButton);
        testWeChatButton.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                testWeChatConnection();
            }
        });
    }

    private void loadConfig() {
        keywordEditText.setText(config.getTriggerKeyword());
        wechatIdEditText.setText(config.getTargetWeChatId());
        enableSwitch.setChecked(config.isEnabled());
    }

    private void saveConfig() {
        config.setTriggerKeyword(keywordEditText.getText().toString());
        config.setTargetWeChatId(wechatIdEditText.getText().toString());
        config.setEnabled(enableSwitch.isChecked());

        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
    }

    private void testWeChatConnection() {
        try {
            // 获取配置的目标微信ID
            String targetWeChatId = config.getTargetWeChatId();
            if (targetWeChatId == null || targetWeChatId.trim().isEmpty()) {
                Toast.makeText(this, "请先设置目标微信ID", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取最近收到的短信
            SmsHelper smsHelper = new SmsHelper(this);
            List<SmsModel> smsList = smsHelper.getAllSms();

            if (smsList.isEmpty()) {
                Toast.makeText(this, "没有找到短信记录", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取最新的一条短信
            SmsModel latestSms = smsList.get(0);
            String smsContent = "来自 " + latestSms.getAddress() + " 的短信: " + latestSms.getBody();

            android.util.Log.d("TestWeChat", "目标联系人: " + targetWeChatId);
            android.util.Log.d("TestWeChat", "短信内容: " + smsContent);

            // 保存联系人和消息到配置，供无障碍服务使用
            config.setTargetContact(targetWeChatId); // 使用新的方法名
            config.setLastSmsContent(smsContent);

            // 检查无障碍服务是否已启用
            boolean accessibilityEnabled = isAccessibilityServiceEnabled();
            android.util.Log.d("TestWeChat", "无障碍服务是否启用: " + accessibilityEnabled);

            if (!accessibilityEnabled) {
                Toast.makeText(this, "请先在系统设置中启用无障碍服务", Toast.LENGTH_LONG).show();
                // 引导用户到无障碍设置页面
                Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                return;
            }

            // 停止并重新启动服务以确保状态重置
            stopWeChatAssistantService();

            // 等待一段时间确保服务停止
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // **关键修改：先启动服务，再启动微信**
                startWeChatAssistantService();
            }, 1000); // 延迟1秒启动服务

        } catch (Exception e) {
            Toast.makeText(this, "启动微信失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            android.util.Log.e("TestWeChat", "启动微信失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 启动微信助手服务（避免前台服务超时）
     */
    private void startWeChatAssistantService() {
        try {
            android.util.Log.d("TestWeChat", "准备启动微信助手服务");

            // 方法1：使用延迟启动，确保服务稳定
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent serviceIntent = new Intent(MainActivity.this, WeChatAutoSendService.class);

                        // 添加启动标识
                        serviceIntent.putExtra("action", "start_wechat_operation");
                        serviceIntent.putExtra("target_contact", config.getTargetWeChatId());
                        serviceIntent.putExtra("message", config.getLastSmsContent());

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            // 启动前台服务
                            startForegroundService(serviceIntent);
                            android.util.Log.d("TestWeChat", "✅ 已调用startForegroundService");
                        } else {
                            // Android 8.0以下使用普通startService
                            startService(serviceIntent);
                            android.util.Log.d("TestWeChat", "✅ 已调用startService");
                        }

                        // 服务启动后，再启动微信
                        launchWeChatApp();

                    } catch (Exception e) {
                        android.util.Log.e("TestWeChat", "启动服务异常: " + e.getMessage());
                        Toast.makeText(MainActivity.this, "启动服务失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }, 500); // 延迟500ms启动，确保主线程空闲
        } catch (Exception e) {
            android.util.Log.e("TestWeChat", "启动服务失败: " + e.getMessage());
        }
    }

    private void stopWeChatAssistantService() {
        Intent serviceIntent = new Intent(this, WeChatAutoSendService.class);
        try {
            stopService(serviceIntent);
            android.util.Log.d("TestWeChat", "✅ 已尝试停止服务");
        } catch (Exception e) {
            android.util.Log.e("TestWeChat", "停止服务失败: " + e.getMessage());
        }
    }

    /**
     * 启动微信应用
     */
    private void launchWeChatApp() {
        android.util.Log.d("TestWeChat", "开始启动微信");

        // 延迟启动微信，确保服务已就绪
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // 方法1：通过组件名启动
                    android.content.Intent launchIntent = new android.content.Intent();
                    launchIntent.setComponent(new android.content.ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI"));
                    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

                    startActivity(launchIntent);
                    Toast.makeText(MainActivity.this, "成功启动微信，准备发送消息", Toast.LENGTH_SHORT).show();
                    android.util.Log.d("TestWeChat", "✅ 成功通过组件名启动微信");

                } catch (Exception componentException) {
                    android.util.Log.w("TestWeChat", "通过组件名启动微信失败: " + componentException.getMessage());

                    // 方法2：通过包名启动
                    try {
                        android.content.pm.PackageManager pm = getPackageManager();
                        android.content.Intent launchIntent = pm.getLaunchIntentForPackage("com.tencent.mm");

                        if (launchIntent != null) {
                            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(launchIntent);
                            Toast.makeText(MainActivity.this, "成功启动微信", Toast.LENGTH_SHORT).show();
                            android.util.Log.d("TestWeChat", "✅ 通过包名启动微信");
                        } else {
                            android.util.Log.e("TestWeChat", "未找到微信应用");
                            Toast.makeText(MainActivity.this, "未找到微信应用", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        android.util.Log.e("TestWeChat", "启动微信失败: " + e.getMessage());
                        Toast.makeText(MainActivity.this, "启动微信失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }, 1000); // 延迟1秒启动微信，给服务留出启动时间
    }

    private SmsReceiver smsReceiver;

    private void checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted, load SMS and register SMS receiver
            loadSms();
            registerSmsReceiver();
        } else {
            // Permission not granted, show button to request
            smsRecyclerView.setVisibility(android.view.View.GONE);
            requestPermissionButton.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void registerSmsReceiver() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
            smsReceiver = new SmsReceiver();
            IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
            filter.setPriority(2147483647); // 设置最高优先级
            registerReceiver(smsReceiver, filter);
            android.util.Log.d("MainActivity", "已注册短信接收器");
        } else {
            android.util.Log.e("MainActivity", "没有 RECEIVE_SMS 权限");
        }
    }

    private void unregisterSmsReceiver() {
        if (smsReceiver != null) {
            try {
                unregisterReceiver(smsReceiver);
                android.util.Log.d("MainActivity", "已注销短信接收器");
            } catch (IllegalArgumentException e) {
                android.util.Log.e("MainActivity", "注销短信接收器时出错: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterSmsReceiver();
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + WeChatAutoSendService.class.getCanonicalName();
        String enabledServices = android.provider.Settings.Secure.getString(
            getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        return enabledServices != null && enabledServices.contains(service);
    }

    private void requestSmsPermission() {
        List<String> permissions = new ArrayList<>();

        permissions.add(Manifest.permission.READ_SMS);
        permissions.add(Manifest.permission.READ_PHONE_STATE);

        // 检查是否是Android 13+，如果是，添加通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        ActivityCompat.requestPermissions(
            this,
            permissions.toArray(new String[0]),
            SMS_PERMISSION_REQUEST_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, load SMS and register SMS receiver
                loadSms();
                registerSmsReceiver();
            } else {
                // Permission denied
                Toast.makeText(this, "需要短信权限才能读取短信", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadSms() {
        // Hide permission button and show recycler view
        requestPermissionButton.setVisibility(android.view.View.GONE);
        smsRecyclerView.setVisibility(android.view.View.VISIBLE);

        // Get all SMS from helper
        List<SmsModel> smsList = smsHelper.getAllSms();

        // Initialize adapter and set to recycler view
        smsAdapter = new SmsAdapter(smsList);
        smsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        smsRecyclerView.setAdapter(smsAdapter);
    }
}
