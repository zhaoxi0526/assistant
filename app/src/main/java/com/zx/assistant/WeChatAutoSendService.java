package com.zx.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.zx.assistant.WeChatAutoSenderConfig;

import java.util.ArrayList;
import java.util.List;

import static com.zx.assistant.NotificationHelper.CHANNEL_ID;
import static com.zx.assistant.NotificationHelper.NOTIFICATION_ID;

/**
 * 通过搜索功能查找并联系指定联系人
 */
public class WeChatAutoSendService extends AccessibilityService {
    private static final String TAG = "WeChatAutoSendService";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private String targetContact = "";
    private String messageToSend = "";
    private boolean isForegroundStarted = false;

    // 添加状态管理，避免重复操作
    private enum OperationState {
        IDLE,           // 空闲
        LAUNCHING_WECHAT, // 启动微信
        SEARCHING,      // 正在搜索
        INPUTTING_NAME, // 输入联系人名称
        SELECTING_CONTACT, // 选择联系人
        WAITING_FOR_CHAT_INTERFACE, // 等待聊天界面加载
        SENDING_MESSAGE,   // 发送消息
        COMPLETED       // 完成
    }

    private OperationState currentState = OperationState.IDLE;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;
    private Handler mainHandler;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "无障碍服务已连接");

        mainHandler = new Handler(Looper.getMainLooper());

        // 从配置中获取联系人和消息
        WeChatAutoSenderConfig config = WeChatAutoSenderConfig.getInstance(this);
        this.targetContact = config.getTargetContact();
        this.messageToSend = config.getLastSmsContent();

        Log.d(TAG, "目标联系人: " + targetContact + ", 消息: " + messageToSend);

        // 重置状态
        currentState = OperationState.IDLE; // 改为IDLE状态，等待事件触发
        retryCount = 0;

        Log.d(TAG, "服务已连接，等待事件触发...");
    }

    private void launchWeChatApp() {
        // 尝试通过组件名启动微信
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        try {
            Log.d(TAG, "尝试通过组件名启动微信: com.tencent.mm/.ui.LauncherUI");
            startActivity(intent);
            Log.d(TAG, "成功通过组件名启动微信: com.tencent.mm/.ui.LauncherUI");

            // 延迟一段时间，等待微信完全启动
            mainHandler.postDelayed(() -> {
                Log.d(TAG, "微信启动后延迟，准备进入搜索阶段");
                currentState = OperationState.SEARCHING;
            }, 3000); // 等待3秒让微信完全加载

        } catch (Exception e) {
            Log.e(TAG, "通过组件名启动微信失败: " + e.getMessage());
            e.printStackTrace();

            // 如果组件名启动失败，尝试包名启动
            intent = getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
            if (intent != null) {
                Log.d(TAG, "通过包名找到微信应用: com.tencent.mm");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                try {
                    startActivity(intent);
                    Log.d(TAG, "通过包名成功启动微信");

                    // 延迟一段时间，等待微信完全启动
                    mainHandler.postDelayed(() -> {
                        Log.d(TAG, "微信启动后延迟，准备进入搜索阶段");
                        currentState = OperationState.SEARCHING;
                    }, 3000); // 等待3秒让微信完全加载

                } catch (Exception ex) {
                    Log.e(TAG, "通过包名启动微信失败: " + ex.getMessage());
                    ex.printStackTrace();
                }
            } else {
                Log.e(TAG, "未安装微信或无法通过包名找到微信应用");
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        android.util.Log.d(TAG, "服务 onCreate()");

        // 立即创建通知渠道
        createNotificationChannel();

        // 立即启动前台服务，不等待onStartCommand
        startForegroundImmediately();
    }

    /**
     * 立即启动前台服务（不依赖onStartCommand）
     */
    private void startForegroundImmediately() {
        Log.d(TAG, "立即启动前台服务");

        try {
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }

            // 使用应用自己的图标，不要用系统图标
            int iconResId = getApplicationInfo().icon;
            if (iconResId == 0) {
                iconResId = android.R.drawable.sym_def_app_icon;
            }

            Notification notification = builder
                    .setContentTitle("微信助手")
                    .setContentText("服务运行中...")
                    .setSmallIcon(iconResId)  // 使用应用图标
                    .setPriority(Notification.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();

            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "✅ 前台服务已启动 (在onCreate中)");
            isForegroundStarted = true;

        } catch (Exception e) {
            Log.e(TAG, "启动前台服务失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");

        // 确保前台服务已启动
        if (!isForegroundStarted) {
            startForegroundImmediately();
        }

        return START_STICKY;
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                android.app.NotificationChannel serviceChannel = new android.app.NotificationChannel(
                        CHANNEL_ID,
                        "微信助手服务",
                        android.app.NotificationManager.IMPORTANCE_LOW
                );
                serviceChannel.setDescription("微信自动发送服务正在运行");
                serviceChannel.setSound(null, null);
                serviceChannel.setVibrationPattern(null);

                android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(serviceChannel);
                    android.util.Log.d(TAG, "通知渠道创建成功");
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "创建通知渠道失败: " + e.getMessage());
            }
        }
    }


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!WECHAT_PACKAGE.equals(event.getPackageName() != null ? event.getPackageName().toString() : "")) {
            // 检查是否是微信启动事件，如果是，可能需要重新初始化
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                "com.tencent.mm.ui.LauncherUI".equals(event.getClassName())) {
                Log.d(TAG, "检测到微信启动事件，重新初始化");
                // 从配置中获取联系人和消息
                WeChatAutoSenderConfig config = WeChatAutoSenderConfig.getInstance(this);
                this.targetContact = config.getTargetContact();
                this.messageToSend = config.getLastSmsContent();

                // 只新启动流程
                if (currentState == OperationState.IDLE || currentState == OperationState.COMPLETED) {
                    currentState = OperationState.LAUNCHING_WECHAT;
                    Log.d(TAG, "重新初始化完成，目标联系人: " + targetContact);
                }
            }
            return;
        }

        String className = event.getClassName() != null ? event.getClassName().toString() : "";
        int eventType = event.getEventType();

        Log.d(TAG, "微信事件 - 类型: " + eventType + ", 类名: " + className + ", 当前状态: " + currentState);

        // 避免在某些状态下重复执行相同操作
        if ((eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
             eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) &&
            (currentState == OperationState.SELECTING_CONTACT || currentState == OperationState.SENDING_MESSAGE)) {
            // 在这些状态下，点击事件可能是我们自己触发的，避免重复处理
            Log.d(TAG, "忽略可能由自身操作触发的事件，类型: " + eventType);
            return;
        }

        // 特别处理聊天界面的进入
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            currentState == OperationState.INPUTTING_NAME &&
            className.contains("ListView")) {
            // 在输入名字后，如果检测到ListView中的点击事件，可能是点击了联系人
            Log.d(TAG, "检测到搜索结果列表中的点击事件，准备进入聊天界面");
            // 不要在这里return，而是允许状态转换
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.d(TAG, "getRootInActiveWindow返回null");
            return;
        }

        try {
            // 根据当前状态和界面类型执行相应操作
            switch (currentState) {
                case LAUNCHING_WECHAT:
                    // 等待微信启动完成
                    if (isWeChatMainScreen(className)) {
                        Log.d(TAG, "检测到微信主界面，准备点击搜索");
                        mainHandler.postDelayed(() -> {
                            clickSearchButton(rootNode);
                            currentState = OperationState.SEARCHING;
                        }, 2000); // 增加延时，确保微信完全加载
                    } else {
                        // 如果当前状态是LAUNCHING_WECHAT但不在主界面，可能需要启动微信
                        Log.d(TAG, "不在微信主界面，尝试启动微信");
                        launchWeChatApp();
                    }
                    break;

                case SEARCHING:
                    // 确认进入了搜索界面
                    if (isSearchScreen(className)) {
                        Log.d(TAG, "确认进入搜索界面，准备输入联系人名称");
                        mainHandler.postDelayed(() -> {
                            inputContactName(rootNode, targetContact);
                            currentState = OperationState.INPUTTING_NAME;
                        }, 1000);
                    } else if (isWeChatMainScreen(className)) {
                        // 如果还在主界面，再次尝试点击搜索
                        Log.d(TAG, "仍在微信主界面，再次尝试点击搜索");
                        clickSearchButton(rootNode);
                    }
                    break;

                case INPUTTING_NAME:
                    // 确认在搜索结果界面
                    Log.d(TAG, "当前界面类名: " + className);
                    if (isSearchResultScreen(className) || isContactSearchScreen(className) || className.contains("ListView")) {
                        Log.d(TAG, "进入搜索结果界面，准备选择联系人: " + targetContact);
                        // 只执行一次选择操作
                        if (currentState == OperationState.INPUTTING_NAME) {
                            // 先更新状态，然后选择联系人
                            currentState = OperationState.SELECTING_CONTACT;
                            mainHandler.postDelayed(() -> {
                                selectContactFromResults(rootNode, targetContact);
                            }, 2000); // 增加等待时间，确保搜索结果完全加载
                        }
                    } else {
                        // 如果不在预期的搜索结果界面，继续等待
                        Log.d(TAG, "还未到达搜索结果界面，当前界面: " + className);
                    }
                    break;

                case SELECTING_CONTACT:
                    // 确认进入了聊天界面
                    Log.d(TAG, "SELECTING_CONTACT状态，当前界面类名: " + className + "，类型: " + eventType);
                    if (isChatScreen(className)) {
                        Log.d(TAG, "进入聊天界面，准备发送消息: " + messageToSend);
                        // 更新状态并发送消息
                        if (currentState == OperationState.SELECTING_CONTACT) {
                            currentState = OperationState.SENDING_MESSAGE;
                            mainHandler.postDelayed(() -> {
                                sendMessageInChat(rootNode, messageToSend);
                            }, 1500); // 稍微延长等待时间，确保界面完全加载
                        }
                    } else if (className.contains("EditText")) {
                        // 如果是EditText但不是聊天界面，可能是还在搜索界面
                        Log.d(TAG, "检测到EditText，但不是聊天界面，当前界面: " + className);

                        // 检查是否是聊天输入框（通常在聊天界面中）
                        if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

                            // 尝试再次确认是否为聊天界面
                            if (isLikelyChatInterface(rootNode)) {
                                Log.d(TAG, "确认为聊天界面，准备发送消息: " + messageToSend);
                                if (currentState == OperationState.SELECTING_CONTACT) {
                                    currentState = OperationState.SENDING_MESSAGE;
                                    mainHandler.postDelayed(() -> {
                                        sendMessageInChat(rootNode, messageToSend);
                                    }, 1000);
                                }
                            }
                        }
                    } else {
                        // 如果还没进入聊天界面，继续等待
                        Log.d(TAG, "还未进入聊天界面，当前界面: " + className);
                    }
                    break;

                case WAITING_FOR_CHAT_INTERFACE:
                    // 等待聊天界面加载
                    Log.d(TAG, "WAITING_FOR_CHAT_INTERFACE状态，当前界面类名: " + className + "，类型: " + eventType);
                    if (isChatScreen(className) || (className.contains("EditText") && isLikelyChatInterface(rootNode))) {
                        Log.d(TAG, "检测到聊天界面，准备发送消息: " + messageToSend);
                        currentState = OperationState.SENDING_MESSAGE;
                        mainHandler.postDelayed(() -> {
                            sendMessageInChat(rootNode, messageToSend);
                        }, 1000);
                    } else {
                        // 继续等待界面变化
                        Log.d(TAG, "仍在等待聊天界面加载，当前界面: " + className);

                        // 检查是否仍在搜索界面
                        if (isSearchResultScreen(className) || className.contains("ListView")) {
                            Log.d(TAG, "仍在搜索结果界面，可能点击未成功，尝试重新点击");
                            // 可能需要重新尝试点击，但为了避免无限循环，我们不自动重试
                        }
                    }
                    break;

                case SENDING_MESSAGE:
                    // 消息已发送，操作完成
                    Log.d(TAG, "SENDING_MESSAGE状态，当前界面: " + className + "，类型: " + eventType);
                    if (isChatScreen(className) || className.contains("EditText")) {
                        Log.d(TAG, "确认在聊天界面，等待消息发送完成");
                        // 确保只执行一次完成操作
                        if (currentState == OperationState.SENDING_MESSAGE) {
                            currentState = OperationState.COMPLETED;

                            // 完成后先返回微信主界面，然后退出微信
                            mainHandler.postDelayed(() -> {
                                // 第一次返回：从聊天界面返回到聊天列表
                                performGlobalAction(GLOBAL_ACTION_BACK);

                                mainHandler.postDelayed(() -> {
                                    // 第二次返回：从聊天列表返回到微信主界面
                                    performGlobalAction(GLOBAL_ACTION_BACK);

                                    mainHandler.postDelayed(() -> {
                                        // 尝退出微信应用
                                        // 方法1：使用全局动作BACK键返回主界面
                                        performGlobalAction(GLOBAL_ACTION_BACK);

                                        // 方法2：尝试使用HOME键返回桌面
                                        mainHandler.postDelayed(() -> {
                                            performGlobalAction(GLOBAL_ACTION_HOME);
                                            Log.d(TAG, "已尝试通过HOME键退出微信");
                                        }, 1000);

                                        Log.d(TAG, "已尝试退出微信");
                                    }, 1500); // 等待1.5秒再执行退出操作
                                }, 1500); // 等待1.5秒再返回主界面
                            }, 2000); // 等待2秒让消息发送完成后再返回
                        }
                    }
                    break;

                case COMPLETED:
                    // 操作完成
                    Log.d(TAG, "所有操作已完成");
                    // 重置状态，准备下次操作
                    mainHandler.postDelayed(() -> {
                        currentState = OperationState.IDLE;
                        Log.d(TAG, "状态已重置为IDLE，准备下次操作");
                    }, 5000); // 5秒后重置状态，确保退出操作完成
                    break;

                case IDLE:
                    // 如果意外回到空闲状态，重新开始
                    if (isWeChatMainScreen(className)) {
                        Log.d(TAG, "意外回到主界面，重新开始流程");
                        mainHandler.postDelayed(() -> {
                            clickSearchButton(rootNode);
                            currentState = OperationState.SEARCHING;
                        }, 1000);
                    } else if (WECHAT_PACKAGE.equals(event.getPackageName() != null ? event.getPackageName().toString() : "")) {
                        // 如果在微信应用中但不是主界面，可能需要重新初始化
                        Log.d(TAG, "在微信应用中，但不在主界面，可能需要重新开始");
                    }
                    break;
            }
        } finally {
            rootNode.recycle();
        }
    }

    /**
     * 判断是否为联系人搜索界面
     */
    private boolean isContactSearchScreen(String className) {
        if (className == null) {
            return false;
        }
        return className.contains("Contact") || className.contains("contact") || className.contains("search_contact");
    }

    /**
     * 点击搜索按钮（放大镜图标）
     */
    private void clickSearchButton(AccessibilityNodeInfo rootNode) {
        Log.d(TAG, "开始寻找搜索按钮");

        // 方法1：通过内容描述查找
        String[] searchDescriptions = {"搜索", "Search", "search", "查找", "搜索栏", "搜索框", "放大镜"};

        for (String desc : searchDescriptions) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(desc);
            Log.d(TAG, "通过描述 '" + desc + "' 找到 " + nodes.size() + " 个节点");

            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable() && node.isVisibleToUser()) {
                    Log.d(TAG, "找到可点击的搜索按钮，描述: " + desc);
                    boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "点击搜索按钮结果: " + clicked);

                    if (clicked) {
                        node.recycle();
                        return;
                    }
                }
                node.recycle();
            }
        }

        // 方法2：查找ImageView（放大镜通常是ImageView）
        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        findAllNodes(rootNode, allNodes);

        for (AccessibilityNodeInfo node : allNodes) {
            if (!node.isVisibleToUser()) continue;

            String className = node.getClassName() != null ? node.getClassName().toString() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
            String text = node.getText() != null ? node.getText().toString() : "";

            // 查找可能是搜索按钮的元素
            boolean isSearchButton = (className.contains("ImageView") || className.contains("ImageButton")) &&
                    (desc.toLowerCase().contains("搜索") || desc.toLowerCase().contains("search") ||
                     desc.toLowerCase().contains("查找") || desc.toLowerCase().contains("放大镜") ||
                     text.toLowerCase().contains("搜索"));

            if (isSearchButton && node.isClickable()) {
                Log.d(TAG, "找到搜索按钮: " + className + ", 描述: " + desc);
                boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "点击搜索按钮结果: " + clicked);

                if (clicked) {
                    node.recycle();
                    return;
                }
            }
        }

        // 方法3：通过资源ID查找微信特有的搜索按钮
        List<AccessibilityNodeInfo> searchBtnById = rootNode.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/search_btn");
        if (searchBtnById.isEmpty()) {
            searchBtnById = rootNode.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/gbk"); // 微信常见的搜索按钮ID
        }

        if (!searchBtnById.isEmpty()) {
            for (AccessibilityNodeInfo btn : searchBtnById) {
                if (btn.isClickable() && btn.isVisibleToUser()) {
                    Log.d(TAG, "通过资源ID找到搜索按钮");
                    boolean clicked = btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "点击搜索按钮结果: " + clicked);
                    btn.recycle();
                    return;
                }
                btn.recycle();
            }
        }

        Log.d(TAG, "未找到搜索按钮，尝试通过坐标点击");
        clickSearchByCoordinates();
    }

    /**
     * 通过坐标点击搜索按钮（通常在右上角）
     */
    private void clickSearchByCoordinates() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        // 搜索按钮通常在右上角
        int searchX = (int) (screenWidth * 0.9);  // 距离右侧10%
        int searchY = (int) (screenHeight * 0.08); // 距离顶部8%

        Log.d(TAG, "通过坐标点击搜索按钮: (" + searchX + ", " + searchY + ")");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(searchX, searchY);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 100);
            builder.addStroke(stroke);

            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d(TAG, "坐标点击搜索按钮完成");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d(TAG, "坐标点击搜索按钮被取消");
                }
            }, null);
        }
    }

    /**
     * 在搜索界面输入联系人名称
     */
    private void inputContactName(AccessibilityNodeInfo rootNode, String contactName) {
        Log.d(TAG, "准备在搜索界面输入联系人: " + contactName);

        // 查找搜索输入框
        AccessibilityNodeInfo searchInput = findSearchInputField(rootNode);

        if (searchInput != null) {
            Log.d(TAG, "找到搜索输入框，开始输入联系人名称");

            // 先点击输入框获取焦点
            if (searchInput.isFocusable()) {
                searchInput.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            } else if (searchInput.isClickable()) {
                searchInput.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

            // 输入联系人名称
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, contactName);
            boolean success = searchInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            Log.d(TAG, "设置搜索文本结果: " + success);

            if (success) {
                // 输入完成后，等待搜索结果
                mainHandler.postDelayed(() -> {
                    Log.d(TAG, "输入完成，等待搜索结果加载...");
                    // 状态将在onAccessibilityEvent中根据界面变化自动更新
                }, 2000);
            } else {
                Log.d(TAG, "输入失败，尝试其他方法");
                // 如果直接输入失败，尝试使用全局动作输入
                inputUsingGlobalAction(contactName);
            }

            searchInput.recycle();
        } else {
            Log.d(TAG, "未找到搜索输入框，尝试其他方式");
            // 如果找不到特定的输入框，尝试使用全局动作
            inputUsingGlobalAction(contactName);
        }
    }

    /**
     * 使用全局动作输入文本（备用方法）
     */
    private void inputUsingGlobalAction(String text) {
        Log.d(TAG, "使用全局动作输入文本: " + text);
        // 这里可以实现备用的输入方法
    }

    /**
     * 查找搜索输入框
     */
    private AccessibilityNodeInfo findSearchInputField(AccessibilityNodeInfo rootNode) {
        Log.d(TAG, "开始查找搜索输入框");

        // 方法1：通过resource-id查找（微信常见的搜索框id）
        String[] searchBoxIds = {
            "com.tencent.mm:id/gbh", // 常见的微信搜索框ID
            "com.tencent.mm:id/ht",
            "com.tencent.mm:id/search_input",
            "com.tencent.mm:id/search_src_text"
        };

        for (String id : searchBoxIds) {
            List<AccessibilityNodeInfo> nodesById = rootNode.findAccessibilityNodeInfosByViewId(id);
            if (!nodesById.isEmpty()) {
                Log.d(TAG, "通过resource-id找到搜索框: " + id);
                AccessibilityNodeInfo node = nodesById.get(0);
                if (node.isEditable() || node.isFocusable()) {
                    return AccessibilityNodeInfo.obtain(node);
                }
            }
        }

        // 方法2：通过类名和特征查找
        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        findAllNodes(rootNode, allNodes);

        for (AccessibilityNodeInfo node : allNodes) {
            if (!node.isVisibleToUser()) continue;

            String className = node.getClassName() != null ? node.getClassName().toString() : "";
            String text = node.getText() != null ? node.getText().toString() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";

            // 搜索输入框的特征
            boolean isSearchInput = (className.contains("EditText") || className.contains("SearchView")) &&
                    (desc.contains("搜索") || desc.contains("Search") ||
                     desc.contains("查找") || text.contains("搜索") || text.contains("Search") ||
                     node.isEditable());

            if (isSearchInput) {
                Log.d(TAG, "找到搜索输入框: " + className + ", 描述: " + desc + ", 文本: " + text);
                return AccessibilityNodeInfo.obtain(node);
            }
        }

        Log.d(TAG, "未找到搜索输入框");
        return null;
    }

    /**
     * 从搜索结果中选择联系人
     */
    private void selectContactFromResults(AccessibilityNodeInfo rootNode, String contactName) {
        Log.d(TAG, "从搜索结果中选择联系人: " + contactName);

        // 输出当前界面的所有文本节点，用于调试
        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        findAllNodes(rootNode, allNodes);
        Log.d(TAG, "当前界面共有 " + allNodes.size() + " 个节点");

        // 收集所有可见的文本节点用于调试
        List<String> visibleTexts = new ArrayList<>();
        for (AccessibilityNodeInfo node : allNodes) {
            if (node.isVisibleToUser() && node.getText() != null) {
                String text = node.getText().toString();
                if (!text.isEmpty()) {
                    visibleTexts.add(text);
                }
            }
        }
        Log.d(TAG, "当前界面可见文本: " + visibleTexts);

        // 优先使用直接点击文本的方式
        Log.d(TAG, "尝试直接点击文本: " + contactName);
        boolean clicked = clickTextDirectly(contactName);

        if (clicked) {
            Log.d(TAG, "通过直接点击文本成功点击联系人");
            // 成功点击后，更新状态以防止重复点击
            if (currentState == OperationState.SELECTING_CONTACT) {
                Log.d(TAG, "成功点击联系人，等待界面跳转...");
                // 设置标志，表示已尝试点击，防止重复点击
                currentState = OperationState.WAITING_FOR_CHAT_INTERFACE; // 新增状态
            }
            return;
        } else {
            Log.d(TAG, "直接点击文本失败，尝试其他方法");
        }

        // 如果直接点击文本失败，再尝试原来的节点点击方法
        // 方法1：精确匹配联系人名称
        List<AccessibilityNodeInfo> contactNodes = rootNode.findAccessibilityNodeInfosByText(contactName);
        Log.d(TAG, "通过精确匹配找到 " + contactNodes.size() + " 个联系人节点");

        for (AccessibilityNodeInfo node : contactNodes) {
            String text = node.getText() != null ? node.getText().toString() : "";
            String className = node.getClassName() != null ? node.getClassName().toString() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";

            Log.d(TAG, "找到联系人节点: " + text + ", 类名: " + className + ", 描述: " + desc);

            boolean nodeClicked = false;

            if (node.isClickable() && node.isVisibleToUser()) {
                // 节点本身可点击，直接点击
                Log.d(TAG, "节点本身可点击，直接点击");

                // 获取节点边界信息用于调试
                android.graphics.Rect bounds = new android.graphics.Rect();
                node.getBoundsInScreen(bounds);
                Log.d(TAG, "节点位置: [" + bounds.left + "," + bounds.top + "][" + bounds.right + "," + bounds.bottom + "]");

                // 优先点击ListItem类型的节点，这通常是真正的联系人条目
                if (className.toLowerCase().contains("listitem") || className.toLowerCase().contains("linearlayout")) {
                    Log.d(TAG, "点击ListItem类型的联系人节点");
                }

                nodeClicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            } else {
                // 节点不可点击，尝试查找可点击的父节点
                Log.d(TAG, "节点本身不可点击，尝试查找可点击的父节点");
                AccessibilityNodeInfo clickableParent = findClickableParent(node);
                if (clickableParent != null) {
                    nodeClicked = clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "点击父节点结果: " + nodeClicked);
                    clickableParent.recycle();
                }
            }

            Log.d(TAG, "点击联系人结果: " + nodeClicked);

            if (nodeClicked) {
                node.recycle();
                // 成功点击后，更新状态以防止重复点击
                if (currentState == OperationState.SELECTING_CONTACT) {
                    Log.d(TAG, "成功点击联系人，等待界面跳转...");
                    // 设置标志，表示已尝试点击，防止重复点击
                    currentState = OperationState.WAITING_FOR_CHAT_INTERFACE; // 新增状态
                }
                return;
            } else {
                Log.d(TAG, "点击失败，尝试其他方法");
            }
            node.recycle();
        }

        // 方法2：模糊匹配（更宽松的条件）
        for (AccessibilityNodeInfo node : allNodes) {
            String text = node.getText() != null ? node.getText().toString() : "";

            if (text.toLowerCase().contains(contactName.toLowerCase()) &&
                !text.toLowerCase().contains("搜索") &&
                !text.toLowerCase().contains("search") &&
                !text.toLowerCase().contains("公众号") &&
                !text.toLowerCase().contains("群聊")) {

                Log.d(TAG, "模糊匹配到联系人: " + text);

                boolean nodeClicked = false;

                if (node.isClickable() && node.isVisibleToUser()) {
                    // 节点本身可点击，直接点击
                    Log.d(TAG, "节点本身可点击，直接点击");
                    nodeClicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                } else {
                    // 节点不可点击，尝试查找可点击的父节点
                    Log.d(TAG, "节点本身不可点击，尝试查找可点击的父节点");
                    AccessibilityNodeInfo clickableParent = findClickableParent(node);
                    if (clickableParent != null) {
                        nodeClicked = clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d(TAG, "点击父节点结果: " + nodeClicked);
                        clickableParent.recycle();
                    }
                }

                Log.d(TAG, "点击模糊匹配联系人结果: " + nodeClicked);

                if (nodeClicked) {
                    node.recycle();
                    // 成功点击后，更新状态以防止重复点击
                    if (currentState == OperationState.SELECTING_CONTACT) {
                        Log.d(TAG, "成功点击联系人，等待界面跳转...");
                        currentState = OperationState.WAITING_FOR_CHAT_INTERFACE; // 新增状态
                    }
                    return;
                }
            }
        }

        // 方法3：查找可能的联系人列表项（基于类名和布局）
        for (AccessibilityNodeInfo node : allNodes) {
            String text = node.getText() != null ? node.getText().toString() : "";
            String className = node.getClassName() != null ? node.getClassName().toString() : "";

            // 查找可能是联系人列表项的元素
            boolean isPotentialContact =
                (className.contains("ListItem") ||
                 className.contains("LinearLayout") ||
                 className.contains("RelativeLayout") ||
                 className.contains("FrameLayout")) &&
                !text.toLowerCase().contains("搜索") &&
                !text.toLowerCase().contains("search") &&
                !text.toLowerCase().contains("添加") &&
                !text.toLowerCase().contains("公众号") &&
                !text.toLowerCase().contains("群聊") &&
                !text.toLowerCase().contains("标签") &&
                !text.toLowerCase().contains("更多") &&
                !text.isEmpty(); // 确保有文本内容

            if (isPotentialContact) {
                Log.d(TAG, "找到潜在联系人项: " + text + ", 类名: " + className);

                boolean nodeClicked = false;

                if (node.isClickable() && node.isVisibleToUser()) {
                    // 节点本身可点击，直接点击
                    Log.d(TAG, "节点本身可点击，直接点击");
                    nodeClicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                } else {
                    // 节点不可点击，尝试查找可点击的父节点
                    Log.d(TAG, "节点本身不可点击，尝试查找可点击的父节点");
                    AccessibilityNodeInfo clickableParent = findClickableParent(node);
                    if (clickableParent != null) {
                        nodeClicked = clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d(TAG, "点击父节点结果: " + nodeClicked);
                        clickableParent.recycle();
                    }
                }

                Log.d(TAG, "点击潜在联系人项结果: " + nodeClicked);

                if (nodeClicked) {
                    node.recycle();
                    // 成功点击后，更新状态以防止重复点击
                    if (currentState == OperationState.SELECTING_CONTACT) {
                        Log.d(TAG, "成功点击联系人，等待界面跳转...");
                        currentState = OperationState.WAITING_FOR_CHAT_INTERFACE; // 新增状态
                    }
                    return;
                }
            }
        }

        // 方法4：如果以上都失败，尝试点击第一个看起来像联系人的项目
        Log.d(TAG, "尝试点击第一个看起来像联系人的项目");
        for (AccessibilityNodeInfo node : allNodes) {
            String text = node.getText() != null ? node.getText().toString() : "";
            String className = node.getClassName() != null ? node.getClassName().toString() : "";

            // 检查是否是列表中的项目，且不是搜索相关
            if (!text.isEmpty() &&
                (className.contains("ListItem") || className.contains("View")) &&
                !text.toLowerCase().contains("搜索") &&
                !text.toLowerCase().contains("添加") &&
                !text.toLowerCase().contains("公众号")) {

                Log.d(TAG, "找到看起来像联系人的项目: " + text);

                boolean nodeClicked = false;

                if (node.isClickable() && node.isVisibleToUser()) {
                    // 节点本身可点击，直接点击
                    Log.d(TAG, "节点本身可点击，直接点击");
                    nodeClicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                } else {
                    // 节点不可点击，尝试查找可点击的父节点
                    Log.d(TAG, "节点本身不可点击，尝试查找可点击的父节点");
                    AccessibilityNodeInfo clickableParent = findClickableParent(node);
                    if (clickableParent != null) {
                        nodeClicked = clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d(TAG, "点击父节点结果: " + nodeClicked);
                        clickableParent.recycle();
                    }
                }

                Log.d(TAG, "点击结果: " + nodeClicked);

                if (nodeClicked) {
                    node.recycle();
                    // 成功点击后，更新状态以防止重复点击
                    if (currentState == OperationState.SELECTING_CONTACT) {
                        Log.d(TAG, "成功点击联系人，等待界面跳转...");
                        currentState = OperationState.WAITING_FOR_CHAT_INTERFACE; // 新增状态
                    }
                    return;
                }
            }
        }

        Log.d(TAG, "未找到可点击的联系人，尝试滚动查看更多结果");
        // 尝试滚动列表查看是否有更多结果
        if (rootNode.isScrollable()) {
            Log.d(TAG, "发现可滚动的父容器，尝试滚动");
            rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        }
    }


    /**
     * 在聊天界面发送消息
     */
    private void sendMessageInChat(AccessibilityNodeInfo rootNode, String message) {
        Log.d(TAG, "在聊天界面发送消息: " + message);

        // 查找消息输入框
        AccessibilityNodeInfo inputField = findMessageInputField(rootNode);

        if (inputField != null) {
            Log.d(TAG, "找到消息输入框");

            // 输入消息
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message);
            boolean textSet = inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            Log.d(TAG, "设置消息文本结果: " + textSet);

            if (textSet) {
                // 查找发送按钮
                AccessibilityNodeInfo sendButton = findSendButton(rootNode);
                if (sendButton != null && sendButton.isClickable()) {
                    Log.d(TAG, "找到发送按钮");
                    boolean clicked = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "点击发送按钮结果: " + clicked);

                    if (clicked) {
                        Log.d(TAG, "✅ 消息发送成功！");
                        // 添加短暂延迟确保发送操作完成
                        mainHandler.postDelayed(() -> {
                            Log.d(TAG, "消息发送操作已完成");
                        }, 1000);
                    }
                    sendButton.recycle();
                } else {
                    Log.d(TAG, "未找到发送按钮，尝试其他发送方式");

                    // 尝试按下回车键发送（在输入框上执行ACTION_FOCUS并发送回车）
                    // 或者尝试通过键盘发送
                    Log.d(TAG, "尝试通过回车键发送消息");
                    inputField.performAction(AccessibilityNodeInfo.ACTION_FOCUS);

                    // 尝试使用全局动作发送
                    mainHandler.postDelayed(() -> {
                        // 模拟按下回车键发送（通过执行IME_ACTION_DONE等动作）
                        inputField.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);

                        // 最后尝试按下发送按钮（如果存在）
                        AccessibilityNodeInfo retrySendButton = findSendButton(rootNode);
                        if (retrySendButton != null) {
                            boolean retryClicked = retrySendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.d(TAG, "重试发送按钮点击结果: " + retryClicked);
                            retrySendButton.recycle();
                        }
                    }, 500);
                }
            } else {
                Log.d(TAG, "输入消息失败");
            }

            inputField.recycle();
        } else {
            Log.d(TAG, "未找到消息输入框");
        }
    }

    /**
     * 查找消息输入框
     */
    private AccessibilityNodeInfo findMessageInputField(AccessibilityNodeInfo rootNode) {
        Log.d(TAG, "开始查找消息输入框");

        // 方法1：通过resource-id查找微信常见的输入框ID
        String[] inputFieldIds = {
            "com.tencent.mm:id/bhn", // 常见的微信输入框ID
            "com.tencent.mm:id/input",
            "com.tencent.mm:id/content_editable",
            "com.tencent.mm:id/chatting_content_et"
        };

        for (String id : inputFieldIds) {
            List<AccessibilityNodeInfo> nodesById = rootNode.findAccessibilityNodeInfosByViewId(id);
            if (!nodesById.isEmpty()) {
                Log.d(TAG, "通过resource-id找到输入框: " + id);
                AccessibilityNodeInfo node = nodesById.get(0);
                if (node.isEditable()) {
                    return AccessibilityNodeInfo.obtain(node);
                }
            }
        }

        // 方法2：查找所有EditText且可编辑的元素
        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        findAllNodes(rootNode, allNodes);

        for (AccessibilityNodeInfo node : allNodes) {
            if (!node.isVisibleToUser()) continue;

            String className = node.getClassName() != null ? node.getClassName().toString() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
            String text = node.getText() != null ? node.getText().toString() : "";

            // 消息输入框的特征
            boolean isInputField = className.contains("EditText") &&
                                 node.isEditable() &&
                                 node.isFocusable() &&
                                 (desc.contains("输入") || desc.contains("消息") ||
                                  text.contains("输入") || text.contains("消息") ||
                                  !text.contains("发送")); // 排除发送按钮

            if (isInputField) {
                Log.d(TAG, "找到消息输入框: " + className + ", 描述: " + desc);
                return AccessibilityNodeInfo.obtain(node);
            }
        }

        Log.d(TAG, "未找到消息输入框");
        return null;
    }

    /**
     * 查找发送按钮
     */
    private AccessibilityNodeInfo findSendButton(AccessibilityNodeInfo rootNode) {
        Log.d(TAG, "开始查找发送按钮");

        // 方法1：通过文本查找（包括变体）
        String[] sendTexts = {"发送", "Send", "SEND", "发送消息", "发", "→", "➤", ">"};
        for (String sendText : sendTexts) {
            List<AccessibilityNodeInfo> sendButtons = rootNode.findAccessibilityNodeInfosByText(sendText);
            if (!sendButtons.isEmpty()) {
                Log.d(TAG, "通过文本 '" + sendText + "' 找到发送按钮");
                for (AccessibilityNodeInfo button : sendButtons) {
                    if (button.isClickable() && button.isVisibleToUser()) {
                        Log.d(TAG, "找到可点击的发送按钮: " + sendText);
                        return AccessibilityNodeInfo.obtain(button);
                    }
                    button.recycle();
                }
            }
        }

        // 方法2：通过resource-id查找
        String[] buttonIds = {
            "com.tencent.mm:id/bhm", // 常见的微信发送按钮ID
            "com.tencent.mm:id/send_btn",
            "com.tencent.mm:id/send",
            "com.tencent.mm:id/aob", // 微信新版常用的发送按钮ID
            "com.tencent.mm:id/bak", // 另一种可能的发送按钮ID
            "com.tencent.mm:id/bai"  // 另一种可能的发送按钮ID
        };

        for (String id : buttonIds) {
            List<AccessibilityNodeInfo> buttonsById = rootNode.findAccessibilityNodeInfosByViewId(id);
            if (!buttonsById.isEmpty()) {
                Log.d(TAG, "通过resource-id找到发送按钮: " + id);
                for (AccessibilityNodeInfo button : buttonsById) {
                    if (button.isClickable() && button.isVisibleToUser()) {
                        Log.d(TAG, "通过ID找到可点击的发送按钮: " + id);
                        return AccessibilityNodeInfo.obtain(button);
                    }
                    button.recycle();
                }
            }
        }

        // 方法3：查找可能的发送按钮（基于类名和内容描述）
        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        findAllNodes(rootNode, allNodes);

        for (AccessibilityNodeInfo node : allNodes) {
            if (node.isClickable() && node.isVisibleToUser()) {
                String text = node.getText() != null ? node.getText().toString() : "";
                String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
                String className = node.getClassName() != null ? node.getClassName().toString() : "";

                // 检查多种可能的发送按钮特征
                boolean isSendButton =
                    (text.contains("发送") || text.contains("Send") || text.contains("→") ||
                     text.contains("➤") || text.contains(">") || desc.contains("发送") ||
                     desc.contains("Send") || text.equals("发")) &&
                    (className.contains("Button") || className.contains("ImageButton") ||
                     className.contains("ImageView"));

                if (isSendButton) {
                    Log.d(TAG, "找到可能的发送按钮: text='" + text + "', desc='" + desc + "', class='" + className + "'");
                    return AccessibilityNodeInfo.obtain(node);
                }
            }
        }

        // 方法4：如果以上都没找到，尝试查找输入框旁边或下方的按钮
        AccessibilityNodeInfo inputField = findMessageInputField(rootNode);
        if (inputField != null) {
            Log.d(TAG, "在消息输入框附近查找发送按钮");

            // 获取输入框的位置信息
            android.graphics.Rect inputBounds = new android.graphics.Rect();
            inputField.getBoundsInScreen(inputBounds);

            // 寻找位置在输入框右侧或附近的按钮
            for (AccessibilityNodeInfo node : allNodes) {
                if (node.isClickable() && node.isVisibleToUser()) {
                    String text = node.getText() != null ? node.getText().toString() : "";
                    String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
                    String className = node.getClassName() != null ? node.getClassName().toString() : "";

                    if (className.contains("Button") || className.contains("ImageButton") ||
                        className.contains("ImageView")) {

                        android.graphics.Rect buttonBounds = new android.graphics.Rect();
                        node.getBoundsInScreen(buttonBounds);

                        // 检查按钮是否在输入框附近（右侧或下方）
                        if (buttonBounds.left >= inputBounds.right - 50 &&
                            Math.abs(buttonBounds.top - inputBounds.top) < 100) {

                            // 额外验证这是否真的是发送按钮
                            boolean isSendButton = text.contains("发送") || text.contains("Send") ||
                                                 desc.contains("发送") || desc.contains("Send") ||
                                                 text.equals("发") || text.contains("➤") || text.contains("→");

                            if (isSendButton) {
                                Log.d(TAG, "找到确认的发送按钮: text='" + text + "', desc='" + desc + "'");
                                return AccessibilityNodeInfo.obtain(node);
                            } else {
                                Log.d(TAG, "找到附近按钮但不确定是否为发送按钮: text='" + text + "'");
                            }
                        }
                    }
                }
            }

            inputField.recycle();
        }

        Log.d(TAG, "未找到发送按钮");
        return null;
    }

    /**
     * 判断是否为搜索界面
     */
    private boolean isSearchScreen(String className) {
        if (className == null) {
            return false;
        }
        return className.contains("Search") ||
                className.contains("search") ||
                className.contains("FTSMainUI") || // 微信搜索界面的常见类名
                className.contains("fts") ||
                className.contains("SearchBar") ||
                className.toLowerCase().contains("search");
    }

    /**
     * 判断是否为搜索结果界面
     */
    private boolean isSearchResultScreen(String className) {
        if (className == null) {
            return false;
        }
        return className.contains("FTS") || // 微信搜索相关类名
                className.contains("SearchResult") ||
                className.contains("ContactSearch") ||
                className.contains("CommonContact") ||
                className.contains("SelectContact");
    }

    /**
     * 判断是否为聊天界面
     */
    private boolean isChatScreen(String className) {
        if (className == null) {
            return false;
        }
        return className.contains("ChattingUI") ||
                className.contains("chatting") ||
                className.contains("Chatting") ||
                className.contains("ChatUI") ||
                className.contains("chat") ||
                className.toLowerCase().contains("chat") ||
                className.contains("Conversation") ||
                className.contains("com.tencent.mm.ui.chatting");
    }

    /**
     * 判断是否为微信主界面
     */
    private boolean isWeChatMainScreen(String className) {
        if (className == null) {
            return false;
        }
        return className.contains("LauncherUI") ||
                className.contains("MainUI") ||
                className.contains(".ui.LauncherUI") ||
                className.contains("Home") ||
                className.contains("SNS") ||  // 发现页面
                className.contains("Me");     // 我的页面
    }

    /**
     * 检查当前界面是否可能是聊天界面
     */
    private boolean isLikelyChatInterface(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) {
            return false;
        }

        // 查找可能的聊天界面元素
        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        findAllNodes(rootNode, allNodes);

        int chatElementsCount = 0;

        for (AccessibilityNodeInfo node : allNodes) {
            String text = node.getText() != null ? node.getText().toString() : "";
            String className = node.getClassName() != null ? node.getClassName().toString() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";

            // 检查是否有聊天界面的典型元素
            if (text.contains("发送") || text.contains("Send") || text.equalsIgnoreCase("发") ||
                className.toLowerCase().contains("edittext") ||
                text.contains("语音") || text.contains("表情") || text.contains("更多") ||
                desc.contains("发送") || desc.contains("Send")) {
                chatElementsCount++;
            }

            // 检查是否有搜索相关的元素（如果是，则不是聊天界面）
            if (text.contains("搜索") || text.contains("Search") || text.contains("查找") ||
                className.toLowerCase().contains("search")) {
                // 如果同时有搜索元素和聊天元素，需要进一步判断
                return chatElementsCount > 1; // 聊天元素比搜索元素多才认为是聊天界面
            }
        }

        return chatElementsCount >= 1; // 至少有一个聊天界面元素
    }

    /**
     * 直接点击文本内容
     */
    private boolean clickTextDirectly(String textToClick) {
        Log.d(TAG, "尝试直接点击文本: " + textToClick);

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.d(TAG, "getRootInActiveWindow返回null，无法点击文本");
            return false;
        }

        try {
            // 查找包含指定文本的所有节点
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(textToClick);
            Log.d(TAG, "找到 " + nodes.size() + " 个包含文本 '" + textToClick + "' 的节点");

            for (AccessibilityNodeInfo node : nodes) {
                if (node != null) {
                    String nodeText = node.getText() != null ? node.getText().toString() : "";
                    String className = node.getClassName() != null ? node.getClassName().toString() : "";
                    boolean isClickable = node.isClickable();
                    boolean isVisible = node.isVisibleToUser();

                    Log.d(TAG, "节点详情: 文本='" + nodeText + "', 类名='" + className +
                          "', 可点击=" + isClickable + ", 可见=" + isVisible);

                    if (isClickable && isVisible) {
                        // 如果节点本身可点击，直接点击
                        boolean result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d(TAG, "直接点击节点结果: " + result);

                        node.recycle();
                        return result;
                    } else {
                        // 如果节点不可点击，尝试查找其可点击的父节点
                        Log.d(TAG, "当前节点不可点击，尝试查找可点击的父节点");
                        AccessibilityNodeInfo clickableParent = findClickableParent(node);
                        if (clickableParent != null) {
                            boolean result = clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.d(TAG, "点击父节点结果: " + result);

                            clickableParent.recycle();
                            node.recycle();
                            return result;
                        }
                    }

                    node.recycle();
                }
            }
        } finally {
            if (rootNode != null) {
                rootNode.recycle();
            }
        }

        Log.d(TAG, "未找到可点击的文本节点或其父节点");
        return false;
    }

    /**
     * 查找可点击的父节点
     */
    private AccessibilityNodeInfo findClickableParent(AccessibilityNodeInfo childNode) {
        if (childNode == null) {
            return null;
        }

        // 从当前节点开始向上遍历父节点
        AccessibilityNodeInfo parent = childNode.getParent();
        int level = 0;
        final int MAX_LEVELS = 10; // 防止无限循环

        while (parent != null && level < MAX_LEVELS) {
            String parentClassName = parent.getClassName() != null ? parent.getClassName().toString() : "";
            boolean isParentClickable = parent.isClickable();
            boolean isParentVisible = parent.isVisibleToUser();

            Log.d(TAG, "检查父节点: 级别=" + level + ", 类名='" + parentClassName +
                  "', 可点击=" + isParentClickable + ", 可见=" + isParentVisible);

            if (isParentClickable && isParentVisible) {
                // 检查父节点是否包含我们想要的文本
                String parentText = parent.getText() != null ? parent.getText().toString() : "";
                Log.d(TAG, "找到可点击父节点，文本: " + parentText);

                return AccessibilityNodeInfo.obtain(parent); // 返回副本
            }

            // 移动到更高一级的父节点
            AccessibilityNodeInfo oldParent = parent;
            parent = parent.getParent();
            oldParent.recycle(); // 回收临时引用
            level++;
        }

        Log.d(TAG, "未找到可点击的父节点");
        return null;
    }



    /**
     * 辅助方法：查找所有节点
     */
    private void findAllNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> nodeList) {
        if (node == null) {
            return;
        }

        nodeList.add(AccessibilityNodeInfo.obtain(node));

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findAllNodes(child, nodeList);
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "无障碍服务被中断");
    }
}
