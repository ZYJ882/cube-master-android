package com.manus.cubemaster;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

import com.manus.cubemaster.solver.SolverFacade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 魔方大师首版主界面：离线求解、相机辅助录入、3D 浏览和可调速还原。 */
public final class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION = 2001;
    private final CubeState cube = new CubeState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService solveExecutor = Executors.newSingleThreadExecutor();
    private final List<String> solutionMoves = new ArrayList<>();
    private final Random random = new Random();

    private Cube3DView cubeView;
    private FaceEditorView editor;
    private TextView stateText;
    private TextView solutionText;
    private TextView playStatus;
    private TextView speedLabel;
    private AppCompatButton solveButton;
    private AppCompatButton playButton;
    private SeekBar speedBar;
    private int playbackIndex = 0;
    private boolean playing = false;
    private String beforePlayback;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(16, 19, 26));
        getWindow().setNavigationBarColor(Color.rgb(16, 19, 26));
        setContentView(buildContent());
        refreshAll();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 19, 26));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("魔方大师", 28, Color.rgb(244, 247, 251));
        title.setTypeface(null, 1);
        header.addView(title);
        TextView subtitle = text("3×3 离线识别 · 高效求解 · 动态还原", 13, Color.rgb(170, 180, 195));
        subtitle.setPadding(0, dp(2), 0, dp(12));
        header.addView(subtitle);
        content.addView(header);

        LinearLayout cubeCard = panel();
        cubeCard.setPadding(dp(10), dp(8), dp(10), dp(8));
        cubeView = new Cube3DView(this);
        cubeView.setContentDescription("可拖拽旋转浏览的三维魔方");
        cubeView.setTapListener(() -> cubeView.resetCamera());
        cubeCard.addView(cubeView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(310)));
        TextView gestureHint = text("单指拖拽浏览 · 轻点复位视角", 12, Color.rgb(170, 180, 195));
        gestureHint.setGravity(Gravity.CENTER);
        cubeCard.addView(gestureHint, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(25)));
        content.addView(cubeCard, marginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 12));

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setGravity(Gravity.CENTER);
        AppCompatButton scan = actionButton("相机识图", Color.rgb(64, 110, 224), Color.WHITE);
        scan.setOnClickListener(v -> openScanner());
        quickRow.addView(scan, weighted(0, dp(48), 1f, 0, 0, dp(4), 0));
        AppCompatButton scramble = actionButton("随机打乱", Color.rgb(45, 55, 72), Color.WHITE);
        scramble.setOnClickListener(v -> scramble());
        quickRow.addView(scramble, weighted(0, dp(48), 1f, dp(4), 0, dp(4), 0));
        AppCompatButton reset = actionButton("重置魔方", Color.rgb(45, 55, 72), Color.WHITE);
        reset.setOnClickListener(v -> resetCube());
        quickRow.addView(reset, weighted(0, dp(48), 1f, dp(4), 0, 0, 0));
        content.addView(quickRow, marginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48), 0, 0, 0, 12));

        LinearLayout turnsPanel = panel();
        turnsPanel.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView turnsTitle = text("手动转动", 14, Color.rgb(244, 247, 251));
        turnsPanel.addView(turnsTitle);
        HorizontalScrollView turnScroll = new HorizontalScrollView(this);
        turnScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout turns = new LinearLayout(this);
        for (char face : CubeState.FACE_ORDER.toCharArray()) {
            String normal = String.valueOf(face);
            AppCompatButton clockwise = compactAction(normal, Color.rgb(40, 49, 63));
            clockwise.setOnClickListener(v -> applyManualMove(normal));
            turns.addView(clockwise, new LinearLayout.LayoutParams(dp(46), dp(40)) {{ setMargins(0, dp(6), dp(5), 0); }});
            String reverse = face + "′";
            AppCompatButton counter = compactAction(reverse, Color.rgb(40, 49, 63));
            counter.setOnClickListener(v -> applyManualMove(face + "'"));
            turns.addView(counter, new LinearLayout.LayoutParams(dp(46), dp(40)) {{ setMargins(0, dp(6), dp(7), 0); }});
        }
        turnScroll.addView(turns);
        turnsPanel.addView(turnScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        content.addView(turnsPanel, marginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 12));

        LinearLayout editorPanel = panel();
        editorPanel.setPadding(dp(10), dp(10), dp(10), dp(10));
        TextView editorTitle = text("手动上色 / 识别结果复核", 16, Color.rgb(244, 247, 251));
        editorPanel.addView(editorTitle);
        stateText = text("", 12, Color.rgb(170, 180, 195));
        stateText.setPadding(0, dp(2), 0, dp(5));
        editorPanel.addView(stateText);
        editor = new FaceEditorView(this, cube);
        editor.setListener(this::onCubeEdited);
        editorPanel.addView(editor, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(editorPanel, marginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 12));

        LinearLayout solvePanel = panel();
        solvePanel.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView solveTitle = text("计算与还原", 16, Color.rgb(244, 247, 251));
        solvePanel.addView(solveTitle);
        TextView explain = text("使用设备端两阶段搜索，结果按标准魔方记号显示。求解前会检查颜色数、朝向与奇偶性。", 12, Color.rgb(170, 180, 195));
        explain.setPadding(0, dp(4), 0, dp(8));
        solvePanel.addView(explain);
        solveButton = actionButton("计算高效解法", Color.rgb(110, 231, 183), Color.rgb(13, 22, 20));
        solveButton.setOnClickListener(v -> calculateSolution());
        solvePanel.addView(solveButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));
        solutionText = text("尚未计算解法", 14, Color.rgb(244, 247, 251));
        solutionText.setPadding(dp(4), dp(12), dp(4), dp(6));
        solutionText.setLineSpacing(dp(3), 1f);
        solvePanel.addView(solutionText);

        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setGravity(Gravity.CENTER_VERTICAL);
        speedLabel = text("还原速度：×4", 13, Color.rgb(220, 226, 235));
        speedRow.addView(speedLabel, new LinearLayout.LayoutParams(dp(112), dp(38)));
        speedBar = new SeekBar(this);
        speedBar.setMax(9);
        speedBar.setProgress(3);
        speedBar.setContentDescription("还原速度");
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { speedLabel.setText("还原速度：×" + (progress + 1)); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        speedRow.addView(speedBar, new LinearLayout.LayoutParams(0, dp(38), 1f));
        solvePanel.addView(speedRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        LinearLayout playbackRow = new LinearLayout(this);
        playButton = actionButton("开始还原", Color.rgb(64, 110, 224), Color.WHITE);
        playButton.setEnabled(false);
        playButton.setOnClickListener(v -> togglePlayback());
        playbackRow.addView(playButton, weighted(0, dp(48), 1f, 0, 0, dp(4), 0));
        AppCompatButton step = actionButton("单步", Color.rgb(45, 55, 72), Color.WHITE);
        step.setOnClickListener(v -> stepPlayback());
        playbackRow.addView(step, weighted(0, dp(48), 0.56f, dp(4), 0, 0, 0));
        solvePanel.addView(playbackRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        playStatus = text("准备就绪", 12, Color.rgb(170, 180, 195));
        playStatus.setGravity(Gravity.CENTER);
        playStatus.setPadding(0, dp(7), 0, 0);
        solvePanel.addView(playStatus, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(29)));
        content.addView(solvePanel);

        TextView attribution = text("离线求解引擎基于 Apache-2.0 许可的两阶段算法实现；详见工程内 THIRD_PARTY_LICENSES.md。", 10, Color.rgb(120, 132, 148));
        attribution.setPadding(dp(4), dp(12), dp(4), 0);
        content.addView(attribution);
        return scroll;
    }

    private void openScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
            return;
        }
        new CameraScanDialog(this, this, 0, (face, values) -> {
            cube.setFace(face, values);
            editor.setActiveFace(face);
            onCubeEdited();
            toast("已导入 " + CubeState.FACE_ORDER.charAt(face) + " 面，请在下方复核颜色。");
        }).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) openScanner();
            else toast("未获得相机权限，您仍可以使用手动上色功能。");
        }
    }

    private void applyManualMove(String move) {
        stopPlayback();
        cube.applyMove(move);
        solutionMoves.clear();
        solutionText.setText("状态已手动改变，请重新计算解法。");
        playButton.setEnabled(false);
        refreshAll();
    }

    private void scramble() {
        stopPlayback();
        cube.reset();
        List<String> scramble = new ArrayList<>();
        char previous = 0;
        for (int i = 0; i < 22; i++) {
            char face;
            do { face = CubeState.FACE_ORDER.charAt(random.nextInt(6)); } while (face == previous);
            previous = face;
            int modifier = random.nextInt(3);
            String move = String.valueOf(face) + (modifier == 1 ? "2" : modifier == 2 ? "'" : "");
            scramble.add(move);
            cube.applyMove(move);
        }
        solutionMoves.clear();
        solutionText.setText("已打乱：" + joinMoves(scramble));
        playButton.setEnabled(false);
        playStatus.setText("请点击“计算高效解法”。");
        refreshAll();
    }

    private void resetCube() {
        stopPlayback();
        cube.reset();
        solutionMoves.clear();
        solutionText.setText("魔方已重置为复原状态。");
        playButton.setEnabled(false);
        playStatus.setText("准备就绪");
        refreshAll();
    }

    private void onCubeEdited() {
        stopPlayback();
        solutionMoves.clear();
        solutionText.setText("上色状态已修改，请重新计算解法。");
        playButton.setEnabled(false);
        refreshAll();
    }

    private void calculateSolution() {
        stopPlayback();
        String snapshot = cube.facelets();
        String validation = SolverFacade.validate(snapshot);
        if (validation != null) {
            solutionText.setText("无法求解：" + validation);
            playStatus.setText("请先修正面片。当前 " + colorSummary());
            toast(validation);
            return;
        }
        solveButton.setEnabled(false);
        solveButton.setText("正在预计算并搜索…");
        solutionText.setText("正在设备端计算，请稍候…");
        playStatus.setText("首次运行会建立必要的搜索表，耗时可能稍长。");
        solveExecutor.execute(() -> {
            try {
                String solution = SolverFacade.solve(snapshot);
                List<String> parsed = CubeState.parseMoves(solution);
                runOnUiThread(() -> {
                    solveButton.setEnabled(true);
                    solveButton.setText("重新计算高效解法");
                    if (!snapshot.equals(cube.facelets())) {
                        solutionText.setText("魔方状态已变化，请重新计算。");
                        return;
                    }
                    solutionMoves.clear();
                    solutionMoves.addAll(parsed);
                    playbackIndex = 0;
                    beforePlayback = snapshot;
                    solutionText.setText("共 " + parsed.size() + " 步：\n" + joinMoves(parsed));
                    playButton.setEnabled(!parsed.isEmpty());
                    playStatus.setText(parsed.isEmpty() ? "当前已复原。" : "解法已就绪，可单步查看或自动还原。");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    solveButton.setEnabled(true);
                    solveButton.setText("计算高效解法");
                    solutionText.setText("求解失败：" + (e.getMessage() == null ? "请检查录入状态。" : e.getMessage()));
                    playStatus.setText("未生成动画。可尝试重新扫描或手动修正颜色。");
                });
            }
        });
    }

    private void togglePlayback() {
        if (solutionMoves.isEmpty()) return;
        if (playing) {
            stopPlayback();
        } else {
            playing = true;
            playButton.setText("暂停还原");
            playbackTick.run();
        }
    }

    private final Runnable playbackTick = new Runnable() {
        @Override public void run() {
            if (!playing) return;
            if (playbackIndex >= solutionMoves.size()) {
                playing = false;
                playButton.setText("再次演示");
                playStatus.setText("已完成还原。魔方应处于复原状态。");
                refreshAll();
                return;
            }
            String move = solutionMoves.get(playbackIndex++);
            cube.applyMove(move);
            playStatus.setText("第 " + playbackIndex + " / " + solutionMoves.size() + " 步：" + move);
            refreshAll();
            int delay = Math.max(95, 900 - (speedBar.getProgress() + 1) * 78);
            handler.postDelayed(this, delay);
        }
    };

    private void stepPlayback() {
        if (solutionMoves.isEmpty()) { toast("请先计算解法。"); return; }
        stopPlayback();
        if (playbackIndex >= solutionMoves.size()) {
            cube.setFacelets(beforePlayback == null ? cube.facelets() : beforePlayback);
            playbackIndex = 0;
        }
        String move = solutionMoves.get(playbackIndex++);
        cube.applyMove(move);
        playStatus.setText("第 " + playbackIndex + " / " + solutionMoves.size() + " 步：" + move);
        refreshAll();
        if (playbackIndex >= solutionMoves.size()) playButton.setText("再次演示");
    }

    private void stopPlayback() {
        handler.removeCallbacks(playbackTick);
        if (playing) playStatus.setText("还原已暂停。可继续自动还原或单步查看。");
        playing = false;
        if (playButton != null && !solutionMoves.isEmpty()) playButton.setText(playbackIndex >= solutionMoves.size() ? "再次演示" : "开始还原");
    }

    private void refreshAll() {
        if (cubeView != null) cubeView.setFacelets(cube.facelets());
        if (editor != null) editor.refresh();
        if (stateText != null) stateText.setText("颜色统计：" + colorSummary() + "  ·  中心块：URFDLB");
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

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(round(Color.rgb(26, 31, 42), 18, Color.rgb(56, 66, 82), dp(1)));
        return panel;
    }

    private AppCompatButton actionButton(String label, int background, int foreground) {
        AppCompatButton button = new AppCompatButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setBackground(round(background, 12, Color.TRANSPARENT, 0));
        return button;
    }

    private AppCompatButton compactAction(String label, int background) {
        AppCompatButton button = actionButton(label, background, Color.rgb(240, 244, 250));
        button.setTextSize(13);
        return button;
    }

    private TextView text(String content, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
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

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        solveExecutor.shutdownNow();
        super.onDestroy();
    }
}
