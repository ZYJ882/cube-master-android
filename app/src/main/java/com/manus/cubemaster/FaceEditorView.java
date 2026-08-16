package com.manus.cubemaster;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;

/** 用于修正识别结果或逐格手动录入的单面上色面板。 */
public final class FaceEditorView extends LinearLayout {
    public interface Listener { void onCubeEdited(); }

    private CubeState cube;
    private int activeFace = 0;
    private char selectedColor = 'U';
    private final AppCompatButton[] faceButtons = new AppCompatButton[6];
    private final AppCompatButton[] stickerButtons = new AppCompatButton[9];
    private final AppCompatButton[] paletteButtons = new AppCompatButton[6];
    private Listener listener;

    public FaceEditorView(Context context, CubeState cube) {
        super(context);
        this.cube = cube;
        setOrientation(VERTICAL);
        setPadding(dp(4), dp(4), dp(4), dp(4));
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

    private void build() {
        HorizontalScrollView facesScroll = new HorizontalScrollView(getContext());
        facesScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout faceRow = new LinearLayout(getContext());
        faceRow.setOrientation(HORIZONTAL);
        for (int i = 0; i < 6; i++) {
            final int face = i;
            AppCompatButton button = compactButton(CubeState.FACE_ORDER.substring(i, i + 1));
            button.setOnClickListener(v -> setActiveFace(face));
            faceButtons[i] = button;
            faceRow.addView(button, new LinearLayout.LayoutParams(dp(48), dp(38)) {{ setMargins(dp(2), 0, dp(2), 0); }});
        }
        facesScroll.addView(faceRow);
        addView(facesScroll, new LayoutParams(LayoutParams.MATCH_PARENT, dp(42)));

        GridLayout grid = new GridLayout(getContext());
        grid.setRowCount(3);
        grid.setColumnCount(3);
        grid.setPadding(dp(6), dp(8), dp(6), dp(8));
        for (int i = 0; i < 9; i++) {
            final int local = i;
            AppCompatButton sticker = new AppCompatButton(getContext());
            sticker.setText("");
            sticker.setPadding(0, 0, 0, 0);
            sticker.setOnClickListener(v -> {
                if (local == 4) return;
                cube.set(CubeState.stickerIndex(activeFace, local / 3, local % 3), selectedColor);
                refresh();
                if (listener != null) listener.onCubeEdited();
            });
            stickerButtons[i] = sticker;
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(52);
            params.columnSpec = GridLayout.spec(local % 3, 1f);
            params.rowSpec = GridLayout.spec(local / 3, 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(sticker, params);
        }
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, dp(184)));

        TextView hint = new TextView(getContext());
        hint.setText("选择颜色后点按面片；中心色固定为标准配色");
        hint.setTextColor(Color.rgb(170, 180, 195));
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(hint, new LayoutParams(LayoutParams.MATCH_PARENT, dp(24)));

        LinearLayout palette = new LinearLayout(getContext());
        palette.setGravity(Gravity.CENTER);
        for (int i = 0; i < 6; i++) {
            final char color = CubeState.FACE_ORDER.charAt(i);
            AppCompatButton swatch = new AppCompatButton(getContext());
            swatch.setText(String.valueOf(color));
            swatch.setTextColor(color == 'D' || color == 'U' ? Color.DKGRAY : Color.WHITE);
            swatch.setTextSize(12);
            swatch.setPadding(0, 0, 0, 0);
            swatch.setOnClickListener(v -> { selectedColor = color; refreshPalette(); });
            paletteButtons[i] = swatch;
            palette.addView(swatch, new LinearLayout.LayoutParams(dp(40), dp(38)) {{ setMargins(dp(2), 0, dp(2), 0); }});
        }
        addView(palette, new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));
    }

    private AppCompatButton compactButton(String text) {
        AppCompatButton button = new AppCompatButton(getContext());
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    public void refresh() {
        for (int i = 0; i < 6; i++) {
            faceButtons[i].setText((i == activeFace ? "● " : "") + CubeState.FACE_ORDER.charAt(i));
            faceButtons[i].setTextColor(i == activeFace ? Color.rgb(110, 231, 183) : Color.rgb(220, 226, 235));
            faceButtons[i].setBackground(background(i == activeFace ? Color.rgb(43, 59, 74) : Color.rgb(31, 37, 48), 8, Color.TRANSPARENT, 0));
        }
        for (int i = 0; i < 9; i++) {
            char color = cube.get(CubeState.stickerIndex(activeFace, i / 3, i % 3));
            boolean center = i == 4;
            stickerButtons[i].setEnabled(!center);
            stickerButtons[i].setBackground(background(CubeState.colorArgb(color), 8, center ? Color.WHITE : Color.rgb(28, 33, 42), center ? dp(2) : dp(1)));
            stickerButtons[i].setText(center ? String.valueOf(CubeState.FACE_ORDER.charAt(activeFace)) : "");
            stickerButtons[i].setTextColor(Color.argb(180, 20, 24, 30));
        }
        refreshPalette();
    }

    private void refreshPalette() {
        for (int i = 0; i < 6; i++) {
            char color = CubeState.FACE_ORDER.charAt(i);
            paletteButtons[i].setBackground(background(CubeState.colorArgb(color), 10,
                    color == selectedColor ? Color.WHITE : Color.rgb(48, 57, 71), color == selectedColor ? dp(3) : dp(1)));
        }
    }

    private GradientDrawable background(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
