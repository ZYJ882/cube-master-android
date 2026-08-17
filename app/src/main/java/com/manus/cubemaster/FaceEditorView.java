package com.manus.cubemaster;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;

/** 液态玻璃风格的面片编辑器：分段选面、锁定中心块及可视色板。 */
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
        setPadding(0, dp(2), 0, dp(2));
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
        TextView faceLabel = label("EDIT FACE");
        addView(faceLabel, new LayoutParams(LayoutParams.MATCH_PARENT, dp(19)));
        HorizontalScrollView facesScroll = new HorizontalScrollView(getContext());
        facesScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout faceRow = new LinearLayout(getContext());
        faceRow.setOrientation(HORIZONTAL);
        for (int i = 0; i < 6; i++) {
            final int face = i;
            AppCompatButton button = compactButton(CubeState.FACE_ORDER.substring(i, i + 1));
            button.setOnClickListener(v -> setActiveFace(face));
            faceButtons[i] = button;
            faceRow.addView(button, new LinearLayout.LayoutParams(dp(47), dp(39)) {{ setMargins(0, 0, dp(5), 0); }});
        }
        facesScroll.addView(faceRow);
        addView(facesScroll, new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));

        GridLayout grid = new GridLayout(getContext());
        grid.setRowCount(3);
        grid.setColumnCount(3);
        grid.setPadding(0, dp(9), 0, dp(8));
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
            params.height = dp(56);
            params.columnSpec = GridLayout.spec(local % 3, 1f);
            params.rowSpec = GridLayout.spec(local / 3, 1f);
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(sticker, params);
        }
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, dp(194)));

        TextView hint = new TextView(getContext());
        hint.setText("中心块固定为标准配色 · 先选色，再点按面片");
        hint.setTextColor(Color.rgb(181, 211, 238));
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(hint, new LayoutParams(LayoutParams.MATCH_PARENT, dp(25)));

        TextView paletteLabel = label("COLOR PALETTE");
        paletteLabel.setPadding(dp(2), dp(5), 0, 0);
        addView(paletteLabel, new LayoutParams(LayoutParams.MATCH_PARENT, dp(25)));
        LinearLayout palette = new LinearLayout(getContext());
        palette.setGravity(Gravity.CENTER);
        for (int i = 0; i < 6; i++) {
            final char color = CubeState.FACE_ORDER.charAt(i);
            AppCompatButton swatch = new AppCompatButton(getContext());
            swatch.setText(String.valueOf(color));
            swatch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            swatch.setTextColor(color == 'D' || color == 'U' ? Color.rgb(16, 35, 52) : Color.WHITE);
            swatch.setTextSize(12);
            swatch.setPadding(0, 0, 0, 0);
            swatch.setOnClickListener(v -> { selectedColor = color; refreshPalette(); });
            paletteButtons[i] = swatch;
            palette.addView(swatch, new LinearLayout.LayoutParams(dp(42), dp(40)) {{ setMargins(dp(2), 0, dp(2), 0); }});
        }
        addView(palette, new LayoutParams(LayoutParams.MATCH_PARENT, dp(45)));
    }

    private TextView label(String content) {
        TextView label = new TextView(getContext());
        label.setText(content);
        label.setTextColor(Color.rgb(140, 223, 255));
        label.setTextSize(10);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setLetterSpacing(.14f);
        return label;
    }

    private AppCompatButton compactButton(String text) {
        AppCompatButton button = new AppCompatButton(getContext());
        button.setText(text);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    public void refresh() {
        for (int i = 0; i < 6; i++) {
            boolean active = i == activeFace;
            faceButtons[i].setText((active ? "●  " : "") + CubeState.FACE_ORDER.charAt(i));
            faceButtons[i].setTextColor(active ? Color.rgb(10, 30, 48) : Color.rgb(232, 246, 255));
            faceButtons[i].setBackground(active
                    ? gradient(new int[]{Color.rgb(153, 239, 209), Color.rgb(124, 206, 255)}, 13, Color.argb(125, 238, 255, 255), dp(1))
                    : gradient(new int[]{Color.argb(70, 255, 255, 255), Color.argb(35, 133, 194, 255)}, 13, Color.argb(81, 212, 241, 255), dp(1)));
        }
        for (int i = 0; i < 9; i++) {
            char color = cube.get(CubeState.stickerIndex(activeFace, i / 3, i % 3));
            boolean center = i == 4;
            stickerButtons[i].setEnabled(!center);
            stickerButtons[i].setBackground(stickerBackground(CubeState.colorArgb(color), center));
            stickerButtons[i].setText(center ? String.valueOf(CubeState.FACE_ORDER.charAt(activeFace)) : "");
            stickerButtons[i].setTextColor(Color.argb(185, 8, 21, 33));
            stickerButtons[i].setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        refreshPalette();
    }

    private void refreshPalette() {
        for (int i = 0; i < 6; i++) {
            char color = CubeState.FACE_ORDER.charAt(i);
            boolean chosen = color == selectedColor;
            paletteButtons[i].setBackground(gradient(
                    new int[]{CubeState.colorArgb(color), lighten(CubeState.colorArgb(color), 26)},
                    14,
                    chosen ? Color.WHITE : Color.argb(110, 220, 242, 255),
                    chosen ? dp(3) : dp(1)));
        }
    }

    private GradientDrawable stickerBackground(int color, boolean center) {
        return gradient(new int[]{lighten(color, 18), color}, 16,
                center ? Color.rgb(245, 254, 255) : Color.argb(122, 238, 251, 255), center ? dp(3) : dp(1));
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
