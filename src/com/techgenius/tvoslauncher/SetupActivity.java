package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * First-time setup, in the style of Apple TV's own setup: one question per page, a large title
 * and a short line of help at the top, choices as a centred list (white when focused), OK moves
 * on and Back goes back. Pages: Welcome (in many languages), your profile, look, top shelf, weather, presentation,
 * home screen & permissions, done. Shown once; Settings \u2192 Home \u2192 Run Setup Again repeats it.
 */
public class SetupActivity extends Activity {
    static final String K_DONE = "setup_done";

    FrameLayout root;
    ImageView backdrop;
    LinearLayout page;
    TextView skipBtn;
    int index = 0;
    int lookSubStep = 0; // 0 = choose appearance, 1 = choose scenes, 2 = choose classic background
    final Handler handler = new Handler(Looper.getMainLooper());

    interface Step {
        void build();
    }

    final List<Step> steps = new ArrayList<>();

    static boolean done(Activity a) {
        return a.getSharedPreferences("setup", MODE_PRIVATE).getBoolean(K_DONE, false);
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(I18n.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Clicks.init(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View dim = new View(this);
        dim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x99000000, 0x66000000, 0xB3000000}));
        root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setClipChildren(true);
        page.setClipToPadding(true);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        page.setPaddingRelative(0, u(36), 0, u(22));
        root.addView(page, plp);

        // Top-right "Skip Setup" pill button on the first setup screen (language selection)
        skipBtn = new TextView(this);
        skipBtn.setId(View.generateViewId());
        skipBtn.setText(I18n.resourceString(SetupActivity.this, R.string.text_skip_setup) + "  " + chev());
        skipBtn.setTextColor(Color.WHITE);
        skipBtn.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(skipBtn, 14);
        skipBtn.setGravity(Gravity.CENTER);
        skipBtn.setFocusable(true);
        skipBtn.setClickable(true);
        skipBtn.setPaddingRelative(u(18), u(9), u(18), u(9));
        GradientDrawable skipBg = new GradientDrawable();
        skipBg.setCornerRadius(u(999));
        skipBg.setColor(0x2EFFFFFF);
        skipBg.setStroke(Math.max(1, u(1)), 0x40FFFFFF);
        skipBtn.setBackground(skipBg);
        skipBtn.setOnFocusChangeListener((v, has) -> {
            skipBg.setColor(has ? 0xF2FFFFFF : 0x2EFFFFFF);
            skipBg.setStroke(Math.max(1, u(1)), has ? Color.WHITE : 0x40FFFFFF);
            skipBtn.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(180).start();
        });
        skipBtn.setOnClickListener(v -> {
            Scenes.setClock24h(this, I18n.isDefault24hForLang(I18n.getLang(this)));
            getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean(K_DONE, true).putInt("cur_step", 0).apply();
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        FrameLayout.LayoutParams skipLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        skipLp.topMargin = u(26);
        skipLp.setMarginEnd(u(36));
        root.addView(skipBtn, skipLp);

        I18n.applyDirection(this, root);
        setContentView(root);

        steps.add(this::hello);
        steps.add(this::profile);
        steps.add(this::look);
        steps.add(this::weather);
        steps.add(this::presentation);
        steps.add(this::homeAndPermissions);
        steps.add(this::defaultHomeSetup);
        steps.add(this::buttonMapperSetup);
        steps.add(this::finish_);
        if (getIntent().getBooleanExtra("skip", false)) {  // adb: --ez skip true marks setup as done
            getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean(K_DONE, true).putInt("cur_step", 0).apply();
            finish();
            return;
        }
        String initLang = getIntent().getStringExtra("lang");
        if (initLang != null && !initLang.isEmpty()) {
            I18n.setLang(this, initLang);
        }
        int savedStep = getSharedPreferences("setup", MODE_PRIVATE).getInt("cur_step", 0);
        show(getIntent().getIntExtra("step", savedStep), 0);  // adb: --ei step N opens a page directly
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String lang = intent.getStringExtra("lang");
        if (lang != null && !lang.isEmpty()) {
            I18n.setLang(this, lang);
        }
        int savedStep = getSharedPreferences("setup", MODE_PRIVATE).getInt("cur_step", index);
        int target = intent.getIntExtra("step", savedStep);
        if (target != index || (lang != null && !lang.isEmpty())) {
            show(target, 0);
        } else if (index == 5 || index == 6 || index == 7) {
            show(index, 0);
        }
    }

    int u(float v) {
        return Ui.px(this, v);
    }

    // ---------------------------------------------------------------- navigation

    void show(int i, int dir) {
        handler.removeCallbacksAndMessages(null);
        if (i != 2) lookSubStep = 0;
        index = Math.max(0, Math.min(i, steps.size() - 1));
        getSharedPreferences("setup", MODE_PRIVATE).edit().putInt("cur_step", index).apply();
        if (skipBtn != null) {
            skipBtn.setText(I18n.resourceString(SetupActivity.this, R.string.text_skip_setup) + "  " + chev());
            skipBtn.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        }
        page.animate().cancel();
        page.removeAllViews();
        steps.get(index).build();
        // New page: slide and fade. Same page re-drawn after a switch: a quick fade only.
        page.setAlpha(dir == 0 ? 0.6f : 0f);
        page.setTranslationX(u(30) * dir * (I18n.isRtl(this) ? -1 : 1));
        page.animate().alpha(1f).translationX(0).setDuration(dir == 0 ? 120 : 320).start();
    }

    void next() {
        show(index + 1, 1);
    }

    @Override
    public void onBackPressed() {
        if (index == 2 && lookSubStep != 0) {
            lookSubStep = 0;
            show(2, -1);
            return;
        }
        if (index == 5 && permMode != 0) {
            permMode = 0;
            show(5, -1);
            return;
        }
        if (index > 0) show(index - 1, -1);
        // The first page has nowhere to go back to: setup stays until it is finished.
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (index == 5 || index == 6) show(index, 0);  // refresh the ticks
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (index == 5 || index == 6 || index == 7) show(index, 0);
    }

    // ---------------------------------------------------------------- building blocks

    TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Ui.font(this, "display"));
        t.setLetterSpacing(-0.02f);
        Ui.textSize(t, 34);
        t.setGravity(Gravity.CENTER);
        page.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    TextView subtitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xB3FFFFFF);
        t.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(t, 14);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(520), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = u(10);
        lp.bottomMargin = u(26);
        page.addView(t, lp);
        return t;
    }

    LinearLayout list() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setClipChildren(false);
        l.setClipToPadding(false);
        l.setPaddingRelative(u(22), u(6), u(22), u(6));
        if (page.findViewWithTag("list") == null) l.setTag("list");
        page.addView(l, new LinearLayout.LayoutParams(u(484), ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    LinearLayout scrollableList() {
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(true);
        sv.setClipToPadding(true);
        sv.setTag("scroll_container");
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setClipChildren(false);
        l.setClipToPadding(false);
        l.setPaddingRelative(u(22), u(6), u(22), u(6));
        if (page.findViewWithTag("list") == null) l.setTag("list");
        sv.addView(l, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(u(484), 0, 1f);
        page.addView(sv, svLp);
        return l;
    }

    /** A choice row: title, optional detail and a tick; white with dark text when focused. */
    View option(LinearLayout list, String label, String detail, boolean checked, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPaddingRelative(u(18), u(8), u(16), u(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(12));
        bg.setColor(0x26FFFFFF);
        r.setBackground(bg);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Color.WHITE);
        l.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(l, 15);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(l);
        TextView d = null;
        if (detail != null && !detail.isEmpty()) {
            d = new TextView(this);
            d.setText(detail);
            d.setTextColor(0x99FFFFFF);
            d.setTypeface(Ui.font(this, "regular"));
            Ui.textSize(d, 12);
            d.setSingleLine(true);
            d.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = u(2);
            texts.addView(d, dlp);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView tick = new TextView(this);
        tick.setText(checked ? "\u2713" : "");
        tick.setTextColor(0xFF30D158);
        tick.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(tick, 17);
        LinearLayout.LayoutParams tickLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tickLp.setMarginStart(u(10));
        r.addView(tick, tickLp);
        TextView dRef = d;
        r.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x26FFFFFF);
            l.setTextColor(has ? 0xFF111114 : Color.WHITE);
            if (dRef != null) dRef.setTextColor(has ? 0x99111114 : 0x99FFFFFF);
            tick.setTextColor(has ? 0xFF1E9E48 : 0xFF30D158);
            v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(180).start();
        });
        r.setOnClickListener(v -> action.run());
        r.setMinimumHeight(d == null ? u(46) : u(56));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = u(8);
        list.addView(r, lp);
        if (list.getChildCount() == 1 && "list".equals(list.getTag())) r.requestFocus();
        return r;
    }

    void dots() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 1; i < steps.size() - 1; i++) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == index ? Color.WHITE : 0x59FFFFFF);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(6), u(6));
            lp.leftMargin = lp.rightMargin = u(4);
            d.addView(dot, lp);
        }
        if (page.findViewWithTag("scroll_container") == null) {
            View spacer = new View(this);
            page.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        }
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = u(12);
        page.addView(d, dlp);
    }

    void setBackdrop(String style) {
        Bitmap b = null;
        if ("scenes".equals(style)) {
            b = Scenes.loadAssetBitmap(this, "backgrounds/scene-night.jpg", 1280, 720);
        } else if (style != null && style.startsWith("scene_asset:")) {
            b = Scenes.loadAssetBitmap(this, style.substring(12), 1280, 720);
        } else if (style != null && style.startsWith("photo:")) {
            b = Scenes.loadAssetBitmap(this, "backgrounds/" + style.substring(6), 1280, 720);
        }
        if (b == null) {
            b = MainActivity.makeWallpaperBase(style != null ? style : "aurora");
        }
        backdrop.setImageDrawable(new BitmapDrawable(getResources(), b));
        backdrop.animate().alpha(1f).setDuration(300).start();
    }

    // ---------------------------------------------------------------- pages

    /** Forward arrow that points the right way for the reading direction. */
    private String chev() {
        return I18n.isRtl(this) ? "\u2039" : "\u203a";
    }

    /** Welcome, then Get Started. */
    void hello() {
        backdrop.animate().alpha(0f).setDuration(300).start();
        TextView hi = title(I18n.resourceString(SetupActivity.this, R.string.text_welcome));
        Ui.textSize(hi, 52);
        hi.setAlpha(0f);
        hi.setTranslationY(u(12));
        hi.animate().alpha(1f).translationY(0).setDuration(700).start();
        // "Welcome" in Turkish, English, Swedish, Indian and world languages, fading from one to the next.
        String[] words = getResources().getStringArray(R.array.welcome_greetings);
        int[] k = {I18n.isTr(this) ? 1 : I18n.isRtl(this) ? words.length - 1 : 0};
        Runnable[] cycle = new Runnable[1];
        cycle[0] = () -> {
            k[0] = (k[0] + 1) % words.length;
            hi.animate().alpha(0f).setDuration(350).withEndAction(() -> {
                hi.setText(words[k[0]]);
                hi.animate().alpha(1f).setDuration(450).start();
            }).start();
            handler.postDelayed(cycle[0], 2200);
        };
        handler.postDelayed(cycle[0], 2400);
        TextView s = subtitle(I18n.resourceString(SetupActivity.this, R.string.text_let_s_set_up_tvos_launcher));
        Ui.textSize(s, 16);
        ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);
        LinearLayout l = scrollableList();
        String curLang = I18n.getLang(this);
        View startRow = option(l, I18n.resourceString(SetupActivity.this, R.string.text_get_started), null, false, () -> {
            Scenes.setClock24h(this, I18n.isDefault24hForLang(I18n.getLang(this)));
            next();
        });
        if (skipBtn != null) {
            startRow.setId(View.generateViewId());
            startRow.setNextFocusUpId(skipBtn.getId());
            startRow.setNextFocusRightId(skipBtn.getId());
            skipBtn.setNextFocusDownId(startRow.getId());
            skipBtn.setNextFocusLeftId(startRow.getId());
        }
        I18n.LanguageOption[] languages = I18n.languageOptions(this);
        for (int i = 0; i < languages.length; i++) {
            final int rowIdx = i + 1;
            final I18n.LanguageOption language = languages[i];
            final String code = language.code;
            View langRow = option(l, language.title, language.description, code.equals(curLang), () -> {
                boolean wasRtl = I18n.isRtl(this);
                I18n.setLang(this, code);
                if (wasRtl != I18n.isRtl(this)) {
                    // Reading direction changed: rebuild the screen so the whole layout flips.
                    getSharedPreferences("setup", MODE_PRIVATE).edit().putInt("cur_step", 0).apply();
                    recreate();
                    return;
                }
                show(0, 0);
                focusRow(rowIdx);
            });
            if (skipBtn != null) {
                langRow.setNextFocusRightId(skipBtn.getId());
            }
        }
        TextView by = new TextView(this);
        by.setText("by techgenius");
        by.setTextColor(0x80FFFFFF);
        by.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(by, 13);
        by.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams byLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        byLp.topMargin = u(12);
        page.addView(by, byLp);
    }

    void profile() {
        title(I18n.resourceString(SetupActivity.this, R.string.text_create_your_profile));
        subtitle(I18n.resourceString(SetupActivity.this, R.string.text_your_name_and_an_avatar_each_profile_kee));
        Profiles.Profile p = Profiles.current(this);
        LinearLayout l = list();
        View nameRow = option(l, I18n.resourceString(SetupActivity.this, R.string.text_name), I18n.profileName(this, p.name), false, () -> {
            EditText in = new EditText(this);
            in.setSingleLine(true);
            in.setText(p.name);
            in.setSelection(in.getText().length());
            int pad = u(14);
            FrameLayout box = new FrameLayout(this);
            box.setPaddingRelative(pad, pad / 2, pad, 0);
            box.addView(in);
            android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(I18n.resourceString(SetupActivity.this, R.string.text_your_name)).setView(box)
                    .setPositiveButton(I18n.resourceString(SetupActivity.this, R.string.text_ok), (d, w) -> {
                        String n = in.getText().toString().trim();
                        if (!n.isEmpty()) {
                            p.name = n;
                            Profiles.update(this, p);
                        }
                        show(index, 0);
                    }).setNegativeButton(I18n.resourceString(SetupActivity.this, R.string.text_cancel), null).show();
            Ui.setupDialogEditText(in, dlg);
            in.requestFocus();
        });
        int nameId = View.generateViewId();
        nameRow.setId(nameId);

        // Avatar: a row of emoji, the current one ringed.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        List<String> choices = new ArrayList<>(Profiles.freeEmojis(this, p.id));
        if (!choices.contains(p.emoji)) choices.add(0, p.emoji);
        int count = Math.min(8, choices.size());
        int[] emojiIds = new int[count];
        TextView[] emojiViews = new TextView[count];
        GradientDrawable[] emojiBgs = new GradientDrawable[count];
        int activeEmojiId = View.NO_ID;
        for (int i = 0; i < count; i++) {
            final int idx = i;
            String e = choices.get(i);
            TextView t = new TextView(this);
            int eid = View.generateViewId();
            t.setId(eid);
            emojiIds[i] = eid;
            emojiViews[i] = t;
            if (e.equals(p.emoji) || activeEmojiId == View.NO_ID) activeEmojiId = eid;
            t.setText(e);
            Ui.textSize(t, 26);
            t.setGravity(Gravity.CENTER);
            t.setFocusable(true);
            t.setClickable(true);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(e.equals(p.emoji) ? 0x59FFFFFF : 0x1FFFFFFF);
            emojiBgs[i] = g;
            t.setBackground(g);
            t.setOnFocusChangeListener((v, has) -> {
                g.setStroke(has ? u(2) : 0, Color.WHITE);
                v.animate().scaleX(has ? 1.2f : 1f).scaleY(has ? 1.2f : 1f).setDuration(160).start();
            });
            t.setOnClickListener(v -> {
                p.emoji = e;
                Profiles.update(this, p);
                for (int j = 0; j < count; j++) {
                    boolean sel = choices.get(j).equals(p.emoji);
                    emojiBgs[j].setColor(sel ? 0x59FFFFFF : 0x1FFFFFFF);
                }
                nameRow.setNextFocusDownId(emojiIds[idx]);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(46), u(46));
            lp.leftMargin = lp.rightMargin = u(5);
            row.addView(t, lp);
        }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = u(6);
        rlp.bottomMargin = u(14);
        page.addView(row, rlp);
        LinearLayout l2 = list();
        View contRow = option(l2, I18n.resourceString(SetupActivity.this, R.string.text_continue), null, false, this::next);
        int contId = View.generateViewId();
        contRow.setId(contId);

        if (activeEmojiId != View.NO_ID) {
            nameRow.setNextFocusDownId(activeEmojiId);
            contRow.setNextFocusUpId(activeEmojiId);
            for (int i = 0; i < count; i++) {
                emojiViews[i].setNextFocusUpId(nameId);
                emojiViews[i].setNextFocusDownId(contId);
                if (i > 0) emojiViews[i].setNextFocusLeftId(emojiIds[i - 1]);
                if (i + 1 < count) emojiViews[i].setNextFocusRightId(emojiIds[i + 1]);
            }
        }
        nameRow.requestFocus();
        dots();
    }

    /** Step 1: Choose Appearance (Night scenes vs Classic). Step 2: Choose Scenes or Classic Background. */
    void look() {
        boolean scenesMode = Scenes.isScenesLook(this);
        String curWall = Profiles.homePrefs(this).getString("wallpaper", "photo:tessellated-pavement.jpg");
        if (lookSubStep == 0) {
            title(I18n.resourceString(SetupActivity.this, R.string.text_appearance));
            TextView s = subtitle(I18n.resourceString(SetupActivity.this, R.string.text_choose_how_you_want_your_home_screen_to));
            ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);
            setBackdrop(scenesMode ? "scenes" : curWall);
            LinearLayout l = list();
            View scenesRow = option(l, I18n.resourceString(SetupActivity.this, R.string.text_night_scenes_6b95bc),
                    I18n.resourceString(SetupActivity.this, R.string.text_rotating_nature_scenes_with_a_glass_cloc),
                    scenesMode, () -> {
                        Scenes.setLook(this, Scenes.LOOK_SCENES);
                        lookSubStep = 1;
                        show(index, 1);
                    });
            View.OnFocusChangeListener scenesBase = scenesRow.getOnFocusChangeListener();
            scenesRow.setOnFocusChangeListener((v, has) -> {
                scenesBase.onFocusChange(v, has);
                if (has) setBackdrop("scenes");
            });

            View classicRow = option(l, I18n.resourceString(SetupActivity.this, R.string.text_classic),
                    I18n.resourceString(SetupActivity.this, R.string.text_top_shelf_6_column_app_grid_and_custom_w),
                    !scenesMode, () -> {
                        Scenes.setLook(this, Scenes.LOOK_CLASSIC);
                        lookSubStep = 2;
                        show(index, 1);
                    });
            View.OnFocusChangeListener classicBase = classicRow.getOnFocusChangeListener();
            classicRow.setOnFocusChangeListener((v, has) -> {
                classicBase.onFocusChange(v, has);
                if (has) setBackdrop(curWall);
            });
            if (scenesMode) scenesRow.requestFocus();
            else classicRow.requestFocus();
            dots();
            return;
        }

        if (lookSubStep == 1) {
            title(I18n.resourceString(SetupActivity.this, R.string.text_choose_your_scenes));
            TextView s = subtitle(I18n.resourceString(SetupActivity.this, R.string.text_choose_which_scenes_take_turns_behind_th));
            ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);
            setBackdrop("scene_asset:" + Scenes.ALL[0].bgAsset);
            LinearLayout l = scrollableList();
            for (int i = 0; i < Scenes.ALL.length; i++) {
                final Scenes.Spec spec = Scenes.ALL[i];
                boolean on = Scenes.isSceneEnabled(this, spec.id);
                final View[] rowRef = new View[1];
                View r = option(l, spec.title(this),
                        on ? I18n.resourceString(SetupActivity.this, R.string.text_included_in_rotation) : I18n.resourceString(SetupActivity.this, R.string.status_off),
                        on, () -> {
                            if (Scenes.toggleScene(this, spec.id)) {
                                boolean nowOn = Scenes.isSceneEnabled(this, spec.id);
                                if (rowRef[0] instanceof ViewGroup) {
                                    ViewGroup vg = (ViewGroup) rowRef[0];
                                    if (vg.getChildCount() >= 2) {
                                        View texts = vg.getChildAt(0);
                                        if (texts instanceof ViewGroup && ((ViewGroup) texts).getChildCount() >= 2) {
                                            TextView d = (TextView) ((ViewGroup) texts).getChildAt(1);
                                            d.setText(nowOn ? I18n.resourceString(SetupActivity.this, R.string.text_included_in_rotation) : I18n.resourceString(SetupActivity.this, R.string.status_off));
                                        }
                                        View tick = vg.getChildAt(1);
                                        if (tick instanceof TextView) {
                                            ((TextView) tick).setText(nowOn ? "\u2713" : "");
                                        }
                                    }
                                }
                            }
                        });
                rowRef[0] = r;
                View.OnFocusChangeListener base = r.getOnFocusChangeListener();
                r.setOnFocusChangeListener((v, has) -> {
                    base.onFocusChange(v, has);
                    if (has) setBackdrop("scene_asset:" + spec.bgAsset);
                });
                if (i == 0) r.requestFocus();
            }
            option(l, I18n.resourceString(SetupActivity.this, R.string.text_continue), null, false, () -> {
                lookSubStep = 0;
                next();
            });
            dots();
            return;
        }

        // lookSubStep == 2: Classic Backgrounds
        title(I18n.resourceString(SetupActivity.this, R.string.text_choose_a_background));
        TextView s = subtitle(I18n.resourceString(SetupActivity.this, R.string.text_choose_a_photo_or_ambient_colour_theme_b));
        ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);
        setBackdrop(curWall);
        LinearLayout l = scrollableList();
        for (int i = 0; i < MainActivity.WALL_KEYS.length; i++) {
            String key = MainActivity.WALL_KEYS[i];
            String name = MainActivity.wallName(this, i);
            boolean checked = key.equals(curWall);
            View r = option(l, name,
                    key.startsWith("photo:") ? I18n.resourceString(SetupActivity.this, R.string.text_classic_photo) : I18n.resourceString(SetupActivity.this, R.string.text_classic_look),
                    checked, () -> {
                        Scenes.setLook(this, Scenes.LOOK_CLASSIC);
                        Profiles.homePrefs(this).edit().putString("wallpaper", key).apply();
                        lookSubStep = 0;
                        next();
                    });
            View.OnFocusChangeListener base = r.getOnFocusChangeListener();
            r.setOnFocusChangeListener((v, has) -> {
                base.onFocusChange(v, has);
                if (has) setBackdrop(key);
            });
            if (checked) r.requestFocus();
        }
        dots();
    }

    /** This profile's Home Screen: recommendations and/or news on the top shelf, as two switches. */
    void homeContent() {
        backdrop.animate().alpha(0.5f).setDuration(300).start();
        title(I18n.resourceString(SetupActivity.this, R.string.text_your_home_screen));
        subtitle(I18n.resourceString(SetupActivity.this, R.string.text_what_appears_at_the_top_of_home_behind_y));
        boolean recs = Suggestions.showRecs(this), news = Suggestions.showNews(this);
        LinearLayout l = list();
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_recommendations),
                I18n.resourceString(SetupActivity.this, R.string.text_popular_movies_and_series_on_your_stream), recs, () -> {
            Suggestions.setShelf(this, !recs, news);
            show(index, 0);
            focusRow(0);
        });
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_news),
                I18n.resourceString(SetupActivity.this, R.string.text_headlines_from_your_city_and_the_world), news, () -> {
            Suggestions.setShelf(this, recs, !news);
            show(index, 0);
            focusRow(1);
        });
        boolean cw = Profiles.homePrefs(this).getBoolean("cw_enabled", true);
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_continue_watching),
                I18n.resourceString(SetupActivity.this, R.string.text_what_you_were_watching_mixed_into_the_ca), cw, () -> {
            Profiles.homePrefs(this).edit().putBoolean("cw_enabled", !cw).apply();
            show(index, 0);
            focusRow(2);
        });
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_continue), recs || news || cw ? null : I18n.resourceString(SetupActivity.this, R.string.text_just_your_background_for_now), false, this::next);
        dots();
    }

    /** After re-building a page, put focus back on the row that was pressed. */
    void focusRow(int i) {
        View v = page.findViewWithTag("list");
        if (v instanceof LinearLayout && i < ((LinearLayout) v).getChildCount()) ((LinearLayout) v).getChildAt(i).requestFocus();
    }

    void weather() {
        title(I18n.resourceString(SetupActivity.this, R.string.text_weather));
        subtitle(I18n.resourceString(SetupActivity.this, R.string.text_a_small_weather_display_on_home_with_a_5));
        LinearLayout l = list();
        String city = Weather.place(this);
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_use_my_location),
                city.isEmpty() ? I18n.resourceString(SetupActivity.this, R.string.text_found_from_your_internet_connection) : I18n.resourceString(SetupActivity.this, R.string.text_looks_like) + city,
                Weather.enabled(this) && !Weather.manual(this), () -> {
                    Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, true).apply();
                    HomeDashboard.setPrayerLocationAuto(this);
                    Weather.setPlace(this, null);
                    next();
                });
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_choose_a_city),
                Weather.manual(this) ? Weather.place(this) : I18n.resourceString(SetupActivity.this, R.string.text_search_by_name), Weather.manual(this), this::searchCityDialog);
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_next_9f81b4), null, false, this::next);
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_don_t_show_weather), null, !Weather.enabled(this), () -> {
            Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, false).apply();
            Weather.changed(this);
            next();
        });
        if (city.isEmpty()) Weather.refresh(this, true, () -> {
            if (index == 3) show(3, 0);  // show the detected city
        });
        dots();
    }

    private void searchCityDialog() {
        EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setHint(I18n.resourceString(SetupActivity.this, R.string.text_city_name_e_g_istanbul));
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPaddingRelative(pad, pad / 2, pad, 0);
        box.addView(in);
        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(I18n.resourceString(SetupActivity.this, R.string.text_choose_city))
                .setView(box)
                .setPositiveButton(I18n.resourceString(SetupActivity.this, R.string.text_search_946f53), (d, w) -> {
                    String q = in.getText().toString().trim();
                    if (q.isEmpty()) return;
                    new Thread(() -> {
                        List<Weather.Place> found;
                        try {
                            found = Weather.search(q);
                        } catch (Exception e) {
                            found = null;
                        }
                        List<Weather.Place> res = found;
                        runOnUiThread(() -> showCityMatches(q, res));
                    }).start();
                })
                .setNegativeButton(I18n.resourceString(SetupActivity.this, R.string.text_cancel), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    private void showCityMatches(String q, List<Weather.Place> res) {
        if (isFinishing()) return;
        if (res == null || res.isEmpty()) {
            android.widget.Toast.makeText(this,
                    res == null
                            ? I18n.resourceString(SetupActivity.this, R.string.text_couldn_t_search_check_the_internet_conne)
                            : I18n.resourceString(this, R.string.message_no_place_found, q),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[res.size()];
        for (int i = 0; i < names.length; i++) {
            Weather.Place pl = res.get(i);
            names[i] = pl.detail.isEmpty() ? pl.name : pl.name + "  \u00b7  " + pl.detail;
        }
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(I18n.resourceString(SetupActivity.this, R.string.text_matching_places))
                .setItems(names, (d, w) -> {
                    Weather.Place picked = res.get(w);
                    Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, true).apply();
                    HomeDashboard.setPrayerLocationAuto(this);
                    Weather.setPlace(this, picked);
                    Weather.refresh(this, true, null);
                    show(index, 0);
                    focusRow(2);
                })
                .setNegativeButton(I18n.resourceString(SetupActivity.this, R.string.text_cancel), null)
                .show();
    }

    /** Presentation is for the whole TV: switch it on and choose its options here, once. */
    void presentation() {
        ImageView icon = new ImageView(this);
        icon.setImageBitmap(com.techgenius.tvoslauncher.present.PresentIcon.banner(u(90) * 2, u(54) * 2));
        icon.setClipToOutline(true);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(u(9));
        icon.setBackground(shape);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(u(90), u(54));
        ilp.bottomMargin = u(10);
        page.addView(icon, ilp);
        title(I18n.resourceString(SetupActivity.this, R.string.text_presentation));
        subtitle(I18n.resourceString(SetupActivity.this, R.string.text_images_videos_and_webpages_sent_from_any));
        boolean on = com.techgenius.tvoslauncher.present.Presenter.enabled(this);
        LinearLayout l = list();
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_use_presentation),
                on ? I18n.resourceString(SetupActivity.this, R.string.text_adds_a_presentation_card_to_home) : I18n.resourceString(SetupActivity.this, R.string.status_off), on, () -> {
            com.techgenius.tvoslauncher.present.Presenter.set(this, com.techgenius.tvoslauncher.present.Presenter.KEY_ENABLED, !on);
            show(index, 0);
            focusRow(0);
        });
        if (on) {
            boolean boot = com.techgenius.tvoslauncher.present.Presenter.startOnBoot(this),
                    awake = com.techgenius.tvoslauncher.present.Presenter.keepAwake(this);
            option(l, I18n.resourceString(SetupActivity.this, R.string.text_start_with_the_tv),
                    I18n.resourceString(SetupActivity.this, R.string.text_play_as_soon_as_the_tv_turns_on_signage), boot, () -> {
                com.techgenius.tvoslauncher.present.Presenter.set(this, com.techgenius.tvoslauncher.present.Presenter.KEY_BOOT, !boot);
                show(index, 0);
                focusRow(1);
            });
            option(l, I18n.resourceString(SetupActivity.this, R.string.text_keep_screen_awake),
                    I18n.resourceString(SetupActivity.this, R.string.text_no_screensaver_or_sleep_while_presenting), awake, () -> {
                com.techgenius.tvoslauncher.present.Presenter.set(this, com.techgenius.tvoslauncher.present.Presenter.KEY_AWAKE, !awake);
                show(index, 0);
                focusRow(2);
            });
        }
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_continue), null, false, this::next);
        dots();
    }

    boolean askedLocation;
    int permMode = 0; // 0 = Choose Usage Mode (3 modes), 1 = Easy ADB Directions, 2 = Manual Setup

    void openDefaultHomeSubMenu() {
        show(6, 1);
    }

    void homeAndPermissions() {
        if (permMode == 0) {
            title(I18n.resourceString(SetupActivity.this, R.string.text_finish_setting_up));
            TextView s = subtitle(I18n.resourceString(SetupActivity.this, R.string.text_select_a_usage_mode_for_permissions_butt));
            ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(14);
            LinearLayout l = scrollableList();
            option(l, I18n.resourceString(SetupActivity.this, R.string.text_mode_secure_auto_setup_close_adb_recomme),
                    I18n.resourceString(SetupActivity.this, R.string.text_grants_write_secure_settings_once_to_sel),
                    false,
                    () -> {
                        LocalAdb.setAdbMode(this, LocalAdb.ADB_MODE_SECURE_CLOSE);
                        proceedToEasyAdbOrDirections();
                    });
            option(l, I18n.resourceString(SetupActivity.this, R.string.text_mode_persistent_adb_setup_keep_adb_open),
                    I18n.resourceString(SetupActivity.this, R.string.text_keeps_network_adb_enabled_for_cleanyourt),
                    false,
                    () -> {
                        LocalAdb.setAdbMode(this, LocalAdb.ADB_MODE_PERSISTENT);
                        proceedToEasyAdbOrDirections();
                    });
            option(l, I18n.resourceString(SetupActivity.this, R.string.text_mode_manual_setup_without_adb),
                    I18n.resourceString(SetupActivity.this, R.string.text_grant_permissions_one_by_one_manually_th),
                    false,
                    () -> {
                        permMode = 2;
                        show(5, 1);
                    });
            option(l, I18n.resourceString(SetupActivity.this, R.string.text_continue), null, false, this::next);
            dots();
            return;
        }

        if (permMode == 1) {
            boolean secureClose = LocalAdb.getAdbMode(this) == LocalAdb.ADB_MODE_SECURE_CLOSE;
            title(I18n.resourceString(SetupActivity.this, R.string.text_enable_adb_debugging));
            TextView s = subtitle(I18n.resourceString(this,
                    secureClose ? R.string.setup_adb_close_steps : R.string.setup_adb_keep_steps));
            ((LinearLayout.LayoutParams) s.getLayoutParams()).width = u(620);
            ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(14);
            LinearLayout l = scrollableList();
            option(l, I18n.resourceString(SetupActivity.this, R.string.text_open_developer_options_tv_settings),
                    I18n.resourceString(SetupActivity.this, R.string.text_enable_usb_network_debugging_in_develope),
                    LocalAdb.isAdbSettingEnabled(this),
                    () -> {
                        Intent dev = new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                        if (!Suggestions.start(this, dev)) {
                            Intent about = new Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS);
                            if (!Suggestions.start(this, about)) {
                                Suggestions.start(this, new Intent(android.provider.Settings.ACTION_SETTINGS));
                            }
                        }
                    });
            option(l, secureClose
                            ? I18n.resourceString(SetupActivity.this, R.string.text_grant_permissions_close_adb_now)
                            : I18n.resourceString(SetupActivity.this, R.string.text_grant_permissions_now_keep_adb_open),
                    secureClose
                            ? I18n.resourceString(SetupActivity.this, R.string.text_grants_write_secure_settings_closes_port)
                            : I18n.resourceString(SetupActivity.this, R.string.text_uses_local_adb_to_grant_permissions_and),
                    false,
                    this::runEasyAdbGrant);
            option(l, I18n.resourceString(SetupActivity.this, R.string.text_back_to_usage_mode_selection),
                    I18n.resourceString(SetupActivity.this, R.string.text_switch_between_secure_auto_setup_persist),
                    false,
                    () -> {
                        permMode = 0;
                        show(5, -1);
                    });
            dots();
            return;
        }

        // permMode == 2: Manual Setup
        if (!Perms.hasLocation(this) && !askedLocation) {
            askedLocation = true;
            handler.postDelayed(() -> Perms.requestLocation(this), 600);
        }
        title(I18n.resourceString(SetupActivity.this, R.string.text_finish_setting_up));
        TextView s = subtitle(I18n.resourceString(SetupActivity.this, R.string.text_tvos_launcher_works_best_as_the_home_scr));
        ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);
        LinearLayout l = scrollableList();
        boolean home = Perms.isDefaultHome(this);
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_make_tvos_launcher_the_home_screen),
                home ? I18n.resourceString(SetupActivity.this, R.string.text_the_home_button_opens_tvos_launcher) : I18n.resourceString(SetupActivity.this, R.string.text_choose_tvos_launcher_when_asked), home,
                () -> Perms.requestDefaultHome(this));
        if (!Perms.hasLocation(this)) {
            option(l, I18n.resourceString(SetupActivity.this, R.string.text_show_the_wi_fi_name),
                    I18n.resourceString(SetupActivity.this, R.string.text_in_control_center_needs_location_access), false,
                    () -> Perms.requestLocation(this));
        }
        if (Perms.listenerSupported(this) && !Perms.hasListener(this)) {
            option(l, I18n.resourceString(SetupActivity.this, R.string.text_continue_watching),
                    I18n.resourceString(SetupActivity.this, R.string.text_remember_what_you_play_in_each_app), false, () -> Perms.requestListener(this));
        }
        boolean accOn = ButtonMapper.isServiceEnabled(this);
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_button_mapper_control_panel),
                accOn
                        ? I18n.resourceString(SetupActivity.this, R.string.text_active_in_accessibility)
                        : I18n.resourceString(SetupActivity.this, R.string.text_enable_in_accessibility_so_remapped_butt),
                accOn,
                () -> {
                    Intent acc = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    if (!Suggestions.start(this, acc)) {
                        Suggestions.start(this, new Intent(android.provider.Settings.ACTION_SETTINGS));
                    }
                });
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_back_to_usage_mode_selection), null, false, () -> {
            permMode = 0;
            show(5, -1);
        });
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_continue), null, false, this::next);
        dots();
    }

    /** Dedicated page right after permissions: Make tvOS Launcher Default Home (with sequential troubleshooting steps). */
    void defaultHomeSetup() {
        title(I18n.resourceString(SetupActivity.this, R.string.text_make_tvos_launcher_default_home));
        TextView s = subtitle(I18n.resourceString(SetupActivity.this, R.string.text_on_tcl_philips_and_google_tvs_oem_firmwa));
        ((LinearLayout.LayoutParams) s.getLayoutParams()).width = u(620);
        ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(14);
        LinearLayout l = scrollableList();

        boolean isDef = Perms.isDefaultHome(this);
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_step_1_set_tvos_launcher_as_default_via),
                isDef ? I18n.resourceString(SetupActivity.this, R.string.text_active_set_as_default_home_launcher)
                      : I18n.resourceString(SetupActivity.this, R.string.text_run_role_home_set_home_activity_over_loc),
                isDef,
                () -> runAdbTroubleshootStep(this::executeStep1SetDefaultLauncherViaAdb));

        option(l, I18n.resourceString(SetupActivity.this, R.string.text_step_2_disable_system_launchers_google_t),
                CleanYourTv.getSystemLaunchersSummary(this),
                !Perms.hasEnabledStockLauncher(this),
                () -> runAdbTroubleshootStep(() -> CleanYourTv.showDisableSystemLaunchersDialog(this, () -> show(index, 0))));

        boolean homeFixOn = ButtonMapper.isHomeFixEnabled(this);
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_step_3_fix_home_button_via_accessibility),
                homeFixOn
                        ? I18n.resourceString(SetupActivity.this, R.string.text_on_pressing_home_launches_tvos_launcher)
                        : I18n.resourceString(SetupActivity.this, R.string.text_off_tap_to_enable_home_button_remap),
                homeFixOn,
                () -> {
                    boolean next = !ButtonMapper.isHomeFixEnabled(this);
                    ButtonMapper.setHomeFixEnabled(this, next);
                    if (next && !ButtonMapper.isServiceEnabled(this)) {
                        if (!LocalAdb.hasWriteSecureSettings(this) || !LocalAdb.repairPermissionsViaSecureSettings(this)) {
                            Intent acc = Perms.accessibilityScreen(this);
                            if (acc != null) {
                                Suggestions.start(this, acc);
                            }
                        }
                    }
                    show(index, 0);
                });

        option(l, I18n.resourceString(SetupActivity.this, R.string.text_step_4_open_android_system_home_settings),
                I18n.resourceString(SetupActivity.this, R.string.text_open_system_selector_if_supported_by_tv),
                false,
                () -> Perms.openSystemHomeSettingsUi(this));

        option(l, I18n.resourceString(SetupActivity.this, R.string.text_continue),
                I18n.resourceString(SetupActivity.this, R.string.text_proceed_to_the_next_setup_step_when_you),
                false,
                this::next);
        dots();
    }

    private void executeStep1SetDefaultLauncherViaAdb() {
        android.app.AlertDialog progress = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(I18n.resourceString(SetupActivity.this, R.string.text_setting_default_launcher))
                .setMessage(I18n.resourceString(SetupActivity.this, R.string.text_setting_role_home_and_default_home_activ))
                .setCancelable(false)
                .create();
        progress.show();
        LocalAdb.setDefaultLauncherViaAdb(this, (success, msg) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            try {
                progress.dismiss();
            } catch (Throwable ignored) {
            }
            // Refresh current page without skipping so the user can proceed to the next step on this page
            show(index, 0);
            android.widget.Toast.makeText(this,
                    success
                            ? I18n.resourceString(SetupActivity.this, R.string.text_step_1_complete_default_home_launcher_se)
                            : I18n.resourceString(SetupActivity.this, R.string.text_could_not_connect_to_adb_enable_usb_netw),
                    android.widget.Toast.LENGTH_LONG).show();
        }));
    }

    /**
     * If the user selected Secure ADB Mode on the previous page (or ADB is currently off),
     * prompts them that ADB mode is required for this troubleshooting step and lets them
     * either Enable ADB Now or Skip This Step.
     */
    private void runAdbTroubleshootStep(Runnable stepAction) {
        boolean secureModeSelected = LocalAdb.getAdbMode(this) == LocalAdb.ADB_MODE_SECURE_CLOSE;
        boolean adbOff = !LocalAdb.isAdbSettingEnabled(this) && !LocalAdb.portReachable;
        if (secureModeSelected || adbOff) {
            new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(I18n.resourceString(SetupActivity.this, R.string.text_adb_mode_required))
                    .setMessage(I18n.resourceString(SetupActivity.this, R.string.text_you_need_the_adb_mode_enabled_for_this_t))
                    .setPositiveButton(I18n.resourceString(SetupActivity.this, R.string.text_enable_adb_now), (d, w) -> {
                        LocalAdb.setAdbMode(this, LocalAdb.ADB_MODE_PERSISTENT);
                        android.app.AlertDialog progress = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle(I18n.resourceString(SetupActivity.this, R.string.text_enabling_adb))
                                .setMessage(I18n.resourceString(SetupActivity.this, R.string.text_turning_on_adb_debugging_for_this_troubl))
                                .setCancelable(false)
                                .create();
                        progress.show();
                        LocalAdb.enableAdbPort(this, (success, msg) -> runOnUiThread(() -> {
                            if (isFinishing()) return;
                            try {
                                progress.dismiss();
                            } catch (Throwable ignored) {
                            }
                            show(index, 0);
                            if (success || LocalAdb.isAdbSettingEnabled(this) || LocalAdb.portReachable) {
                                stepAction.run();
                            } else {
                                new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                        .setTitle(I18n.resourceString(SetupActivity.this, R.string.text_turn_on_adb_debugging))
                                        .setMessage(I18n.resourceString(SetupActivity.this, R.string.text_please_enable_usb_debugging_network_debu))
                                        .setPositiveButton(I18n.resourceString(SetupActivity.this, R.string.text_open_developer_options), (d2, w2) -> {
                                            Intent dev = new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                                            if (!Suggestions.start(this, dev)) {
                                                Intent about = new Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS);
                                                if (!Suggestions.start(this, about)) {
                                                    Suggestions.start(this, new Intent(android.provider.Settings.ACTION_SETTINGS));
                                                }
                                            }
                                        })
                                        .setNegativeButton(I18n.resourceString(SetupActivity.this, R.string.text_skip_this_step), null)
                                        .show();
                            }
                        }));
                    })
                    .setNegativeButton(I18n.resourceString(SetupActivity.this, R.string.text_skip_this_step), null)
                    .show();
            return;
        }
        stepAction.run();
    }

    private void proceedToEasyAdbOrDirections() {
        if (!LocalAdb.isAdbSettingEnabled(this)) {
            permMode = 1;
            show(5, 1);
        } else {
            runEasyAdbGrant();
        }
    }

    private void runEasyAdbGrant() {
        final boolean closeAfter = LocalAdb.getAdbMode(this) == LocalAdb.ADB_MODE_SECURE_CLOSE;
        android.app.AlertDialog progress = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(closeAfter
                        ? I18n.resourceString(SetupActivity.this, R.string.text_secure_setup_adb)
                        : I18n.resourceString(SetupActivity.this, R.string.text_persistent_adb_setup))
                .setMessage(I18n.resourceString(SetupActivity.this, R.string.text_granting_permissions_via_local_adb_if_an))
                .setCancelable(false)
                .create();
        progress.show();

        LocalAdb.grantAllPermissions(this, closeAfter, (success, msg) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            try {
                progress.dismiss();
            } catch (Exception ignored) {
            }
            if (success) {
                if (closeAfter) {
                    new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle(I18n.resourceString(SetupActivity.this, R.string.text_permissions_granted_adb_closed))
                            .setMessage(I18n.resourceString(SetupActivity.this, R.string.message_write_secure_settings_and_all_launcher_permissio))
                            .setPositiveButton(I18n.resourceString(SetupActivity.this, R.string.text_continue), (d, w) -> next())
                            .setOnCancelListener(d -> next())
                            .show();
                } else {
                    android.widget.Toast.makeText(this,
                            I18n.resourceString(SetupActivity.this, R.string.text_all_permissions_granted_automatically),
                            android.widget.Toast.LENGTH_SHORT).show();
                    next();
                }
            } else {
                permMode = 1;
                show(5, 0);
                new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(I18n.resourceString(SetupActivity.this, R.string.text_adb_debugging_needed))
                        .setMessage(I18n.resourceString(SetupActivity.this, R.string.text_could_not_connect_to_local_adb_on_port_5_1f7e75))
                        .setPositiveButton(I18n.resourceString(SetupActivity.this, R.string.text_ok), null)
                        .setNeutralButton(I18n.resourceString(SetupActivity.this, R.string.text_manual_setup), (d, w) -> {
                            permMode = 2;
                            show(5, 1);
                        })
                        .show();
            }
        }));
    }

    /** Step right after permissions: let the user add remote buttons and assign 1x, 2x, 3x tap actions. */
    void buttonMapperSetup() {
        title(I18n.resourceString(SetupActivity.this, R.string.text_button_mapper_7fa6e3));
        TextView s = subtitle(I18n.resourceString(SetupActivity.this, R.string.text_map_any_button_on_your_remote_with_singl));
        ((LinearLayout.LayoutParams) s.getLayoutParams()).width = u(580);
        ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);

        LinearLayout l = scrollableList();
        if (!ButtonMapper.isServiceEnabled(this)) {
            option(l, I18n.resourceString(SetupActivity.this, R.string.text_enable_button_mapper_in_accessibility),
                    I18n.resourceString(SetupActivity.this, R.string.text_required_so_mapped_buttons_work_over_eve),
                    false,
                    () -> {
                        Intent acc = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        if (!Suggestions.start(this, acc)) {
                            Suggestions.start(this, new Intent(android.provider.Settings.ACTION_SETTINGS));
                        }
                    });
        }

        option(l, I18n.resourceString(SetupActivity.this, R.string.text_add_new_button_to_remap_7d8e68),
                I18n.resourceString(SetupActivity.this, R.string.text_assign_single_tap_1x_double_tap_2x_tripl),
                false,
                () -> ButtonMapper.showCaptureAndConfigureDialog(this, () -> show(index, 0)));

        List<Integer> mappedKeys = ButtonMapper.getMappedKeys(this);
        for (final int keyCode : mappedKeys) {
            option(l, ButtonMapper.keyLabel(this, keyCode),
                    ButtonMapper.keySummaryLabel(this, keyCode),
                    true,
                    () -> ButtonMapper.showConfigureKeyDialog(this, keyCode, () -> show(index, 0)));
        }

        option(l, I18n.resourceString(SetupActivity.this, R.string.text_continue),
                mappedKeys.isEmpty() ? I18n.resourceString(SetupActivity.this, R.string.text_you_can_also_configure_buttons_later_in) : null,
                false,
                this::next);
        dots();
    }

    void finish_() {
        backdrop.animate().alpha(0f).setDuration(300).start();
        View top = new View(this);
        page.addView(top, new LinearLayout.LayoutParams(1, 0, 1f));
        int cs = u(64);
        Bitmap cb = Bitmap.createBitmap(cs, cs, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cc = new android.graphics.Canvas(cb);
        android.graphics.Paint cp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        cp.setColor(0xFF30D158);
        cc.drawCircle(cs / 2f, cs / 2f, cs / 2f, cp);
        cp.setColor(Color.WHITE);
        cp.setStyle(android.graphics.Paint.Style.STROKE);
        cp.setStrokeWidth(cs * 0.09f);
        cp.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        cp.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        android.graphics.Path tick = new android.graphics.Path();
        tick.moveTo(cs * 0.29f, cs * 0.52f);
        tick.lineTo(cs * 0.44f, cs * 0.67f);
        tick.lineTo(cs * 0.72f, cs * 0.37f);
        cc.drawPath(tick, cp);
        ImageView check = new ImageView(this);
        check.setImageBitmap(cb);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(u(64), u(64));
        clp.bottomMargin = u(18);
        page.addView(check, clp);
        title(I18n.resourceString(SetupActivity.this, R.string.text_you_re_all_set));
        subtitle(I18n.resourceString(SetupActivity.this, R.string.text_change_any_of_this_later_in_settings_fro));
        LinearLayout l = list();
        option(l, I18n.resourceString(SetupActivity.this, R.string.text_go_to_home_screen), null, false, () -> {
            getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean(K_DONE, true).putInt("cur_step", 0).apply();
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        View bottom = new View(this);
        page.addView(bottom, new LinearLayout.LayoutParams(1, 0, 1.4f));
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent ev) {
        MainActivity.navSound(this, ev);
        return super.dispatchKeyEvent(ev);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
