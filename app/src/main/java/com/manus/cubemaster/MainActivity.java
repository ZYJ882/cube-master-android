package com.manus.cubemaster;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
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

import com.manus.cubemaster.solver.SolverFacade;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 液态玻璃版主界面：三维浏览、相机辅助录入、离线求解与动态还原。 */
public final class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION = 2001;
    private final CubeState cube = new CubeState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService solveExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService warmUpExecutor = Executors.newSingleThreadExecutor();
    private final List<String> solutionMoves = new ArrayList<>();
    private final List<String> lastScrambleMoves = new ArrayList<>();
    private final Random random = new Random();
    private Future<?> warmUpFuture;
    private Future<?> activeSolveFuture;
    private boolean solveInProgress = false;
    private long solveRequestId = 0L;
    private String scrambleState;
    /** 仅在用户按下“刷新上色”后启用：未确认的面片在 3D 模型中显示为灰色。 */
    private boolean modelPreviewMode = false;

    private Cube3DView cubeView;
    private FaceEditorView editor;
    private TextView stateText;
    private TextView solutionText;
    private TextView playStatus;
    private TextView speedLabel;
    private TextView heroStatus;
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
        AppCompatButton refreshModel = glassAction("刷新上色", Color.argb(78, 121, 216, 255), Color.rgb(235, 250, 255));
        refreshModel.setTextSize(10);
        refreshModel.setContentDescription("同步上色到三维魔方");
        refreshModel.setOnClickListener(v -> syncEditorPreviewTo3D());
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(dp(82), dp(28));
        refreshParams.setMargins(dp(6), 0, 0, 0);
        meta.addView(refreshModel, refreshParams);
        hero.addView(meta, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));

        FrameLayout stage = new FrameLayout(this);
        stage.setBackground(gradient(new int[]{Color.argb(42, 8, 24, 58), Color.argb(18, 255, 255, 255)}, 24, Color.argb(64, 213, 242, 255), dp(1)));
        cubeView = new Cube3DView(this);
        cubeView.setContentDescription("可拖拽旋转浏览的三维魔方");
        cubeView.setTapListener(() -> cubeView.resetCamera());
        stage.addView(cubeView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(312)));
        TextView cue = text("连续拖拽环绕 · 松手后惯性滑行 · 双击回主视角", 11, Color.rgb(190, 215, 239));
        cue.setGravity(Gravity.CENTER);
        cue.setBackground(gradient(new int[]{Color.argb(66, 16, 32, 66), Color.argb(28, 255, 255, 255)}, 15, Color.argb(42, 219, 242, 255), dp(1)));
        FrameLayout.LayoutParams cueParams = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cueParams.bottomMargin = dp(11);
        stage.addView(cue, cueParams);
        hero.addView(stage, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(312)));

        TextView brief = text("立体状态与真实面片颜色同步更新", 12, Color.rgb(177, 207, 233));
        brief.setPadding(dp(3), dp(10), dp(3), 0);
        hero.addView(brief, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
        return hero;
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
        editor.setListener(this::onCubeEdited);
        panel.addView(editor, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return panel;
    }

    private View buildSolutionPanel() {
        GlassCardLayout panel = glassCard(Color.argb(53, 167, 235, 255));
        panel.setPadding(dp(14), dp(13), dp(14), dp(13));
        panel.addView(sectionHead("SOLUTION STUDIO", "计算与还原"));
        TextView explain = text("离线搜索会校验颜色、朝向与奇偶性，再生成可播放的标准记号步骤。", 12, Color.rgb(195, 219, 241));
        explain.setPadding(0, dp(6), 0, dp(10));
        panel.addView(explain);
        solveButton = glassAction("✦  计算高效解法", Color.rgb(149, 243, 197), Color.rgb(7, 29, 31));
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
            cube.setFace(face, values);
            editor.setActiveFace(face);
            editor.markFaceCaptured(face);
            modelPreviewMode = false;
            if (cubeView != null) cubeView.setFacelets(cube.facelets());
            onCubeEdited();
            String[] names = {"白色", "红色", "绿色", "黄色", "橙色", "蓝色"};
            toast("已导入" + names[face] + "面，请在下方复核颜色。");
        }).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) openScanner();
            else toast("未获得相机权限，您仍可以使用手动上色功能。");
        }
    }

    private void startSolverWarmUp() {
        warmUpFuture = warmUpExecutor.submit(() -> {
            try {
                SolverFacade.warmUp();
                runOnUiThread(() -> {
                    if (!solveInProgress && playStatus != null) playStatus.setText("求解器已就绪，可计算任意合法状态。");
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    if (!solveInProgress && playStatus != null) playStatus.setText("求解器预热未完成；仍可使用随机打乱的一键逆序还原。");
                });
            }
        });
    }

    private void applyManualMove(String move) {
        cancelActiveSolve(false);
        stopPlayback();
        modelPreviewMode = false;
        cube.applyMove(move);
        clearScrambleContext();
        solutionMoves.clear();
        solutionText.setText("状态已手动改变，请重新计算解法。");
        playButton.setEnabled(false);
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
        scrambleState = cube.facelets();
        solutionMoves.clear();
        solutionMoves.addAll(inverseMoves(lastScrambleMoves));
        playbackIndex = 0;
        beforePlayback = scrambleState;
        solutionText.setText("已生成 22 步合法打乱：\n" + joinMoves(lastScrambleMoves) + "\n\n已保留可逆路线（" + solutionMoves.size() + " 步），可直接开始还原。");
        playButton.setEnabled(true);
        playStatus.setText("打乱仅由合法面转动组成，保证可还原；已禁用同面和同轴连续转动。");
        refreshAll();
    }

    private void resetCube() {
        cancelActiveSolve(false);
        stopPlayback();
        modelPreviewMode = false;
        cube.reset();
        clearScrambleContext();
        solutionMoves.clear();
        solutionText.setText("魔方已重置为复原状态。\n现在可以扫描真实魔方，或直接随机打乱。");
        playButton.setEnabled(false);
        playStatus.setText("准备就绪");
        refreshAll();
    }

    private void onCubeEdited() {
        cancelActiveSolve(false);
        stopPlayback();
        clearScrambleContext();
        solutionMoves.clear();
        if (!modelPreviewMode && cubeView != null) cubeView.setFacelets(cube.facelets());
        solutionText.setText(modelPreviewMode ? "上色已更改；按“刷新上色”即可更新灰显预览。" : "上色已更改。完成后将校验是否可还原。 ");
        playButton.setEnabled(false);
        refreshAll();
    }

    /** 手动同步：已确认格显示用户填写的颜色，尚未填写格显示灰色。 */
    private void syncEditorPreviewTo3D() {
        if (cubeView == null || editor == null) return;
        stopPlayback();
        modelPreviewMode = true;
        cubeView.setFacelets(editor.previewFaceletsFor3D());
        playStatus.setText("3D 上色预览已刷新：灰色表示尚未填写。 ");
        toast("已同步上色；灰色格子尚未填写。");
    }

    private void calculateSolution() {
        if (solveInProgress) {
            cancelActiveSolve(true);
            return;
        }
        stopPlayback();
        if (editor != null && editor.isManualEntryInProgress() && !editor.isEntryComplete()) {
            solutionText.setText("还有格子未填写。请按照“第几面”的提示完成九宫格后再计算。 ");
            playStatus.setText(editor.entryStatus());
            return;
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
            solutionText.setText("魔方已经复原，无需再执行还原步骤。");
            playButton.setEnabled(false);
            playStatus.setText("当前状态已复原。");
            return;
        }

        final long requestId = ++solveRequestId;
        solveInProgress = true;
        playButton.setEnabled(false);
        solveButton.setEnabled(true);
        solveButton.setText("取消计算");
        solutionText.setText("正在准备解法…\n如首次预热仍未完成，可点击“取消计算”返回界面。");
        playStatus.setText("设备端搜索进行中；随机打乱状态将优先使用已保存的可逆路线。");
        activeSolveFuture = solveExecutor.submit(() -> {
            try {
                final List<String> parsed;
                if (snapshot.equals(scrambleState) && !lastScrambleMoves.isEmpty()) {
                    parsed = inverseMoves(lastScrambleMoves);
                } else {
                    if (warmUpFuture != null) warmUpFuture.get(75, TimeUnit.SECONDS);
                    String solution = SolverFacade.solve(snapshot);
                    parsed = CubeState.parseMoves(solution);
                }
                runOnUiThread(() -> finishSolveSuccess(requestId, snapshot, parsed));
            } catch (InterruptedException | CancellationException e) {
                Thread.currentThread().interrupt();
                runOnUiThread(() -> finishSolveCancelled(requestId));
            } catch (TimeoutException e) {
                runOnUiThread(() -> finishSolveFailure(requestId, "求解器预热超过 75 秒。请稍后重试，或重新启动应用。"));
            } catch (ExecutionException e) {
                runOnUiThread(() -> finishSolveFailure(requestId, "求解器预热失败：" + messageOf(e.getCause())));
            } catch (Throwable e) {
                runOnUiThread(() -> finishSolveFailure(requestId, messageOf(e)));
            }
        });
    }

    private void finishSolveSuccess(long requestId, String snapshot, List<String> parsed) {
        if (requestId != solveRequestId) return;
        solveInProgress = false;
        activeSolveFuture = null;
        solveButton.setText("重新计算高效解法");
        if (!snapshot.equals(cube.facelets())) {
            solutionText.setText("魔方状态已变化，请重新计算。");
            return;
        }
        solutionMoves.clear();
        solutionMoves.addAll(parsed);
        playbackIndex = 0;
        beforePlayback = snapshot;
        if (parsed.isEmpty()) {
            solutionText.setText("魔方已经复原，无需还原步骤。");
            playButton.setEnabled(false);
            playStatus.setText("当前状态已复原。");
        } else {
            solutionText.setText("共 " + parsed.size() + " 步：\n" + joinMoves(parsed));
            playButton.setEnabled(true);
            playStatus.setText("解法已就绪，可单步查看或自动还原。");
        }
    }

    private void finishSolveCancelled(long requestId) {
        if (requestId != solveRequestId) return;
        solveInProgress = false;
        activeSolveFuture = null;
        solveButton.setText("计算高效解法");
        solutionText.setText("已取消计算。您可以继续编辑、打乱，或在求解器预热完成后重试。");
        playStatus.setText("计算未占用界面。 ");
    }

    private void finishSolveFailure(long requestId, String error) {
        if (requestId != solveRequestId) return;
        solveInProgress = false;
        activeSolveFuture = null;
        solveButton.setText("计算高效解法");
        solutionText.setText("求解未完成：" + error);
        playStatus.setText("请检查上色状态；随机打乱可直接使用“开始还原”。");
    }

    private void cancelActiveSolve(boolean showMessage) {
        if (!solveInProgress) return;
        solveRequestId++;
        if (activeSolveFuture != null) activeSolveFuture.cancel(true);
        activeSolveFuture = null;
        solveInProgress = false;
        solveButton.setText("计算高效解法");
        if (showMessage) {
            solutionText.setText("已取消计算。求解器会继续在后台完成预热，不会阻塞界面。 ");
            playStatus.setText("可以继续编辑、打乱或稍后重试。");
        }
    }

    private void clearScrambleContext() {
        lastScrambleMoves.clear();
        scrambleState = null;
    }

    private List<String> inverseMoves(List<String> source) {
        List<String> inverse = new ArrayList<>();
        for (int i = source.size() - 1; i >= 0; i--) inverse.add(inverseMove(source.get(i)));
        return inverse;
    }

    private String inverseMove(String move) {
        if (move.endsWith("2")) return move;
        if (move.endsWith("'")) return move.substring(0, move.length() - 1);
        return move + "'";
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
                playStatus.setText("已完成还原。魔方已回到复原状态。");
                refreshAll();
                return;
            }
            final String move = solutionMoves.get(playbackIndex);
            final int ordinal = playbackIndex + 1;
            playStatus.setText("第 " + ordinal + " / " + solutionMoves.size() + " 步：" + move);
            cubeView.animateMove(move, animationDurationMs(), () -> {
                if (!playing) return;
                cube.applyMove(move);
                playbackIndex++;
                refreshAll();
                handler.postDelayed(this, interMoveDelayMs());
            });
        }
    };

    private void stepPlayback() {
        if (solutionMoves.isEmpty()) { toast("请先计算解法。"); return; }
        if (cubeView != null && cubeView.isMoveAnimating()) return;
        if (!ensureCanRestore()) return;
        stopPlayback();
        if (playbackIndex >= solutionMoves.size()) {
            cube.setFacelets(beforePlayback == null ? cube.facelets() : beforePlayback);
            playbackIndex = 0;
            refreshAll();
        }
        final String move = solutionMoves.get(playbackIndex);
        final int ordinal = playbackIndex + 1;
        playStatus.setText("第 " + ordinal + " / " + solutionMoves.size() + " 步：" + move);
        cubeView.animateMove(move, animationDurationMs(), () -> {
            cube.applyMove(move);
            playbackIndex++;
            refreshAll();
            if (playbackIndex >= solutionMoves.size()) {
                playButton.setText("再次演示");
                playStatus.setText("已完成最后一步。再次演示可从头播放。 ");
            }
        });
    }

    /** 在播放前以及每步播放前检查 54 格颜色、中心块、朝向与奇偶性。 */
    private boolean ensureCanRestore() {
        if (editor != null && editor.isManualEntryInProgress() && !editor.isEntryComplete()) {
            stopPlayback();
            playStatus.setText("还原已拦截：请先完成全部 48 个非中心格。 ");
            solutionText.setText("当前录入未完成，不能开始还原。\n" + editor.entryStatus());
            toast("请先完成逐面上色。");
            return false;
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
        solveExecutor.shutdownNow();
        warmUpExecutor.shutdownNow();
        super.onDestroy();
    }
}
