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
        page.setPadding(0, u(36), 0, u(22));
        root.addView(page, plp);

        // Top-right "Skip Setup" pill button on the first setup screen (language selection)
        skipBtn = new TextView(this);
        skipBtn.setId(View.generateViewId());
        skipBtn.setText(tr("Skip Setup", "Kurulumu Atla") + "  \u203a");
        skipBtn.setTextColor(Color.WHITE);
        skipBtn.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(skipBtn, 14);
        skipBtn.setGravity(Gravity.CENTER);
        skipBtn.setFocusable(true);
        skipBtn.setClickable(true);
        skipBtn.setPadding(u(18), u(9), u(18), u(9));
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
        skipLp.rightMargin = u(36);
        root.addView(skipBtn, skipLp);

        setContentView(root);

        steps.add(this::hello);
        steps.add(this::profile);
        steps.add(this::look);
        steps.add(this::weather);
        steps.add(this::presentation);
        steps.add(this::homeAndPermissions);
        steps.add(this::buttonMapperSetup);
        steps.add(this::finish_);
        if (getIntent().getBooleanExtra("skip", false)) {  // adb: --ez skip true marks setup as done
            getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean(K_DONE, true).putInt("cur_step", 0).apply();
            finish();
            return;
        }
        int savedStep = getSharedPreferences("setup", MODE_PRIVATE).getInt("cur_step", 0);
        show(getIntent().getIntExtra("step", savedStep), 0);  // adb: --ei step N opens a page directly
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int savedStep = getSharedPreferences("setup", MODE_PRIVATE).getInt("cur_step", index);
        int target = intent.getIntExtra("step", savedStep);
        if (target != index) {
            show(target, 0);
        } else if (index == 5 || index == 6) {
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
            skipBtn.setText(tr("Skip Setup", "Kurulumu Atla") + "  \u203a");
            skipBtn.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        }
        page.animate().cancel();
        page.removeAllViews();
        steps.get(index).build();
        // New page: slide and fade. Same page re-drawn after a switch: a quick fade only.
        page.setAlpha(dir == 0 ? 0.6f : 0f);
        page.setTranslationX(u(30) * dir);
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
        if (index == 5) show(5, 0);  // refresh the ticks
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (index == 5 || index == 6) show(index, 0);
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
        l.setPadding(u(22), u(6), u(22), u(6));
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
        l.setPadding(u(22), u(6), u(22), u(6));
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
        r.setPadding(u(18), u(8), u(16), u(8));
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
        tickLp.leftMargin = u(10);
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

    String tr(String en, String trStr) {
        return I18n.s(this, en, trStr);
    }

    /** Welcome, then Get Started. */
    void hello() {
        backdrop.animate().alpha(0f).setDuration(300).start();
        TextView hi = title(tr("Welcome", "Ho\u015f Geldiniz"));
        Ui.textSize(hi, 52);
        hi.setAlpha(0f);
        hi.setTranslationY(u(12));
        hi.animate().alpha(1f).translationY(0).setDuration(700).start();
        // "Welcome" in Turkish, English, Indian and world languages, fading from one to the next.
        String[] words = {"Welcome", "Ho\u015f Geldiniz", "\u0938\u094d\u0935\u093e\u0917\u0924 \u0939\u0948", "\u09b8\u09cd\u09ac\u09be\u0997\u09a4\u09ae", "\u0bb5\u0bb0\u0bb5\u0bc7\u0bb1\u0bcd\u0b95\u0bbf\u0bb1\u0bcb\u0bae\u0bcd", "\u0c38\u0c4d\u0c35\u0c3e\u0c17\u0c24\u0c02", "Bienvenue", "Bienvenido", "Willkommen", "\u3088\u3046\u3053\u305d", "Benvenuto", "Bem-vindo", "\u6b22\u8fce"};
        int[] k = {I18n.isTr(this) ? 1 : 0};
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
        TextView s = subtitle(tr("Let\u2019s set up tvOS Launcher.", "tvOS Launcher\u2019\u0131 kural\u0131m."));
        Ui.textSize(s, 16);
        ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);
        LinearLayout l = scrollableList();
        String curLang = I18n.getLang(this);
        View startRow = option(l, tr("Get Started", "Ba\u015fla"), null, false, () -> {
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
        for (int i = 0; i < I18n.LANGUAGES.length; i++) {
            final int rowIdx = i + 1;
            final String code = I18n.LANGUAGES[i][0];
            final String title = I18n.LANGUAGES[i][1];
            final String sub = I18n.LANGUAGES[i][2];
            View langRow = option(l, title, sub, code.equals(curLang), () -> {
                I18n.setLang(this, code);
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
        title(tr("Create Your Profile", "Profilinizi Olu\u015fturun"));
        subtitle(tr(
                "Your name and an avatar. Each profile keeps its own apps, background and Home Screen. Up to three people can have one.",
                "Ad\u0131n\u0131z ve bir avatar. Her profil kendi uygulamalar\u0131n\u0131, arka plan\u0131n\u0131 ve Ana Ekran\u0131n\u0131 saklar. En fazla \u00fc\u00e7 ki\u015fi kullanabilir."));
        Profiles.Profile p = Profiles.current(this);
        LinearLayout l = list();
        View nameRow = option(l, tr("Name", "Ad"), I18n.profileName(this, p.name), false, () -> {
            EditText in = new EditText(this);
            in.setSingleLine(true);
            in.setText(p.name);
            in.setSelection(in.getText().length());
            int pad = u(14);
            FrameLayout box = new FrameLayout(this);
            box.setPadding(pad, pad / 2, pad, 0);
            box.addView(in);
            android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(tr("Your name", "Ad\u0131n\u0131z")).setView(box)
                    .setPositiveButton(tr("OK", "Tamam"), (d, w) -> {
                        String n = in.getText().toString().trim();
                        if (!n.isEmpty()) {
                            p.name = n;
                            Profiles.update(this, p);
                        }
                        show(index, 0);
                    }).setNegativeButton(tr("Cancel", "\u0130ptal"), null).show();
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
        View contRow = option(l2, tr("Continue", "Devam Et"), null, false, this::next);
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
            title(tr("Appearance", "G\u00f6r\u00fcn\u00fcm"));
            TextView s = subtitle(tr("Choose how you want your Home Screen to look.",
                    "Ana Ekran\u0131n\u0131z\u0131n nas\u0131l g\u00f6r\u00fcnmesini istedi\u011finizi se\u00e7in."));
            ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);
            setBackdrop(scenesMode ? "scenes" : curWall);
            LinearLayout l = list();
            View scenesRow = option(l, tr("Night scenes", "Gece sahneleri"),
                    tr("Rotating nature scenes with a glass clock and bottom dock", "Cam saat ve alt dock ile d\u00f6n\u00fc\u015f\u00fcml\u00fc do\u011fa sahneleri"),
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

            View classicRow = option(l, tr("Classic", "Klasik"),
                    tr("Top shelf, 6-column app grid and custom wallpapers", "\u00dcst raf, 6 s\u00fctunlu uygulama \u0131zgaras\u0131 ve \u00f6zel duvar ka\u011f\u0131tlar\u0131"),
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
            title(tr("Choose Your Scenes", "Sahnelerinizi Se\u00e7in"));
            TextView s = subtitle(tr("Choose which scenes take turns behind the glass clock.",
                    "Cam saatin arkas\u0131nda hangi sahnelerin s\u0131rayla g\u00f6r\u00fcnece\u011fini se\u00e7in."));
            ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);
            setBackdrop("scene_asset:" + Scenes.ALL[0].bgAsset);
            LinearLayout l = scrollableList();
            for (int i = 0; i < Scenes.ALL.length; i++) {
                final Scenes.Spec spec = Scenes.ALL[i];
                boolean on = Scenes.isSceneEnabled(this, spec.id);
                final View[] rowRef = new View[1];
                View r = option(l, spec.title(this),
                        on ? tr("Included in rotation", "D\u00f6ng\u00fcye dahil") : tr("Off", "Kapal\u0131"),
                        on, () -> {
                            if (Scenes.toggleScene(this, spec.id)) {
                                boolean nowOn = Scenes.isSceneEnabled(this, spec.id);
                                if (rowRef[0] instanceof ViewGroup) {
                                    ViewGroup vg = (ViewGroup) rowRef[0];
                                    if (vg.getChildCount() >= 2) {
                                        View texts = vg.getChildAt(0);
                                        if (texts instanceof ViewGroup && ((ViewGroup) texts).getChildCount() >= 2) {
                                            TextView d = (TextView) ((ViewGroup) texts).getChildAt(1);
                                            d.setText(nowOn ? tr("Included in rotation", "D\u00f6ng\u00fcye dahil") : tr("Off", "Kapal\u0131"));
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
            option(l, tr("Continue", "Devam Et"), null, false, () -> {
                lookSubStep = 0;
                next();
            });
            dots();
            return;
        }

        // lookSubStep == 2: Classic Backgrounds
        title(tr("Choose a Background", "Bir Arka Plan Se\u00e7in"));
        TextView s = subtitle(tr("Choose a photo or ambient colour theme behind your apps.",
                "Uygulamalar\u0131n\u0131z\u0131n arkas\u0131nda bir foto\u011fraf veya ortam renk temas\u0131 se\u00e7in."));
        ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);
        setBackdrop(curWall);
        LinearLayout l = scrollableList();
        for (int i = 0; i < MainActivity.WALL_KEYS.length; i++) {
            String key = MainActivity.WALL_KEYS[i];
            String name = MainActivity.wallName(this, i);
            boolean checked = key.equals(curWall);
            View r = option(l, name,
                    key.startsWith("photo:") ? tr("Classic photo", "Klasik foto\u011fraf") : tr("Classic look", "Klasik g\u00f6r\u00fcn\u00fcm"),
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
        title(tr("Your Home Screen", "Ana Ekran\u0131n\u0131z"));
        subtitle(tr("What appears at the top of Home, behind your apps. Choose any, all or none.",
                "Uygulamalar\u0131n\u0131z\u0131n arkas\u0131nda, Ana Ekran\u0131n \u00fcst k\u0131sm\u0131nda ne g\u00f6r\u00fcnece\u011fini se\u00e7in."));
        boolean recs = Suggestions.showRecs(this), news = Suggestions.showNews(this);
        LinearLayout l = list();
        option(l, tr("Recommendations", "\u00d6neriler"),
                tr("Popular movies and series on your streaming services", "Yay\u0131n servislerinizdeki pop\u00fcler filmler ve diziler"), recs, () -> {
            Suggestions.setShelf(this, !recs, news);
            show(index, 0);
            focusRow(0);
        });
        option(l, tr("News", "Haberler"),
                tr("Headlines from your city and the world", "\u015eehrinizden ve d\u00fcnyadan man\u015fetler"), news, () -> {
            Suggestions.setShelf(this, recs, !news);
            show(index, 0);
            focusRow(1);
        });
        boolean cw = Profiles.homePrefs(this).getBoolean("cw_enabled", true);
        option(l, tr("Continue Watching", "\u0130zlemeye Devam Et"),
                tr("What you were watching, mixed into the carousel", "\u0130zledi\u011finiz i\u00e7erikler ana ekranda g\u00f6sterilir"), cw, () -> {
            Profiles.homePrefs(this).edit().putBoolean("cw_enabled", !cw).apply();
            show(index, 0);
            focusRow(2);
        });
        option(l, tr("Continue", "Devam Et"), recs || news || cw ? null : tr("Just your background for now", "\u015eimdilik yaln\u0131zca arka plan\u0131n\u0131z"), false, this::next);
        dots();
    }

    /** After re-building a page, put focus back on the row that was pressed. */
    void focusRow(int i) {
        View v = page.findViewWithTag("list");
        if (v instanceof LinearLayout && i < ((LinearLayout) v).getChildCount()) ((LinearLayout) v).getChildAt(i).requestFocus();
    }

    void weather() {
        title(tr("Weather", "Hava Durumu"));
        subtitle(tr("A small weather display on Home, with a 5-day forecast.",
                "Ana Ekranda 5 g\u00fcnl\u00fck tahminle birlikte hava durumu g\u00f6r\u00fcn\u00fcm\u00fc."));
        LinearLayout l = list();
        String city = Weather.place(this);
        option(l, tr("Use My Location", "Konumumu Kullan"),
                city.isEmpty() ? tr("Found from your internet connection", "\u0130nternet ba\u011flant\u0131n\u0131zdan bulunur") : tr("Looks like ", "Tahmini konum: ") + city,
                Weather.enabled(this) && !Weather.manual(this), () -> {
                    Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, true).apply();
                    Weather.setPlace(this, null);
                    next();
                });
        option(l, tr("Choose a City\u2026", "\u015eehir Se\u00e7in\u2026"),
                Weather.manual(this) ? Weather.place(this) : tr("Search by name", "\u0130sme g\u00f6re ara"), Weather.manual(this), this::searchCityDialog);
        option(l, tr("Next", "\u0130leri"), null, false, this::next);
        option(l, tr("Don't Show Weather", "Hava Durumunu G\u00f6sterme"), null, !Weather.enabled(this), () -> {
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
        in.setHint(tr("City name, e.g. Istanbul", "\u015eehir ad\u0131, \u00f6rn. \u0130stanbul"));
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Choose City", "\u015eehir Se\u00e7in"))
                .setView(box)
                .setPositiveButton(tr("Search", "Ara"), (d, w) -> {
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
                .setNegativeButton(tr("Cancel", "\u0130ptal"), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    private void showCityMatches(String q, List<Weather.Place> res) {
        if (isFinishing()) return;
        if (res == null || res.isEmpty()) {
            android.widget.Toast.makeText(this,
                    res == null
                            ? tr("Couldn't search. Check the internet connection.", "Arama yap\u0131lamad\u0131. \u0130nternet ba\u011flant\u0131s\u0131n\u0131 kontrol edin.")
                            : tr("No place found for \u201c" + q + "\u201d.", "\u201c" + q + "\u201d i\u00e7in konum bulunamad\u0131."),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[res.size()];
        for (int i = 0; i < names.length; i++) {
            Weather.Place pl = res.get(i);
            names[i] = pl.detail.isEmpty() ? pl.name : pl.name + "  \u00b7  " + pl.detail;
        }
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Matching places", "E\u015fle\u015fen konumlar"))
                .setItems(names, (d, w) -> {
                    Weather.Place picked = res.get(w);
                    Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, true).apply();
                    Weather.setPlace(this, picked);
                    Weather.refresh(this, true, null);
                    show(index, 0);
                    focusRow(2);
                })
                .setNegativeButton(tr("Cancel", "\u0130ptal"), null)
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
        title(tr("Presentation", "Sunum"));
        subtitle(tr("Images, videos and webpages sent from any phone or computer on the same Wi-Fi. For everyone on this TV.",
                "Ayn\u0131 Wi-Fi a\u011f\u0131ndaki herhangi bir telefon veya bilgisayardan g\u00f6nderilen g\u00f6rseller, videolar ve web sayfalar\u0131."));
        boolean on = com.techgenius.tvoslauncher.present.Presenter.enabled(this);
        LinearLayout l = list();
        option(l, tr("Use Presentation", "Sunumu Kullan"),
                on ? tr("Adds a Presentation card to Home", "Ana Ekrana bir Sunum kart\u0131 ekler") : tr("Off", "Kapal\u0131"), on, () -> {
            com.techgenius.tvoslauncher.present.Presenter.set(this, com.techgenius.tvoslauncher.present.Presenter.KEY_ENABLED, !on);
            show(index, 0);
            focusRow(0);
        });
        if (on) {
            boolean boot = com.techgenius.tvoslauncher.present.Presenter.startOnBoot(this),
                    awake = com.techgenius.tvoslauncher.present.Presenter.keepAwake(this);
            option(l, tr("Start with the TV", "TV ile Ba\u015flat"),
                    tr("Play as soon as the TV turns on (signage)", "TV a\u00e7\u0131l\u0131r a\u00e7\u0131lmaz oynatmaya ba\u015fla (tabela modu)"), boot, () -> {
                com.techgenius.tvoslauncher.present.Presenter.set(this, com.techgenius.tvoslauncher.present.Presenter.KEY_BOOT, !boot);
                show(index, 0);
                focusRow(1);
            });
            option(l, tr("Keep Screen Awake", "Ekran\u0131 A\u00e7\u0131k Tut"),
                    tr("No screensaver or sleep while presenting", "Sunum s\u0131ras\u0131nda ekran koruyucu veya uyku yok"), awake, () -> {
                com.techgenius.tvoslauncher.present.Presenter.set(this, com.techgenius.tvoslauncher.present.Presenter.KEY_AWAKE, !awake);
                show(index, 0);
                focusRow(2);
            });
        }
        option(l, tr("Continue", "Devam Et"), null, false, this::next);
        dots();
    }

    boolean askedLocation;
    int permMode = 0; // 0 = Choose Easy or Manual, 1 = Easy ADB Directions, 2 = Manual Setup

    void homeAndPermissions() {
        if (permMode == 0) {
            title(tr("Finish Setting Up", "Kurulumu Tamamla"));
            TextView s = subtitle(tr(
                    "Choose how you want to set up permissions (Default Home Screen, Button Mapper Accessibility, Power Off, Continue Watching and Wi-Fi).",
                    "İzinleri (Varsayılan Ana Ekran, Düğme Eşleyici Erişilebilirliği, Kapatma, İzlemeye Devam Et ve Wi-Fi) nasıl ayarlamak istediğinizi seçin."));
            ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);
            LinearLayout l = list();
            option(l, tr("Easy Setup (Automatic via ADB)", "Kolay Kurulum (ADB ile Otomatik)"),
                    tr("Grants all required permissions automatically at once", "Gerekli tüm izinleri tek seferde otomatik olarak verir"),
                    false,
                    () -> {
                        if (!LocalAdb.isAdbSettingEnabled(this)) {
                            permMode = 1;
                            show(5, 1);
                        } else {
                            runEasyAdbGrant();
                        }
                    });
            option(l, tr("Manual Setup", "Manuel Kurulum"),
                    tr("Grant permissions one by one manually", "İzinleri tek tek elle verin"),
                    false,
                    () -> {
                        permMode = 2;
                        show(5, 1);
                    });
            option(l, tr("Continue", "Devam Et"), null, false, this::next);
            dots();
            return;
        }

        if (permMode == 1) {
            title(tr("Enable ADB Debugging", "ADB Hata Ayıklamayı Etkinleştirin"));
            TextView s = subtitle(tr(
                    "1. Open TV Settings \u2192 System \u2192 About and click Build 7 times to unlock Developer Options.\n"
                    + "2. Open Settings \u2192 System \u2192 Developer Options and enable USB Debugging / Network Debugging.\n"
                    + "3. Return here and select Grant Permissions Now.",
                    "1. TV Ayarları \u2192 Sistem \u2192 Hakkında menüsüne gidin ve Geliştirici Seçeneklerini açmak için Yapı Numarası'na 7 kez basın.\n"
                    + "2. Ayarlar \u2192 Sistem \u2192 Geliştirici Seçenekleri'nden USB / Ağ Hata Ayıklamayı açın.\n"
                    + "3. Buraya dönüp İzinleri Şimdi Ver'i seçin."));
            ((LinearLayout.LayoutParams) s.getLayoutParams()).width = u(620);
            ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);
            LinearLayout l = scrollableList();
            option(l, tr("Open Developer Options / TV Settings\u2026", "Geliştirici Seçeneklerini / TV Ayarlarını Aç\u2026"),
                    tr("Enable USB / Network Debugging in Developer Options", "Geliştirici Seçeneklerinde USB / Ağ Hata Ayıklamayı açın"),
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
            option(l, tr("Grant Permissions Now", "İzinleri Şimdi Ver"),
                    tr("Use local ADB to grant all permissions automatically", "Tüm izinleri otomatik vermek için yerel ADB'yi kullan"),
                    false,
                    this::runEasyAdbGrant);
            option(l, tr("Switch to Manual Setup", "Manuel Kuruluma Geç"),
                    tr("Grant permissions one by one instead", "İzinleri bunun yerine tek tek elle verin"),
                    false,
                    () -> {
                        permMode = 2;
                        show(5, 1);
                    });
            dots();
            return;
        }

        // permMode == 2: Manual Setup
        if (!Perms.hasLocation(this) && !askedLocation) {
            askedLocation = true;
            handler.postDelayed(() -> Perms.requestLocation(this), 600);
        }
        title(tr("Finish Setting Up", "Kurulumu Tamamla"));
        TextView s = subtitle(tr("tvOS Launcher works best as the home screen. Allow these once; anything this TV doesn't support is skipped.",
                "tvOS Launcher en iyi ana ekran olarak \u00e7al\u0131\u015f\u0131r. Bunlara bir kez izin verin; bu TV'nin desteklemedi\u011fi \u00f6zellikler atlan\u0131r."));
        ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);
        LinearLayout l = scrollableList();
        boolean home = Perms.isDefaultHome(this);
        option(l, tr("Make tvOS Launcher the Home Screen", "tvOS Launcher'\u0131 Ana Ekran Yap"),
                home ? tr("The Home button opens tvOS Launcher", "Ana Ekran d\u00fc\u011fmesi tvOS Launcher'\u0131 a\u00e7ar") : tr("Choose tvOS Launcher when asked", "Soruldu\u011funda tvOS Launcher'\u0131 se\u00e7in"), home,
                () -> Perms.requestDefaultHome(this));
        if (!Perms.hasLocation(this)) {
            option(l, tr("Show the Wi-Fi Name", "Wi-Fi Ad\u0131n\u0131 G\u00f6ster"),
                    tr("In Control Center (needs location access)", "Denetim Merkezi'nde (konum izni gerekir)"), false,
                    () -> Perms.requestLocation(this));
        }
        if (Perms.listenerSupported(this) && !Perms.hasListener(this)) {
            option(l, tr("Continue Watching", "\u0130zlemeye Devam Et"),
                    tr("Remember what you play in each app", "Her uygulamada ne oynatt\u0131\u011f\u0131n\u0131z\u0131 hat\u0131rla"), false, () -> Perms.requestListener(this));
        }
        if (Perms.adminSupported(this) && !Perms.hasAdmin(this)) {
            option(l, tr("Power Off & Sleep Timer", "Kapatma ve Uyku Zamanlay\u0131c\u0131s\u0131"),
                    tr("Let tvOS Launcher turn the screen off", "tvOS Launcher'\u0131n ekran\u0131 kapatmas\u0131na izin ver"), false, () -> Perms.requestAdmin(this));
        }
        boolean accOn = ButtonMapper.isServiceEnabled(this);
        option(l, tr("Button Mapper & Control Panel", "D\u00fc\u011fme E\u015fleyici ve Denetim Paneli"),
                accOn
                        ? tr("Active in Accessibility", "Eri\u015filebilirlik'te etkin")
                        : tr("Enable in Accessibility so remapped buttons work in all apps", "E\u015flenen d\u00fc\u011fmelerin t\u00fcm uygulamalarda \u00e7al\u0131\u015fmas\u0131 i\u00e7in Eri\u015filebilirlik'te etkinle\u015ftirin"),
                accOn,
                () -> {
                    Intent acc = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    if (!Suggestions.start(this, acc)) {
                        Suggestions.start(this, new Intent(android.provider.Settings.ACTION_SETTINGS));
                    }
                });
        option(l, tr("Continue", "Devam Et"), null, false, this::next);
        dots();
    }

    private void runEasyAdbGrant() {
        android.app.AlertDialog progress = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Easy Setup (ADB)", "Kolay Kurulum (ADB)"))
                .setMessage(tr(
                        "Granting permissions via local ADB\u2026\n\nIf an \u201cAllow USB debugging?\u201d prompt appears on screen, check \u201cAlways allow\u201d and press Allow.",
                        "Yerel ADB ile izinler veriliyor\u2026\n\nEkranda \u201cUSB hata ayıklamaya izin verilsin mi?\u201d penceresi çıkarsa \u201cHer zaman izin ver\u201di işaretleyip İzin Ver'e basın."))
                .setCancelable(false)
                .create();
        progress.show();

        LocalAdb.grantAllPermissions(this, (success, msg) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            try {
                progress.dismiss();
            } catch (Exception ignored) {
            }
            if (success) {
                android.widget.Toast.makeText(this,
                        tr("All permissions granted automatically!", "Tüm izinler otomatik olarak verildi!"),
                        android.widget.Toast.LENGTH_SHORT).show();
                next();
            } else {
                permMode = 1;
                show(5, 0);
                new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(tr("ADB Debugging Needed", "ADB Hata Ayıklama Gerekli"))
                        .setMessage(tr(
                                "Could not connect to local ADB on port 5555.\n\nPlease make sure USB Debugging / Network Debugging is turned ON in Developer Options, then try again — or choose Manual Setup.",
                                "Port 5555 üzerinden yerel ADB'ye bağlanılamadı.\n\nLütfen Geliştirici Seçenekleri'nde USB / Ağ Hata Ayıklamanın AÇIK olduğundan emin olup tekrar deneyin veya Manuel Kurulum'u seçin."))
                        .setPositiveButton(tr("OK", "Tamam"), null)
                        .setNeutralButton(tr("Manual Setup", "Manuel Kurulum"), (d, w) -> {
                            permMode = 2;
                            show(5, 1);
                        })
                        .show();
            }
        }));
    }

    /** Step right after permissions: let the user add remote buttons and assign 1x, 2x, 3x tap actions. */
    void buttonMapperSetup() {
        title(tr("Button Mapper", "Düğme Eşleyici"));
        TextView s = subtitle(tr(
                "Map any button on your remote with Single Tap (1x), Double Tap (2x) and Triple Tap (3x) actions — open the Control Panel, Recent Apps Switcher, or any app.",
                "Kumandanızdaki herhangi bir düğmeye Tek Basış (1x), Çift Basış (2x) ve Üçlü Basış (3x) eylemleri atayın — Denetim Paneli'ni, Son Uygulamalar Değiştiricisi'ni veya herhangi bir uygulamayı açın."));
        ((LinearLayout.LayoutParams) s.getLayoutParams()).width = u(580);
        ((LinearLayout.LayoutParams) s.getLayoutParams()).bottomMargin = u(16);

        LinearLayout l = scrollableList();
        if (!ButtonMapper.isServiceEnabled(this)) {
            option(l, tr("Enable Button Mapper in Accessibility…", "Erişilebilirlik'te Düğme Eşleyiciyi Etkinleştir…"),
                    tr("Required so mapped buttons work over every app", "Eşlenen düğmelerin her uygulamanın üzerinde çalışması için gereklidir"),
                    false,
                    () -> {
                        Intent acc = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        if (!Suggestions.start(this, acc)) {
                            Suggestions.start(this, new Intent(android.provider.Settings.ACTION_SETTINGS));
                        }
                    });
        }

        option(l, tr("+ Add New Button to Remap…", "+ Yeniden Eşlemek İçin Yeni Düğme Ekle…"),
                tr("Assign Single Tap (1x), Double Tap (2x) & Triple Tap (3x) actions", "Tek Basış (1x), Çift Basış (2x) ve Üçlü Basış (3x) eylemleri atayın"),
                false,
                () -> ButtonMapper.showCaptureAndConfigureDialog(this, () -> show(index, 0)));

        List<Integer> mappedKeys = ButtonMapper.getMappedKeys(this);
        for (final int keyCode : mappedKeys) {
            option(l, ButtonMapper.keyLabel(this, keyCode),
                    ButtonMapper.keySummaryLabel(this, keyCode),
                    true,
                    () -> ButtonMapper.showConfigureKeyDialog(this, keyCode, () -> show(index, 0)));
        }

        option(l, tr("Continue", "Devam Et"),
                mappedKeys.isEmpty() ? tr("You can also configure buttons later in Settings", "Düğmeleri daha sonra Ayarlar'dan da yapılandırabilirsiniz") : null,
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
        title(tr("You're All Set", "Her \u015eey Haz\u0131r"));
        subtitle(tr("Change any of this later in Settings, from the gear in Control Center.",
                "Bunlar\u0131 daha sonra Ayarlar'dan istedi\u011finiz zaman de\u011fi\u015ftirebilirsiniz."));
        LinearLayout l = list();
        option(l, tr("Go to Home Screen", "Ana Ekrana Git"), null, false, () -> {
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
