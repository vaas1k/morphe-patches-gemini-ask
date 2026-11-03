package app.geminiask.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.youtube.addon.AddOnApi;
import app.morphe.extension.youtube.patches.VideoInformation;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GeminiAsk {
    static final String GEMINI = "com.google.android.apps.bard";
    static final String KEY = "morphe_gemini_prompts";
    static final String TARGET_KEY = "morphe_gemini_target";
    static final String DEFAULT = "Перескажи по-русски\nГлавные тезисы\nОтветь на вопросы по видео";
    static final String NO_PROMPT = "Без промпта";
    static final String[] TARGETS = {"Gemini", "Яндекс"};
    static final String[] TARGET_IDS = {"gemini", "yandex"};
    static final String[] PACKAGES = {GEMINI, "com.yandex.aliceapp"};
    static final int YANDEX = 1;
    static final BooleanSetting BUTTON = new BooleanSetting("morphe_gemini_ask_button", true);

    /** Called from AddOnManager.registerAddOns(). */
    public static void register() {
        AddOnApi.addLegacyPlayerControlsListener(controls -> AddOnApi.createLegacyButton(
                "gemini_ask", controls, "morphe_gemini_ask_button", BUTTON,
                v -> start(v.getContext(), new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/" + VideoInformation.getVideoId())),
                null));
    }

    public static void start(Context context, Intent intent) {
        Activity activity = activity(context);
        if (activity == null) {
            context.startActivity(intent);
            return;
        }
        SharedPreferences prefs = activity.getSharedPreferences("morphe_prefs", Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, DEFAULT);
        List<String> prompts = parse(raw);
        prompts.add(NO_PROMPT);
        String[] items = prompts.toArray(new String[0]);
        String url = intent.getStringExtra(Intent.EXTRA_TEXT);
        boolean alice = !activity.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(PACKAGES[YANDEX]), 0).isEmpty();

        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(pad, pad, pad, 0);
        TextView title = new TextView(activity);
        title.setText("AI");
        title.setTextSize(20);
        head.addView(title);
        RadioGroup group = new RadioGroup(activity);
        for (int i = 0; i < TARGETS.length; i++) {
            RadioButton b = new RadioButton(activity);
            b.setId(i + 1);
            b.setText(TARGETS[i]);
            group.addView(b);
        }
        head.addView(group);
        TextView note = new TextView(activity);
        note.setText("Яндекс: откроется 300.ya.ru, ссылка в буфере обмена, промпт не передаётся");
        head.addView(note);
        group.setOnCheckedChangeListener((g, id) ->
                note.setVisibility(id - 1 == YANDEX && !alice ? View.VISIBLE : View.GONE));
        group.check(targetIndex(prefs.getString(TARGET_KEY, null)) + 1);

        new AlertDialog.Builder(activity)
                .setCustomTitle(head)
                .setItems(items, (d, which) -> {
                    int t = group.getCheckedRadioButtonId() - 1;
                    prefs.edit().putString(TARGET_KEY, TARGET_IDS[t]).apply();
                    String p = which == items.length - 1 ? "" : items[which];
                    launch(activity, intent, t, text(p, url), url, alice);
                })
                .setNeutralButton("Изменить", (d, w) -> {
                    EditText edit = new EditText(activity);
                    edit.setText(raw);
                    new AlertDialog.Builder(activity)
                            .setTitle("Промпты, по одному в строке")
                            .setView(edit)
                            .setPositiveButton(android.R.string.ok, (d2, w2) ->
                                    prefs.edit().putString(KEY, edit.getText().toString()).apply())
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .show();
    }

    static void launch(Activity a, Intent send, int target, String text, String url, boolean alice) {
        Intent i;
        if (target == YANDEX && !alice) {
            // 300.ya.ru ignores ?url= (checked 2026-10-25): hand the link over via the clipboard
            ((ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("url", url));
            i = new Intent(Intent.ACTION_VIEW, Uri.parse(yandexUrl(url)));
        } else {
            i = send.setPackage(PACKAGES[target]).putExtra(Intent.EXTRA_TEXT, text);
        }
        // checked up front: YouTube's activity swallows ActivityNotFoundException with its own generic toast
        if (i.getPackage() != null && a.getPackageManager().queryIntentActivities(i, 0).isEmpty()) {
            Toast.makeText(a, TARGETS[target] + " не установлен", Toast.LENGTH_LONG).show();
            return;
        }
        a.startActivity(i);
    }

    static int targetIndex(String id) {
        return Math.max(0, Arrays.asList(TARGET_IDS).indexOf(id));
    }

    static String yandexUrl(String url) {
        try {
            return "https://300.ya.ru/?url=" + URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<String> parse(String raw) {
        List<String> out = new ArrayList<>();
        for (String line : raw.split("\n")) {
            if (!line.trim().isEmpty()) out.add(line.trim());
        }
        return out;
    }

    static String text(String prompt, String url) {
        return prompt.isEmpty() ? url : prompt + "\n" + url;
    }

    private static Activity activity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    /** Self-check of the pure logic: java -cp classes:android.jar app.morphe.extension.youtube.GeminiAsk */
    public static void main(String[] a) {
        if (!parse(DEFAULT).equals(Arrays.asList("Перескажи по-русски", "Главные тезисы", "Ответь на вопросы по видео"))) throw new AssertionError("parse default");
        if (parse(" a \n\n b\n").size() != 2) throw new AssertionError("parse blanks");
        if (!text("p", "u").equals("p\nu")) throw new AssertionError("text");
        if (!text("", "u").equals("u")) throw new AssertionError("no prompt");
        if (!yandexUrl("https://youtu.be/ulHhwyCeP3M").equals("https://300.ya.ru/?url=https%3A%2F%2Fyoutu.be%2FulHhwyCeP3M")) throw new AssertionError("yandexUrl");
        if (targetIndex(null) != 0 || targetIndex("bogus") != 0 || targetIndex("perplexity") != 0 || targetIndex("yandex") != YANDEX) throw new AssertionError("targetIndex");
        System.out.println("GeminiAsk self-check OK");
    }
}
