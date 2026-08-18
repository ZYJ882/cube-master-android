package com.manus.cubemaster;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

import com.manus.cubemaster.solver.CfopSolver;
import com.manus.cubemaster.solver.LayerByLayerSolver;
import com.manus.cubemaster.solver.RouxSolver;
import com.manus.cubemaster.solver.SolverFacade;
import com.manus.cubemaster.solver.ZzSolver;

import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** 液态玻璃版主界面：三维浏览、相机辅助录入、离线求解与动态还原。 */
public final class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION = 2001;
    private static final long SOLVER_RESOURCE_LOAD_TIMEOUT_MS = 8_000L;
    private static final long SOLVE_REQUEST_TIMEOUT_MS = 12_000L;
    private static final String KOCIEMBA_TABLE_ASSET = "kociemba_tables_v1.bin";
    private static final String ROUX_TABLE_ASSET = "roux_block_tables_v1.bin";
    private final CubeState cube = new CubeState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService solveExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService warmUpExecutor = Executors.newSingleThreadExecutor();
    /** Roux 与 ZZ 表彼此独立；双工作线程避免用户切换方法时被另一条路线的预热排队阻塞。 */
    private final ExecutorService stageWarmUpExecutor = Executors.newFixedThreadPool(2);
    private final List<String> solutionMoves = new ArrayList<>();
    private final List<LayerByLayerSolver.Stage> layerStages = new ArrayList<>();
    private final List<String> lastScrambleMoves = new ArrayList<>();
    private final List<AppCompatButton> solveMethodButtons = new ArrayList<>();
    private SolveMethod selectedSolveMethod = SolveMethod.KOCIEMBA;
    /** 打乱采用系统熵支持的安全随机源；仅生成合法外层转动。 */
    private final SecureRandom random = new SecureRandom();
    private Future<?> warmUpFuture;
    private Future<?> rouxWarmUpFuture;
    private Future<?> zzWarmUpFuture;
    private Future<?> activeSolveFuture;
    private boolean solveInProgress = false;
    private boolean solverTablesReady = false;
    private boolean solverInitializationFailed = false;
    private boolean rouxStageReady = false;
    private boolean zzStageReady = false;
    private SolveMethod pendingStageCalculateMethod;
    private boolean pendingCalculateAfterLoad = false;
    private long solverInitializationAttempt = 0L;
    private long solveRequestId = 0L;
    private Runnable solveTimeoutTask;
    /** 仅在用户按下“刷新上色”后启用：未确认的面片在 3D 模型中显示为灰色。 */
    private boolean modelPreviewMode = false;

    private Cube3DView cubeView;
    private FaceEditorView editor;
    private TextView stateText;
    private TextView solutionText;
    private TextView methodText;
    private TextView playStatus;
    private TextView speedLabel;
    private TextView heroStatus;
    private View solutionPanel;
    private AppCompatButton solveButton;
    private AppCompatButton playButton;
    private SeekBar speedBar;
    private int playbackIndex = 0;
    private boolean playing = false;
    private String beforePlayback;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(8, 15, 34));
        setContentView(buildContent());
        refreshAll();
        startSolverWarmUp();
    }

    private View buildContent() {
        FrameLayout root = new FrameLayout(this);
        root.addView(new LiquidBackdropView(this), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(38));
        scroll.addView(content, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        content.addView(buildHeader(), marginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 14));
        content.addView(buildHero(), marginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 12));
        content.addView(buildQuickActions(), marginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64), 0, 0, 0, 12));
        content.addView(buildTurnsPanel(), marginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 12));
        content.addView(buildEditorPanel(), marginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 12));
        content.addView(buildSolutionPanel());

        TextView attribution = text("离线两阶段求解 · 所有数据仅保留在设备本地", 11, Color.rgb(177, 201, 225));
        attribution.setGravity(Gravity.CENTER);
        attribution.setPadding(0, dp(13), 0, 0);
        content.addView(attribution, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), 0, dp(2), 0);

        TextView monogram = text("C", 20, Color.rgb(7, 25, 33));
        monogram.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        monogram.setGravity(Gravity.CENTER);
        monogram.setBackground(gradient(new int[]{Color.rgb(149, 243, 197), Color.rgb(111, 202, 255)}, 18, Color.TRANSPARENT, 0));
        header.addView(monogram, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(11), 0, 0, 0);
        TextView eyebrow = text("CUBE INTELLIGENCE", 10, Color.rgb(143, 224, 255));
        eyebrow.setLetterSpacing(.14f);
        titleBlock.addView(eyebrow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(17)));
        TextView title = text("魔方大师", 27, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleBlock.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)));
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, dp(50), 1f));

        heroStatus = text("READY", 10, Color.rgb(190, 255, 222));
        heroStatus.setLetterSpacing(.1f);
        heroStatus.setGravity(Gravity.CENTER);
        heroStatus.setPadding(dp(10), 0, dp(10), 0);
        heroStatus.setBackground(gradient(new int[]{Color.argb(95, 89, 207, 155), Color.argb(65, 70, 168, 156)}, 16, Color.argb(112, 195, 255, 229), dp(1)));
        header.addView(heroStatus, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)));
        return header;
    }

    private View buildHero() {
        GlassCardLayout hero = glassCard(Color.argb(44, 220, 242, 255));
        hero.setPadding(dp(15), dp(14), dp(15), dp(13));
        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("LIVE CUBE", 11, Color.rgb(180, 228, 255));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setLetterSpacing(.16f);
        meta.addView(label, new LinearLayout.LayoutParams(0, dp(23), 1f));
        TextView chip = text("3 × 3 × 3", 11, Color.rgb(231, 247, 255));
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(9), 0, dp(9), 0);
        chip.setBackground(gradient(new int[]{Color.argb(74, 107, 166, 255), Color.argb(42, 235, 255, 255)}, 14, Color.argb(72, 224, 247, 255), dp(1)));
        meta.addView(chip, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)));
        AppCompatButton refreshModel = glassAction("同步上色", Color.argb(78, 121, 216, 255), Color.rgb(235, 250, 255));
        refreshModel.setTextSize(10);
        refreshModel.setContentDescription("将下方上色同步到三维主魔方");
        refreshModel.setOnClickListener(v -> syncColorToLiveCube());
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(dp(82), dp(28));
        refreshParams.setMargins(dp(6), 0, 0, 0);
        meta.addView(refreshModel, refreshParams);
        hero.addView(meta, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));

        FrameLayout stage = new FrameLayout(this);
        stage.setBackground(gradient(new int[]{Color.argb(42, 8, 24, 58), Color.argb(18, 255, 255, 255)}, 24, Color.argb(64, 213, 242, 255), dp(1)));
        cubeView = new Cube3DView(this);
        cubeView.setContentDescription("拖动魔方周围空白区域旋转视角，拖动魔方层扭动该层");
        cubeView.setTapListener(() -> cubeView.resetCamera());
        cubeView.setLayerGestureListener(this::beginDirectLayerGesture);
        cubeView.setDirectMoveListener(this::handleDirectMove);
        stage.addView(cubeView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(312)));

        LinearLayout cameraControls = new LinearLayout(this);
        cameraControls.setGravity(Gravity.CENTER_VERTICAL);
        AppCompatButton orbitControl = glassAction("3D", Color.argb(112, 99, 205, 255), Color.WHITE);
        orbitControl.setTextSize(11);
        orbitControl.setContentDescription("按住拖动控制三维魔方视角");
        bindCameraDrag(orbitControl, false);
        cameraControls.addView(orbitControl, new LinearLayout.LayoutParams(dp(48), dp(34)));
        AppCompatButton peekControl = glassAction("◉", Color.argb(92, 104, 232, 205), Color.WHITE);
        peekControl.setTextSize(15);
        peekControl.setContentDescription("按住拖动临时预览视角，松手恢复原视角");
        bindCameraDrag(peekControl, true);
        LinearLayout.LayoutParams peekParams = new LinearLayout.LayoutParams(dp(42), dp(34));
        peekParams.setMargins(dp(5), 0, 0, 0);
        cameraControls.addView(peekControl, peekParams);
        FrameLayout.LayoutParams cameraParams = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34), Gravity.TOP | Gravity.END);
        cameraParams.setMargins(0, dp(10), dp(10), 0);
        stage.addView(cameraControls, cameraParams);

        TextView cue = text("拖魔方层：跟手预览，松手吸附 · 空白区或 3D 控制视角 · ◉ 松手复原", 10, Color.rgb(190, 215, 239));
        cue.setGravity(Gravity.CENTER);
        cue.setBackground(gradient(new int[]{Color.argb(66, 16, 32, 66), Color.argb(28, 255, 255, 255)}, 15, Color.argb(42, 219, 242, 255), dp(1)));
        FrameLayout.LayoutParams cueParams = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cueParams.bottomMargin = dp(11);
        stage.addView(cue, cueParams);
        hero.addView(stage, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(312)));

        TextView brief = text("面层跟手转动 · 视角临时预览 · 状态与真实面片同步", 12, Color.rgb(177, 207, 233));
        brief.setPadding(dp(3), dp(10), dp(3), 0);
        hero.addView(brief, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
        return hero;
    }

    /** 将小型视角按钮绑定为按住拖动手势；临时模式在松手时用短缓动回到原视角。 */
    private void bindCameraDrag(AppCompatButton control, boolean restoreOnRelease) {
        control.setOnTouchListener(new View.OnTouchListener() {
            private float lastX;
            private float lastY;
            private Cube3DView.CameraPose savedPose;
            private boolean moved;

            @Override public boolean onTouch(View view, MotionEvent event) {
                if (cubeView == null) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        cubeView.beginExternalCameraControl();
                        savedPose = restoreOnRelease ? cubeView.captureCameraPose() : null;
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        moved = false;
                        view.setPressed(true);
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - lastX;
                        float dy = event.getRawY() - lastY;
                        if (Math.abs(dx) + Math.abs(dy) > 0.5f) {
                            moved = true;
                            cubeView.dragExternalCameraBy(dx, dy);
                            lastX = event.getRawX();
                            lastY = event.getRawY();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        view.setPressed(false);
                        if (restoreOnRelease) {
                            cubeView.restoreCameraPose(savedPose);
                            playStatus.setText("临时视角预览结束，已恢复原视角。 ");
                        } else if (moved) {
                            playStatus.setText("3D 视角已调整，可继续拖动查看。 ");
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private View buildQuickActions() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        AppCompatButton scan = glassAction("⌁  识图", Color.rgb(117, 209, 255), Color.rgb(7, 25, 43));
        scan.setOnClickListener(v -> openScanner());
        row.addView(scan, weighted(0, dp(58), 1.12f, 0, 0, dp(6), 0));
        AppCompatButton scramble = glassAction("打乱", Color.argb(68, 255, 255, 255), Color.WHITE);
        scramble.setOnClickListener(v -> scramble());
        row.addView(scramble, weighted(0, dp(58), .82f, dp(3), 0, dp(3), 0));
        AppCompatButton reset = glassAction("复位", Color.argb(68, 255, 255, 255), Color.WHITE);
        reset.setOnClickListener(v -> resetCube());
        row.addView(reset, weighted(0, dp(58), .82f, dp(3), 0, 0, 0));
        return row;
    }

    private View buildTurnsPanel() {
        GlassCardLayout panel = glassCard(Color.argb(38, 185, 209, 255));
        panel.setPadding(dp(14), dp(13), dp(14), dp(12));
        panel.addView(sectionHead("CONTROL DECK", "手动转动"));
        HorizontalScrollView turnScroll = new HorizontalScrollView(this);
        turnScroll.setHorizontalScrollBarEnabled(false);
        turnScroll.setPadding(0, dp(8), 0, 0);
        LinearLayout turns = new LinearLayout(this);
        for (char face : CubeState.FACE_ORDER.toCharArray()) {
            String normal = String.valueOf(face);
            AppCompatButton clockwise = turnPill(normal);
            clockwise.setOnClickListener(v -> applyManualMove(normal));
            turns.addView(clockwise, new LinearLayout.LayoutParams(dp(44), dp(42)) {{ setMargins(0, 0, dp(5), 0); }});
            AppCompatButton counter = turnPill(face + "′");
            counter.setOnClickListener(v -> applyManualMove(face + "'"));
            turns.addView(counter, new LinearLayout.LayoutParams(dp(44), dp(42)) {{ setMargins(0, 0, dp(10), 0); }});
        }
        turnScroll.addView(turns);
        panel.addView(turnScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        return panel;
    }

    private View buildEditorPanel() {
        GlassCardLayout panel = glassCard(Color.argb(45, 161, 210, 255));
        panel.setPadding(dp(14), dp(13), dp(14), dp(12));
        panel.addView(sectionHead("逐面录入", "按真实颜色填魔方"));
        stateText = text("", 12, Color.rgb(195, 218, 239));
        stateText.setPadding(0, dp(5), 0, dp(4));
        panel.addView(stateText);
        editor = new FaceEditorView(this, cube);
        editor.setListener(this::onColorDraftEdited);
        panel.addView(editor, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return panel;
    }

    private View buildSolutionPanel() {
        GlassCardLayout panel = glassCard(Color.argb(53, 167, 235, 255));
        // 求解结果会动态扩展；禁用该卡片的软件图层，避免部分设备在重测量时丢失整张卡片。
        panel.setLayerType(View.LAYER_TYPE_NONE, null);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setPadding(dp(14), dp(13), dp(14), dp(13));
        solutionPanel = panel;
        panel.addView(sectionHead("SOLUTION STUDIO", "计算与还原"));
        TextView explain = text("离线搜索会校验颜色、朝向与奇偶性，再生成可播放的标准记号步骤。", 12, Color.rgb(195, 219, 241));
        explain.setPadding(0, dp(6), 0, dp(10));
        panel.addView(explain);

        TextView methodHead = text("选择还原策略", 13, Color.rgb(230, 248, 255));
        methodHead.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        methodHead.setPadding(dp(2), dp(2), dp(2), dp(4));
        panel.addView(methodHead);
        HorizontalScrollView methodScroll = new HorizontalScrollView(this);
        methodScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout methods = new LinearLayout(this);
        for (SolveMethod method : SolveMethod.values()) {
            AppCompatButton methodButton = glassAction(method.displayName(), Color.argb(65, 222, 246, 255), Color.WHITE);
            methodButton.setTextSize(11);
            methodButton.setOnClickListener(v -> selectSolveMethod(method));
            methodButton.setTag(method);
            solveMethodButtons.add(methodButton);
            methods.addView(methodButton, new LinearLayout.LayoutParams(dp(124), dp(42)) {{ setMargins(0, 0, dp(6), 0); }});
        }
        methodScroll.addView(methods);
        panel.addView(methodScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        methodText = text("", 11, Color.rgb(188, 219, 243));
        methodText.setLineSpacing(dp(2), 1f);
        methodText.setPadding(dp(3), dp(3), dp(3), dp(9));
        panel.addView(methodText);
        refreshMethodSelection();

        solveButton = glassAction(calculateButtonLabel(), Color.rgb(149, 243, 197), Color.rgb(7, 29, 31));
        solveButton.setTextSize(15);
        solveButton.setOnClickListener(v -> calculateSolution());
        panel.addView(solveButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        solutionText = text("尚未计算解法", 14, Color.WHITE);
        solutionText.setPadding(dp(4), dp(13), dp(4), dp(8));
        solutionText.setLineSpacing(dp(4), 1f);
        panel.addView(solutionText);

        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setGravity(Gravity.CENTER_VERTICAL);
        speedRow.setPadding(dp(3), 0, dp(3), 0);
        speedLabel = text("播放速度  ×4", 12, Color.rgb(204, 229, 249));
        speedRow.addView(speedLabel, new LinearLayout.LayoutParams(dp(112), dp(42)));
        speedBar = new SeekBar(this);
        speedBar.setMax(9);
        speedBar.setProgress(3);
        speedBar.setContentDescription("还原速度");
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { speedLabel.setText("播放速度  ×" + (progress + 1)); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        speedRow.addView(speedBar, new LinearLayout.LayoutParams(0, dp(42), 1f));
        panel.addView(speedRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout playback = new LinearLayout(this);
        playButton = glassAction("开始还原", Color.rgb(117, 209, 255), Color.rgb(7, 25, 43));
        playButton.setEnabled(false);
        playButton.setOnClickListener(v -> togglePlayback());
        playback.addView(playButton, weighted(0, dp(52), 1f, 0, 0, dp(5), 0));
        AppCompatButton step = glassAction("单步", Color.argb(75, 255, 255, 255), Color.WHITE);
        step.setOnClickListener(v -> stepPlayback());
        playback.addView(step, weighted(0, dp(52), .5f, dp(5), 0, 0, 0));
        panel.addView(playback, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        playStatus = text("准备就绪", 12, Color.rgb(185, 215, 239));
        playStatus.setGravity(Gravity.CENTER);
        playStatus.setPadding(0, dp(9), 0, 0);
        panel.addView(playStatus, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
        return panel;
    }

    private void selectSolveMethod(SolveMethod method) {
        if (method == selectedSolveMethod) return;
        cancelActiveSolve(false);
        stopPlayback();
        solutionMoves.clear();
        layerStages.clear();
        playbackIndex = 0;
        selectedSolveMethod = method;
        if (playButton != null) playButton.setEnabled(false);
        refreshMethodSelection();
        if (solutionText != null) {
            solutionText.setText("已切换至“" + method.displayName() + "”。\n"
                    + method.description() + "\n\n点击“" + calculateButtonLabel() + "”以针对当前状态生成可播放步骤。");
        }
        if (requiresStageWarmUp(method) && !isStageSolverReady(method)) {
            startStageSolverWarmUp(method);
            if (playStatus != null) playStatus.setText(method == SolveMethod.ROUX
                    ? "已选择“真实 Roux”；正在读取 APK 内置阶段表，完成后可立即计算。 "
                    : "已选择“" + method.displayName() + "”；正在后台准备阶段表，完成后可立即计算。 ");
        } else if (playStatus != null) {
            playStatus.setText("已选择独立“" + method.displayName() + "”求解器；旧解法已清除。 ");
        }
    }

    private void refreshMethodSelection() {
        if (methodText != null) {
            String source = "当前模式：独立求解器；将实际按以上阶段生成动作。";
            methodText.setText(selectedSolveMethod.signature() + " · " + selectedSolveMethod.audience()
                    + "\n" + selectedSolveMethod.description()
                    + "\n学习阶段：" + selectedSolveMethod.stageSummary()
                    + "\n" + source);
        }
        for (AppCompatButton button : solveMethodButtons) {
            Object tag = button.getTag();
            if (!(tag instanceof SolveMethod)) continue;
            SolveMethod method = (SolveMethod) tag;
            boolean chosen = method == selectedSolveMethod;
            button.setText((chosen ? "✓ " : "") + method.displayName());
            button.setAlpha(chosen ? 1f : .64f);
        }
        if (solveButton != null && !solveInProgress) solveButton.setText(calculateButtonLabel());
    }

    private String calculateButtonLabel() {
        if (selectedSolveMethod == SolveMethod.LAYER_BY_LAYER) return "计算真实层先法";
        if (selectedSolveMethod == SolveMethod.CFOP) return "计算真实 CFOP";
        if (selectedSolveMethod == SolveMethod.ROUX) return "计算真实 Roux";
        if (selectedSolveMethod == SolveMethod.ZZ) return "计算真实 ZZ";
        return "计算高效解法";
    }

    /** 求解内容更新前后强制保留并重测量卡片，防止动态结果导致某些设备丢弃下方视图。 */
    private void stabilizeSolutionPanel() {
        if (solutionPanel == null) return;
        solutionPanel.setVisibility(View.VISIBLE);
        solutionPanel.requestLayout();
        solutionPanel.invalidate();
        solutionPanel.post(() -> {
            if (solutionPanel != null) {
                solutionPanel.requestLayout();
                solutionPanel.invalidate();
            }
        });
    }

    private String formatSolvedResult(List<String> parsed) {
        if (selectedSolveMethod == SolveMethod.KOCIEMBA) {
            return "高效计算机解 · Kociemba 两阶段\n共 " + parsed.size() + " 步：\n" + joinMoves(parsed);
        }
        StringBuilder out = new StringBuilder(selectedSolveMethod.displayName() + " · 每阶段已验证\n");
        int total = 0;
        for (int i = 0; i < layerStages.size(); i++) {
            LayerByLayerSolver.Stage stage = layerStages.get(i);
            total += stage.moves().size();
            out.append("\n").append(i + 1).append(". ").append(stage.title())
                    .append("（").append(stage.moves().size()).append(" 步）\n")
                    .append(stage.detail()).append("\n")
                    .append(joinMoves(stage.moves())).append("\n");
        }
        out.append("\n共 ").append(total).append(" 步；播放将严格按以上阶段进行。");
        return out.toString();
    }

    private LinearLayout sectionHead(String eyebrowText, String titleText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text(eyebrowText, 10, Color.rgb(143, 224, 255));
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        eyebrow.setLetterSpacing(.14f);
        row.addView(eyebrow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(17)));
        TextView title = text(titleText, 18, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(25)));
        return row;
    }

    private GlassCardLayout glassCard(int tint) {
        GlassCardLayout card = new GlassCardLayout(this);
        card.setGlassTint(tint);
        return card;
    }

    private AppCompatButton glassAction(String label, int fill, int foreground) {
        AppCompatButton button = new AppCompatButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(foreground);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        int start = fill;
        int end = Color.argb(Math.max(30, Color.alpha(fill) - 24), Math.min(255, Color.red(fill) + 22), Math.min(255, Color.green(fill) + 22), Math.min(255, Color.blue(fill) + 22));
        button.setBackground(gradient(new int[]{start, end}, 18, Color.argb(86, 229, 247, 255), dp(1)));
        return button;
    }

    private AppCompatButton turnPill(String label) {
        AppCompatButton button = glassAction(label, Color.argb(64, 240, 249, 255), Color.rgb(242, 249, 255));
        button.setTextSize(14);
        return button;
    }

    private void openScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
            return;
        }
        new CameraScanDialog(this, this, 0, (face, values) -> {
            // 识图结果只写入下方草稿，真实魔方仍可继续打乱、扭层与还原。
            editor.setActiveFace(face);
            editor.importScannedFace(face, values);
            modelPreviewMode = false;
            onColorDraftEdited();
            String[] names = {"白色", "红色", "绿色", "黄色", "橙色", "蓝色"};
            toast("已写入" + names[face] + "上色草稿；点击“刷新上色”可预览。");
        }).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) openScanner();
            else toast("未获得相机权限，您仍可以使用手动上色功能。");
        }
    }

    /** 加载随 APK 发布的 Kociemba 查表资源；8 秒内未完成则明确失败，不会无限等待。 */
    private void startSolverWarmUp() {
        final long attempt = ++solverInitializationAttempt;
        solverTablesReady = false;
        solverInitializationFailed = false;
        if (solveButton != null && !solveInProgress) {
            solveButton.setEnabled(true);
            solveButton.setText(calculateButtonLabel());
        }
        if (!solveInProgress && playStatus != null) playStatus.setText("正在加载 Kociemba 查表资源；现在点击计算会自动继续。 ");
        warmUpFuture = warmUpExecutor.submit(() -> {
            try (java.io.InputStream tableResource = getAssets().open(KOCIEMBA_TABLE_ASSET, AssetManager.ACCESS_STREAMING)) {
                SolverFacade.warmUp(tableResource);
                runOnUiThread(() -> {
                    if (attempt != solverInitializationAttempt) return;
                    solverTablesReady = true;
                    if (solveButton != null && !solveInProgress) {
                        solveButton.setEnabled(true);
                        solveButton.setText(calculateButtonLabel());
                    }
                    if (pendingCalculateAfterLoad && !solveInProgress) {
                        pendingCalculateAfterLoad = false;
                        calculateSolution();
                    } else if (!solveInProgress && playStatus != null) {
                        playStatus.setText("Kociemba 查表资源已加载，可计算当前合法状态。 ");
                    }
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    if (attempt == solverInitializationAttempt) failSolverInitialization("Kociemba 查表资源加载失败：" + messageOf(error));
                });
            }
        });
        handler.postDelayed(() -> {
            if (attempt != solverInitializationAttempt || solverTablesReady || (warmUpFuture != null && warmUpFuture.isDone())) return;
            if (warmUpFuture != null) warmUpFuture.cancel(true);
            failSolverInitialization("Kociemba 查表资源加载超过 8 秒，已停止等待。请重新尝试；若仍失败请重新安装本版本。");
        }, SOLVER_RESOURCE_LOAD_TIMEOUT_MS);
    }

    private void failSolverInitialization(String message) {
        solverInitializationFailed = true;
        solverTablesReady = false;
        pendingCalculateAfterLoad = false;
        if (solveInProgress) {
            solveRequestId++;
            if (activeSolveFuture != null) activeSolveFuture.cancel(true);
            activeSolveFuture = null;
            solveInProgress = false;
        }
        if (solveButton != null) {
            solveButton.setEnabled(true);
            solveButton.setText("重新加载两阶段求解器");
        }
        if (solutionText != null) solutionText.setText(message);
        if (playStatus != null) playStatus.setText("未开始还原；请重新加载查表资源后再计算。 ");
    }

    /** 用户开始直接拖动模型层：旧解法立即失效，完成层转后必须针对新状态重新计算。 */
    private void beginDirectLayerGesture() {
        cancelActiveSolve(false);
        stopPlayback();
        solutionMoves.clear();
        layerStages.clear();
        if (playButton != null) playButton.setEnabled(false);
        if (solutionText != null) solutionText.setText("正在手动扭动魔方层；松手提交后请计算当前状态的标准解法。 ");
        if (playStatus != null) playStatus.setText("手动操作已取消旧解法；松手后可计算当前状态。 ");
    }

    /** 跟手预览已吸附到 90° 后由 Cube3DView 回调；此处只提交一次状态，绝不重复播放动画。 */
    private void handleDirectMove(String move) {
        if (cubeView == null) return;
        cancelActiveSolve(false);
        stopPlayback();
        modelPreviewMode = false;
        clearScrambleContext();
        solutionMoves.clear();
        layerStages.clear();
        playbackIndex = 0;
        cube.applyMove(move);
        if (editor != null) editor.applyLiveMove(move);
        playButton.setEnabled(false);
        solutionText.setText("已完成手势转动 " + move + "。请点击“" + calculateButtonLabel() + "”，生成当前选择策略的可播放步骤。 ");
        playStatus.setText("当前状态已更新，等待所选还原策略生成。 ");
        refreshAll();
    }

    private void applyManualMove(String move) {
        cancelActiveSolve(false);
        stopPlayback();
        modelPreviewMode = false;
        clearScrambleContext();
        solutionMoves.clear();
        layerStages.clear();
        playbackIndex = 0;
        cube.applyMove(move);
        if (editor != null) editor.applyLiveMove(move);
        playButton.setEnabled(false);
        solutionText.setText("已手动转动 " + move + "。请点击“" + calculateButtonLabel() + "”，生成当前选择策略的可播放步骤。 ");
        playStatus.setText("当前状态已更新，等待所选还原策略生成。 ");
        refreshAll();
    }

    private void scramble() {
        cancelActiveSolve(false);
        stopPlayback();
        modelPreviewMode = false;
        cube.reset();
        lastScrambleMoves.clear();
        char previousFace = 0;
        int previousAxis = -1;
        for (int i = 0; i < 22; i++) {
            char face;
            do {
                face = CubeState.FACE_ORDER.charAt(random.nextInt(6));
            } while (face == previousFace || axisOf(face) == previousAxis);
            previousFace = face;
            previousAxis = axisOf(face);
            int modifier = random.nextInt(3);
            String move = String.valueOf(face) + (modifier == 1 ? "2" : modifier == 2 ? "'" : "");
            lastScrambleMoves.add(move);
            cube.applyMove(move);
        }
        if (editor != null) editor.adoptLiveState(cube);
        solutionMoves.clear();
        layerStages.clear();
        playbackIndex = 0;
        playButton.setEnabled(false);
        solutionText.setText("已生成 22 步合法打乱：\n" + joinMoves(lastScrambleMoves) + "\n\n请点击“" + calculateButtonLabel() + "”，生成当前选择策略的可播放步骤。 ");
        playStatus.setText("已用系统安全随机源生成 22 步合法面转；将针对当前面片状态独立计算所选还原路线。 ");
        refreshAll();
    }

    private void resetCube() {
        cancelActiveSolve(false);
        stopPlayback();
        modelPreviewMode = false;
        cube.reset();
        if (editor != null) editor.adoptLiveState(cube);
        clearScrambleContext();
        solutionMoves.clear();
        layerStages.clear();
        solutionText.setText("魔方已重置为复原状态。\n现在可以扫描真实魔方，或直接随机打乱。");
        playButton.setEnabled(false);
        playStatus.setText("准备就绪");
        refreshAll();
    }

    /** 下方上色与识图直接更新唯一的三维主状态；灰色未知格也保留在该状态中。 */
    private void onColorDraftEdited() {
        if (editor == null) return;
        cancelActiveSolve(false);
        stopPlayback();
        clearScrambleContext();
        solutionMoves.clear();
        layerStages.clear();
        playbackIndex = 0;
        cube.setFacelets(editor.liveFacelets());
        modelPreviewMode = false;
        if (cubeView != null) cubeView.setFacelets(cube.facelets());
        if (stateText != null) stateText.setText(editor.entryStatus());
        if (solutionText != null) solutionText.setText("上色已更新主魔方；灰色格仍可继续拖动和扭层。填满后可生成所选还原路线。 ");
        if (playButton != null) playButton.setEnabled(false);
        if (playStatus != null) playStatus.setText(cube.hasUnknownStickers()
                ? "当前有 " + cube.unknownStickerCount() + " 个灰色未填格，可继续上色或手动扭层。"
                : "颜色已完整同步，可计算解法或继续手动扭层。 ");
        refreshAll();
    }

    /** 顶部按钮用于明确将下方当前颜色再次同步到主模型。 */
    private void syncColorToLiveCube() {
        if (editor == null) return;
        onColorDraftEdited();
        toast("已同步上色到主魔方。 ");
    }

    private void calculateSolution() {
        if (solveInProgress) {
            cancelActiveSolve(true);
            return;
        }
        stopPlayback();
        if (cube.hasUnknownStickers()) {
            solutionText.setText("还有 " + cube.unknownStickerCount() + " 个灰色格未填写；可继续滑动魔方层，但需填满后才能计算还原。 ");
            playStatus.setText("还原校验等待完整颜色状态。 ");
            return;
        }
        final SolveMethod requestedMethod = selectedSolveMethod;
        if (requiresStageWarmUp(requestedMethod) && !isStageSolverReady(requestedMethod)) {
            pendingStageCalculateMethod = requestedMethod;
            startStageSolverWarmUp(requestedMethod);
            solutionText.setText("正在后台准备“" + requestedMethod.displayName() + "”的阶段剪枝表；准备完成后将自动计算。 ");
            playStatus.setText(requestedMethod == SolveMethod.ROUX
                    ? "正在加载随 APK 内置的 Roux 阶段表；不会在手机上现场构建大型剪枝表。 "
                    : "首次使用正在准备独立阶段搜索，不占用 12 秒实际求解保护。 ");
            return;
        }
        if (requiresKociembaTables(requestedMethod) && solverInitializationFailed) {
            solutionText.setText("正在重新加载 Kociemba 查表资源；加载完成后请再次计算当前状态。 ");
            startSolverWarmUp();
            return;
        }
        if (requiresKociembaTables(requestedMethod) && !solverTablesReady) {
            pendingCalculateAfterLoad = true;
            solutionText.setText("正在加载 Kociemba 查表资源；加载完成后将自动求解当前状态。 ");
            playStatus.setText("正在加载内置坐标与剪枝表，最长等待 8 秒。 ");
            return;
        }
        if (cube.normalizeOrientationForSolver()) {
            modelPreviewMode = false;
            if (editor != null) editor.adoptLiveState(cube);
            refreshAll();
            toast("已按中心块方向重新对齐，现可计算解法。 ");
        }
        String snapshot = cube.facelets();
        String validation = SolverFacade.validate(snapshot);
        if (validation != null) {
            solutionText.setText("无法求解：" + validation);
            playStatus.setText("请先修正面片。当前 " + colorSummary());
            toast(validation);
            return;
        }
        if (SolverFacade.isSolved(snapshot)) {
            solutionMoves.clear();
            layerStages.clear();
            solutionText.setText("魔方已经复原，无需再执行还原步骤。");
            playButton.setEnabled(false);
            playStatus.setText("当前状态已复原。");
            return;
        }

        final long requestId = ++solveRequestId;
        stabilizeSolutionPanel();
        solveInProgress = true;
        scheduleSolveTimeout(requestId);
        playButton.setEnabled(false);
        solveButton.setEnabled(true);
        solveButton.setText("取消计算");
        final SolveMethod methodAtRequest = requestedMethod;
        solutionText.setText(methodAtRequest == SolveMethod.LAYER_BY_LAYER
                ? "正在按真实层先法依次规划底层十字、首层、中层和顶层…"
                : methodAtRequest == SolveMethod.CFOP
                ? "正在按真实 CFOP 依次规划 Cross、F2L、OLL、PLL…"
                : methodAtRequest == SolveMethod.ROUX
                ? "正在按真实 Roux 依次规划 First Block、Second Block、CMLL、LSE…"
                : methodAtRequest == SolveMethod.ZZ
                ? "正在按真实 ZZ 依次规划 EOLine、受限 ZZ-F2L、OCLL、标准 PLL…"
                : "正在使用 Kociemba 两阶段算法计算当前状态的标准解法…");
        playStatus.setText(methodAtRequest == SolveMethod.LAYER_BY_LAYER
                ? "层先法每个阶段都会实际验证目标达成后再进入下一阶段。 "
                : methodAtRequest == SolveMethod.CFOP
                ? "CFOP 每个阶段都会实际验证 Cross、F2L、OLL、PLL 目标。 "
                : methodAtRequest == SolveMethod.ROUX
                ? "Roux 将逐段验证两个 1×2×3 块、CMLL 与仅 M/U 的 LSE。 "
                : methodAtRequest == SolveMethod.ZZ
                ? "ZZ 将逐段验证全棱定向、DF/DB Line、受限 F2L、OCLL 与标准 PLL。 "
                : "设备端两阶段搜索进行中，正在求解当前状态。 ");
        activeSolveFuture = solveExecutor.submit(() -> {
            try {
                final List<String> parsed;
                final List<LayerByLayerSolver.Stage> stages;
                if (methodAtRequest == SolveMethod.LAYER_BY_LAYER) {
                    LayerByLayerSolver.Result result = LayerByLayerSolver.solve(snapshot);
                    parsed = result.moves();
                    stages = result.stages();
                } else if (methodAtRequest == SolveMethod.CFOP) {
                    CfopSolver.Result result = CfopSolver.solve(snapshot);
                    parsed = result.moves();
                    stages = result.stages();
                } else if (methodAtRequest == SolveMethod.ROUX) {
                    RouxSolver.Result result = RouxSolver.solve(snapshot, (title, detail, stageIndex, stageCount) ->
                            runOnUiThread(() -> {
                                if (requestId != solveRequestId || !solveInProgress) return;
                                solutionText.setText("正在规划真实 Roux 第 " + stageIndex + "/" + stageCount
                                        + " 阶段：" + title + "…");
                                playStatus.setText(detail + " 可随时点击“取消计算”。");
                            }));
                    parsed = result.moves();
                    stages = result.stages();
                } else if (methodAtRequest == SolveMethod.ZZ) {
                    ZzSolver.Result result = ZzSolver.solve(snapshot);
                    parsed = result.moves();
                    stages = result.stages();
                } else {
                    parsed = CubeState.parseMoves(SolverFacade.solve(snapshot));
                    stages = new ArrayList<>();
                }
                runOnUiThread(() -> finishSolveSuccess(requestId, snapshot, methodAtRequest, parsed, stages));
            } catch (Throwable e) {
                if (e instanceof CancellationException || Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    runOnUiThread(() -> finishSolveCancelled(requestId));
                } else {
                    runOnUiThread(() -> finishSolveFailure(requestId, messageOf(e)));
                }
            }
        });
    }

    /** 仅 Kociemba 与内部需两阶段输入的层先法依赖随 APK 加载的坐标表。 */
    private static boolean requiresKociembaTables(SolveMethod method) {
        return method == SolveMethod.KOCIEMBA || method == SolveMethod.LAYER_BY_LAYER;
    }

    private static boolean requiresStageWarmUp(SolveMethod method) {
        return method == SolveMethod.ROUX || method == SolveMethod.ZZ;
    }

    private boolean isStageSolverReady(SolveMethod method) {
        return method == SolveMethod.ROUX ? rouxStageReady : method == SolveMethod.ZZ && zzStageReady;
    }

    /** 首次请求时仅预热所选路线，避免 Roux 大型表阻塞 ZZ 或额外占满内存。 */
    private void startStageSolverWarmUp(SolveMethod method) {
        if (method == SolveMethod.ROUX) {
            if (rouxStageReady || rouxWarmUpFuture != null) return;
            rouxWarmUpFuture = stageWarmUpExecutor.submit(() -> warmStageSolver(method));
        } else if (method == SolveMethod.ZZ) {
            if (zzStageReady || zzWarmUpFuture != null) return;
            zzWarmUpFuture = stageWarmUpExecutor.submit(() -> warmStageSolver(method));
        }
    }

    private void warmStageSolver(SolveMethod method) {
        try {
            if (method == SolveMethod.ROUX) {
                try (java.io.InputStream tableResource = getAssets().open(ROUX_TABLE_ASSET, AssetManager.ACCESS_STREAMING)) {
                    RouxSolver.warmUp(tableResource);
                }
            } else {
                ZzSolver.warmUp();
            }
            runOnUiThread(() -> {
                if (method == SolveMethod.ROUX) { rouxStageReady = true; rouxWarmUpFuture = null; }
                else { zzStageReady = true; zzWarmUpFuture = null; }
                if (pendingStageCalculateMethod == method && !solveInProgress && selectedSolveMethod == method) {
                    pendingStageCalculateMethod = null;
                    calculateSolution();
                } else if (!solveInProgress && selectedSolveMethod == method) {
                    playStatus.setText(method.displayName() + " 阶段表已准备，可立即计算。 ");
                }
            });
        } catch (Throwable error) {
            runOnUiThread(() -> {
                if (method == SolveMethod.ROUX) rouxWarmUpFuture = null; else zzWarmUpFuture = null;
                if (pendingStageCalculateMethod == method) pendingStageCalculateMethod = null;
                if (!solveInProgress && selectedSolveMethod == method) {
                    solutionText.setText(method.displayName() + " 阶段表准备失败：" + messageOf(error));
                    playStatus.setText("未开始实际求解；可重新点击计算，或选择其他方法。 ");
                }
            });
        }
    }

    private void finishSolveSuccess(long requestId, String snapshot, SolveMethod methodAtRequest,
                                    List<String> parsed, List<LayerByLayerSolver.Stage> stages) {
        if (requestId != solveRequestId) return;
        solveInProgress = false;
        activeSolveFuture = null;
        cancelSolveTimeout();
        solveButton.setText("重新" + calculateButtonLabel());
        if (!snapshot.equals(cube.facelets()) || methodAtRequest != selectedSolveMethod) {
            solutionText.setText("魔方状态或还原方法已变化，请重新计算。");
            return;
        }
        solutionMoves.clear();
        layerStages.clear();
        layerStages.addAll(stages);
        solutionMoves.addAll(parsed);
        playbackIndex = 0;
        beforePlayback = snapshot;
        stabilizeSolutionPanel();
        if (parsed.isEmpty()) {
            solutionText.setText("魔方已经复原，无需还原步骤。");
            playButton.setEnabled(false);
            playStatus.setText("当前状态已复原。");
        } else {
            solutionText.setText(formatSolvedResult(parsed));
            playButton.setEnabled(true);
            playStatus.setText(selectedSolveMethod == SolveMethod.LAYER_BY_LAYER
                    ? "真实层先法已就绪：将按底层十字、首层、中层、顶层的顺序播放。"
                    : selectedSolveMethod == SolveMethod.CFOP
                    ? "真实 CFOP 已就绪：将按 Cross、F2L、OLL、PLL 的顺序播放。"
                    : selectedSolveMethod == SolveMethod.ROUX
                    ? "真实 Roux 已就绪：将按两个 Block、CMLL、M/U LSE 的顺序播放。"
                    : selectedSolveMethod == SolveMethod.ZZ
                    ? "真实 ZZ 已就绪：将按 EOLine、受限 F2L、OCLL、标准 PLL 的顺序播放。"
                    : "Kociemba 解法已就绪，可单步查看或自动还原。");
        }
    }

    private void finishSolveCancelled(long requestId) {
        if (requestId != solveRequestId) return;
        solveInProgress = false;
        activeSolveFuture = null;
        cancelSolveTimeout();
        solveButton.setText(calculateButtonLabel());
        stabilizeSolutionPanel();
        solutionText.setText("已取消计算。您可以继续编辑、打乱，或重新生成当前选择的还原路线。 ");
        playStatus.setText("计算未占用界面。 ");
    }

    private void finishSolveFailure(long requestId, String error) {
        if (requestId != solveRequestId) return;
        solveInProgress = false;
        activeSolveFuture = null;
        cancelSolveTimeout();
        solveButton.setText(calculateButtonLabel());
        stabilizeSolutionPanel();
        solutionText.setText("当前状态的“" + selectedSolveMethod.displayName() + "”求解未完成：" + error);
        playStatus.setText("请检查上色状态，或重新计算当前魔方状态。 ");
    }

    private void scheduleSolveTimeout(final long requestId) {
        cancelSolveTimeout();
        solveTimeoutTask = () -> {
            if (!solveInProgress || requestId != solveRequestId) return;
            if (activeSolveFuture != null) activeSolveFuture.cancel(true);
            activeSolveFuture = null;
            solveInProgress = false;
            solveRequestId++;
            stabilizeSolutionPanel();
            if (solveButton != null) solveButton.setText(calculateButtonLabel());
            if (solutionText != null) solutionText.setText("计算已在 12 秒保护时间内停止。当前页面仍可继续使用；已中断 Roux 阶段搜索，不会占用下一次计算。请重新计算，或改用高效计算机解。");
            if (playStatus != null) playStatus.setText("求解未产生步骤，未开始还原。 ");
        };
        handler.postDelayed(solveTimeoutTask, SOLVE_REQUEST_TIMEOUT_MS);
    }

    private void cancelSolveTimeout() {
        if (solveTimeoutTask != null) handler.removeCallbacks(solveTimeoutTask);
        solveTimeoutTask = null;
    }

    private void cancelActiveSolve(boolean showMessage) {
        if (!solveInProgress) return;
        solveRequestId++;
        if (activeSolveFuture != null) activeSolveFuture.cancel(true);
        activeSolveFuture = null;
        solveInProgress = false;
        cancelSolveTimeout();
        solveButton.setText(calculateButtonLabel());
        if (showMessage) {
            solutionText.setText("已取消计算。求解器会继续在后台完成预热，不会阻塞界面。 ");
            playStatus.setText("可以继续编辑、打乱或稍后重试。");
        }
    }

    private void clearScrambleContext() {
        lastScrambleMoves.clear();
    }

    private int axisOf(char face) {
        if (face == 'U' || face == 'D') return 0;
        if (face == 'R' || face == 'L') return 1;
        return 2;
    }

    private String messageOf(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) return "请检查录入状态后重试。";
        return error.getMessage();
    }

    private void togglePlayback() {
        if (solutionMoves.isEmpty()) return;
        if (playing) {
            stopPlayback();
        } else {
            if (!ensureCanRestore()) return;
            if (playbackIndex >= solutionMoves.size()) {
                cube.setFacelets(beforePlayback == null ? cube.facelets() : beforePlayback);
                if (editor != null) editor.adoptLiveState(cube);
                playbackIndex = 0;
                refreshAll();
                if (!ensureCanRestore()) return;
            }
            playing = true;
            playButton.setText("暂停还原");
            playbackTick.run();
        }
    }

    private final Runnable playbackTick = new Runnable() {
        @Override public void run() {
            if (!playing || cubeView == null || cubeView.isMoveAnimating()) return;
            if (!ensureCanRestore()) return;
            if (playbackIndex >= solutionMoves.size()) {
                playing = false;
                playButton.setText("再次演示");
                playStatus.setText("已完成“" + selectedSolveMethod.displayName() + "”展示；魔方已回到复原状态。 ");
                refreshAll();
                return;
            }
            final String move = solutionMoves.get(playbackIndex);
            final int ordinal = playbackIndex + 1;
            playStatus.setText(playbackStepLabel(ordinal, move));
            cubeView.animateMove(move, animationDurationMs(), () -> {
                if (!playing) return;
                cube.applyMove(move);
                if (editor != null) editor.applyLiveMove(move);
                playbackIndex++;
                refreshAll();
                handler.postDelayed(this, interMoveDelayMs());
            });
        }
    };

    private void stepPlayback() {
        if (solutionMoves.isEmpty()) { toast("请先生成当前选择策略的可播放步骤。 "); return; }
        if (cubeView != null && cubeView.isMoveAnimating()) return;
        if (!ensureCanRestore()) return;
        stopPlayback();
        if (playbackIndex >= solutionMoves.size()) {
            cube.setFacelets(beforePlayback == null ? cube.facelets() : beforePlayback);
            if (editor != null) editor.adoptLiveState(cube);
            playbackIndex = 0;
            refreshAll();
        }
        final String move = solutionMoves.get(playbackIndex);
        final int ordinal = playbackIndex + 1;
        playStatus.setText("第 " + ordinal + " / " + solutionMoves.size() + " 步：" + move);
        cubeView.animateMove(move, animationDurationMs(), () -> {
            cube.applyMove(move);
            if (editor != null) editor.applyLiveMove(move);
            playbackIndex++;
            refreshAll();
            if (playbackIndex >= solutionMoves.size()) {
                playButton.setText("再次演示");
                playStatus.setText("已完成“" + selectedSolveMethod.displayName() + "”展示的最后一步。再次演示可从头播放。 ");
            }
        });
    }

    private String playbackStepLabel(int ordinal, String move) {
        String prefix = "第 " + ordinal + " / " + solutionMoves.size() + " 步：" + move;
        if (layerStages.isEmpty()) return prefix;
        int consumed = 0;
        for (int i = 0; i < layerStages.size(); i++) {
            consumed += layerStages.get(i).moves().size();
            if (ordinal <= consumed) return prefix + "  ·  阶段 " + (i + 1) + "：" + layerStages.get(i).title();
        }
        return prefix + "  ·  " + selectedSolveMethod.displayName();
    }

    /** 仅播放已针对当前完整状态独立计算并校验过的解法步骤。 */
    private boolean ensureCanRestore() {
        if (cube.hasUnknownStickers()) {
            stopPlayback();
            playStatus.setText("还原已拦截：还有 " + cube.unknownStickerCount() + " 个灰色未填格。 ");
            solutionText.setText("请继续上色或识图补全灰色格后，再计算并开始还原。 ");
            toast("灰色格尚未填写，暂不能还原。");
            return false;
        }
                    if (cube.normalizeOrientationForSolver()) {
            modelPreviewMode = false;
            if (editor != null) editor.adoptLiveState(cube);
            refreshAll();
        }

        String validation = SolverFacade.validate(cube.facelets());
        if (validation != null) {
            stopPlayback();
            playStatus.setText("还原已拦截：当前状态不可还原。 ");
            solutionText.setText("无法开始还原：" + validation + "\n请在上色区修正后重新计算解法。 ");
            if (playButton != null) playButton.setEnabled(false);
            toast(validation);
            return false;
        }
        return true;
    }

    private int animationDurationMs() {
        int speed = speedBar == null ? 4 : speedBar.getProgress() + 1;
        return Math.max(160, 710 - speed * 52);
    }

    private int interMoveDelayMs() {
        int speed = speedBar == null ? 4 : speedBar.getProgress() + 1;
        return Math.max(30, 150 - speed * 11);
    }

    private void stopPlayback() {
        handler.removeCallbacks(playbackTick);
        if (cubeView != null) cubeView.cancelMoveAnimation();
        if (playing) playStatus.setText("还原已暂停。可继续自动还原或单步查看。");
        playing = false;
        if (playButton != null && !solutionMoves.isEmpty()) playButton.setText(playbackIndex >= solutionMoves.size() ? "再次演示" : "开始还原");
    }

    private void refreshAll() {
        if (cubeView != null) cubeView.setFacelets(modelPreviewMode && editor != null ? editor.previewFaceletsFor3D() : cube.facelets());
        if (editor != null) editor.refresh();
        if (stateText != null) stateText.setText(editor == null ? "颜色统计：" + colorSummary() : editor.entryStatus());
        if (heroStatus != null) heroStatus.setText(cube.facelets().equals(CubeState.SOLVED) ? "SOLVED" : "ACTIVE");
    }

    private String colorSummary() {
        StringBuilder result = new StringBuilder();
        for (char color : CubeState.FACE_ORDER.toCharArray()) {
            if (result.length() > 0) result.append("  ");
            result.append(color).append("=").append(cube.colorCount(color));
        }
        return result.toString();
    }

    private String joinMoves(List<String> moves) { return String.join("  ", moves); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    private TextView text(String content, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable gradient(int[] colors, int radiusDp, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams weighted(int width, int height, float weight, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (activeSolveFuture != null) activeSolveFuture.cancel(true);
        if (warmUpFuture != null) warmUpFuture.cancel(true);
        if (rouxWarmUpFuture != null) rouxWarmUpFuture.cancel(true);
        if (zzWarmUpFuture != null) zzWarmUpFuture.cancel(true);
        solveExecutor.shutdownNow();
        warmUpExecutor.shutdownNow();
        stageWarmUpExecutor.shutdownNow();
        super.onDestroy();
    }
}
