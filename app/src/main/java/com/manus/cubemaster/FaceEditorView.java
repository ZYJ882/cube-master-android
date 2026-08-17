package com.manus.cubemaster;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;

import java.util.Arrays;

/**
 * 面向普通用户的逐面上色工具。以白、红、绿、黄、橙、蓝六种真实颜色引导，
 * 支持从零录入、识别后校正、自动跳面和完整性检查。
 */
public final class FaceEditorView extends LinearLayout {
    public interface Listener { void onCubeEdited(); }

    private static final String[] COLOR_NAMES = {"白色", "红色", "绿色", "黄色", "橙色", "蓝色"};
    private static final String[] SHORT_NAMES = {"白", "红", "绿", "黄", "橙", "蓝"};

    private CubeState cube;
    private int activeFace = 0;
    private char selectedColor = 'U';
    private final AppCompatButton[] faceButtons = new AppCompatButton[6];
    private final AppCompatButton[] stickerButtons = new AppCompatButton[9];
    private final AppCompatButton[] paletteButtons = new AppCompatButton[6];
    private final boolean[] confirmed = new boolean[54];
    private boolean manualEntryInProgress = false;
    private TextView stepTitle;
    private TextView instructionText;
    private TextView completionText;
    private AppCompatButton restartButton;
    private Listener listener;

    public FaceEditorView(Context context, CubeState cube) {
        super(context);
        this.cube = cube;
        Arrays.fill(confirmed, true);
        setOrientation(VERTICAL);
        setPadding(0, dp(2), 0, dp(4));
        build();
        refresh();
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void setCube(CubeState cube) {
        this.cube = cube;
        refresh();
    }

    public void setActiveFace(int face) {
        activeFace = Math.max(0, Math.min(5, face));
        selectedColor = CubeState.FACE_ORDER.charAt(activeFace);
        refresh();
    }

    /** 从识图流程导入完整一面时，标记该面已由用户确认。 */
    public void markFaceCaptured(int face) {
        for (int i = 0; i < 9; i++) confirmed[CubeState.stickerIndex(face, i / 3, i % 3)] = true;
        if (manualEntryInProgress && allFacesComplete()) manualEntryInProgress = false;
        refresh();
    }

    public boolean isManualEntryInProgress() { return manualEntryInProgress; }
    public boolean isEntryComplete() { return !manualEntryInProgress || allFacesComplete(); }

    public String entryStatus() {
        if (manualEntryInProgress) return "手动录入进度：" + confirmedEditableCount() + " / 48 格";
        return "颜色统计：" + colorCounts();
    }

    private void build() {
        LinearLayout heading = new LinearLayout(getContext());
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setOrientation(VERTICAL);
        stepTitle = new TextView(getContext());
        stepTitle.setTextSize(18);
        stepTitle.setTextColor(Color.WHITE);
        stepTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(stepTitle, new LayoutParams(LayoutParams.MATCH_PARENT, dp(36)));
        addView(heading, new LayoutParams(LayoutParams.MATCH_PARENT, dp(38)));

        instructionText = new TextView(getContext());
        instructionText.setTextColor(Color.rgb(192, 220, 243));
        instructionText.setTextSize(11);
        instructionText.setMaxLines(2);
        instructionText.setLineSpacing(dp(1), 1f);
        addView(instructionText, new LayoutParams(LayoutParams.MATCH_PARENT, dp(34)));

        LinearLayout faceRow = new LinearLayout(getContext());
        faceRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < 6; i++) {
            final int face = i;
            AppCompatButton button = pillButton(SHORT_NAMES[i]);
            button.setOnClickListener(v -> setActiveFace(face));
            faceButtons[i] = button;
            faceRow.addView(button, new LinearLayout.LayoutParams(0, dp(40), 1f) {{ setMargins(dp(2), 0, dp(2), 0); }});
        }
        addView(faceRow, new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));

        // 固定的 3×3 小正方形：手机上不会再被权重规则拉成长条。
        LinearLayout board = new LinearLayout(getContext());
        board.setOrientation(VERTICAL);
        board.setGravity(Gravity.CENTER_HORIZONTAL);
        board.setPadding(0, dp(4), 0, dp(2));
        for (int row = 0; row < 3; row++) {
            LinearLayout rowView = new LinearLayout(getContext());
            rowView.setGravity(Gravity.CENTER);
            for (int col = 0; col < 3; col++) {
                final int local = row * 3 + col;
                AppCompatButton sticker = new AppCompatButton(getContext());
                sticker.setAllCaps(false);
                sticker.setPadding(0, 0, 0, 0);
                sticker.setTextSize(11);
                sticker.setOnClickListener(v -> paintSticker(local));
                stickerButtons[local] = sticker;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(62), dp(62));
                params.setMargins(dp(3), dp(3), dp(3), dp(3));
                rowView.addView(sticker, params);
            }
            board.addView(rowView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(68)));
        }
        addView(board, new LayoutParams(LayoutParams.MATCH_PARENT, dp(210)));

        completionText = new TextView(getContext());
        completionText.setTextColor(Color.rgb(194, 220, 243));
        completionText.setTextSize(11);
        completionText.setGravity(Gravity.CENTER);
        addView(completionText, new LayoutParams(LayoutParams.MATCH_PARENT, dp(24)));

        TextView chooseLabel = label("选择颜色后，点九宫格上色");
        chooseLabel.setPadding(dp(2), dp(2), 0, 0);
        addView(chooseLabel, new LayoutParams(LayoutParams.MATCH_PARENT, dp(20)));

        LinearLayout palette = new LinearLayout(getContext());
        palette.setGravity(Gravity.CENTER);
        for (int i = 0; i < 6; i++) {
            final int colorIndex = i;
            AppCompatButton swatch = new AppCompatButton(getContext());
            swatch.setAllCaps(false);
            swatch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            swatch.setTextSize(10);
            swatch.setPadding(0, 0, 0, 0);
            swatch.setOnClickListener(v -> { selectedColor = CubeState.FACE_ORDER.charAt(colorIndex); refresh(); });
            paletteButtons[i] = swatch;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1f);
            params.setMargins(dp(2), 0, dp(2), 0);
            palette.addView(swatch, params);
        }
        addView(palette, new LayoutParams(LayoutParams.MATCH_PARENT, dp(50)));

        LinearLayout footer = new LinearLayout(getContext());
        footer.setGravity(Gravity.CENTER_VERTICAL);
        restartButton = pillButton("从头录入");
        restartButton.setTextColor(Color.rgb(235, 247, 255));
        restartButton.setOnClickListener(v -> startFreshEntry());
        footer.addView(restartButton, new LinearLayout.LayoutParams(0, dp(38), 1f));
        TextView note = new TextView(getContext());
        note.setText("中心块已固定");
        note.setTextColor(Color.rgb(172, 205, 232));
        note.setTextSize(10);
        note.setGravity(Gravity.CENTER);
        footer.addView(note, new LinearLayout.LayoutParams(dp(90), dp(38)) {{ setMargins(dp(6), 0, 0, 0); }});
        addView(footer, new LayoutParams(LayoutParams.MATCH_PARENT, dp(42)));
    }

    private void paintSticker(int local) {
        if (local == 4) return;
        int index = CubeState.stickerIndex(activeFace, local / 3, local % 3);
        cube.set(index, selectedColor);
        confirmed[index] = true;
        boolean faceNowComplete = isFaceComplete(activeFace);
        if (faceNowComplete) autoAdvance();
        if (manualEntryInProgress && allFacesComplete()) manualEntryInProgress = false;
        refresh();
        if (listener != null) listener.onCubeEdited();
    }

    private void startFreshEntry() {
        manualEntryInProgress = true;
        Arrays.fill(confirmed, false);
        for (int face = 0; face < 6; face++) confirmed[CubeState.stickerIndex(face, 1, 1)] = true;
        activeFace = 0;
        selectedColor = 'U';
        refresh();
        if (listener != null) listener.onCubeEdited();
    }

    private void autoAdvance() {
        for (int offset = 1; offset <= 6; offset++) {
            int candidate = (activeFace + offset) % 6;
            if (!isFaceComplete(candidate)) {
                activeFace = candidate;
                selectedColor = CubeState.FACE_ORDER.charAt(candidate);
                return;
            }
        }
    }

    public void refresh() {
        if (stepTitle == null) return;
        String colorName = COLOR_NAMES[activeFace];
        stepTitle.setText("第 " + (activeFace + 1) + " / 6 面：" + colorName + "面");
        instructionText.setText(manualEntryInProgress
                ? "请让「" + colorName + "中心块」朝向自己，然后按实际颜色填写周围 8 格。填完会自动进入下一面。"
                : "识别有误时：先选择正确颜色，再点要修改的格子。若从零录入，请点下方“从头录入”。");

        for (int i = 0; i < 6; i++) {
            boolean active = i == activeFace;
            boolean complete = isFaceComplete(i);
            faceButtons[i].setText(SHORT_NAMES[i] + (complete ? " ✓" : ""));
            faceButtons[i].setTextColor(active ? Color.rgb(7, 30, 45) : Color.rgb(239, 249, 255));
            int fill = active ? CubeState.colorArgb(CubeState.FACE_ORDER.charAt(i)) : Color.argb(64, 238, 250, 255);
            faceButtons[i].setBackground(gradient(new int[]{lighten(fill, 12), fill}, 15,
                    active ? Color.WHITE : Color.argb(100, 215, 242, 255), active ? dp(2) : dp(1)));
        }

        for (int i = 0; i < 9; i++) {
            int index = CubeState.stickerIndex(activeFace, i / 3, i % 3);
            boolean center = i == 4;
            boolean known = confirmed[index];
            char color = cube.get(index);
            stickerButtons[i].setEnabled(!center);
            if (!known && !center) {
                stickerButtons[i].setText("点我\n填写");
                stickerButtons[i].setTextColor(Color.rgb(179, 213, 239));
                stickerButtons[i].setBackground(gradient(new int[]{Color.argb(52, 237, 250, 255), Color.argb(34, 117, 185, 255)}, 18, Color.argb(122, 220, 243, 255), dp(1)));
            } else {
                stickerButtons[i].setText(center ? SHORT_NAMES[activeFace] + "\n中心" : "");
                stickerButtons[i].setTextColor(Color.argb(215, 7, 24, 36));
                stickerButtons[i].setBackground(stickerBackground(CubeState.colorArgb(color), center));
            }
        }

        completionText.setText(manualEntryInProgress
                ? "已填写 " + confirmedEditableCount() + " / 48 格 · " + (allFacesComplete() ? "录入完成，可计算解法" : "继续填写即可")
                : "当前选择：" + colorName + " · 点格子即可改色 · 所有中心块已固定");
        restartButton.setText(manualEntryInProgress ? "重新开始录入" : "从头录入");
        refreshPalette();
    }

    private void refreshPalette() {
        for (int i = 0; i < 6; i++) {
            char color = CubeState.FACE_ORDER.charAt(i);
            int entered = enteredColorCount(color);
            boolean chosen = color == selectedColor;
            int base = CubeState.colorArgb(color);
            paletteButtons[i].setText(COLOR_NAMES[i] + "\n" + entered + " / 9" + (chosen ? "  ✓" : ""));
            paletteButtons[i].setTextColor(color == 'U' || color == 'D' || color == 'L' ? Color.rgb(10, 29, 41) : Color.WHITE);
            paletteButtons[i].setBackground(gradient(new int[]{lighten(base, 22), base}, 16,
                    chosen ? Color.WHITE : Color.argb(105, 229, 247, 255), chosen ? dp(3) : dp(1)));
        }
    }

    private boolean isFaceComplete(int face) {
        for (int i = 0; i < 9; i++) if (!confirmed[CubeState.stickerIndex(face, i / 3, i % 3)]) return false;
        return true;
    }

    private boolean allFacesComplete() {
        for (int face = 0; face < 6; face++) if (!isFaceComplete(face)) return false;
        return true;
    }

    private int confirmedEditableCount() {
        int count = 0;
        for (int face = 0; face < 6; face++) {
            for (int i = 0; i < 9; i++) if (i != 4 && confirmed[CubeState.stickerIndex(face, i / 3, i % 3)]) count++;
        }
        return count;
    }

    private int enteredColorCount(char color) {
        if (!manualEntryInProgress) return cube.colorCount(color);
        int count = 0;
        for (int index = 0; index < 54; index++) if (confirmed[index] && cube.get(index) == color) count++;
        return count;
    }

    private String colorCounts() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (result.length() > 0) result.append("  ");
            result.append(SHORT_NAMES[i]).append("=").append(cube.colorCount(CubeState.FACE_ORDER.charAt(i)));
        }
        return result.toString();
    }

    private TextView label(String content) {
        TextView label = new TextView(getContext());
        label.setText(content);
        label.setTextColor(Color.rgb(140, 223, 255));
        label.setTextSize(10);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setLetterSpacing(.13f);
        return label;
    }

    private AppCompatButton pillButton(String text) {
        AppCompatButton button = new AppCompatButton(getContext());
        button.setText(text);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private GradientDrawable stickerBackground(int color, boolean center) {
        return gradient(new int[]{lighten(color, 20), color}, 18,
                center ? Color.WHITE : Color.argb(166, 230, 248, 255), center ? dp(3) : dp(1));
    }

    private GradientDrawable gradient(int[] colors, int radiusDp, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private static int lighten(int color, int amount) {
        return Color.rgb(Math.min(255, Color.red(color) + amount), Math.min(255, Color.green(color) + amount), Math.min(255, Color.blue(color) + amount));
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
