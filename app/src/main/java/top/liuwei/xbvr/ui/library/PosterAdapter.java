package top.liuwei.xbvr.ui.library;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import top.liuwei.xbvr.R;
import top.liuwei.xbvr.Ui;
import top.liuwei.xbvr.domain.CoverRatioPolicy;
import top.liuwei.xbvr.domain.CoverRepository;
import top.liuwei.xbvr.domain.Models.Entry;

/**
 * Poster wall adapter. It owns only presentation: the recycled card layout, the cover-cache lookups
 * and the per-bind scale type. Resume/favourite state, the poster cache key and filter changes come
 * in through {@link EntryStatus} and {@link Listener}; it never reads Store, prefs or JSON and never
 * performs HTTP itself.
 */
public final class PosterAdapter extends BaseAdapter {
    /** Per-entry page state resolved by the owner, so the adapter stays presentation-only. */
    public interface EntryStatus {
        String posterKey(Entry entry);

        long resume(Entry entry);

        boolean favorite(Entry entry);
    }

    /** Page actions a card can trigger. */
    public interface Listener {
        void credit(String value, boolean studio);

        /** A bitmap that was already cached when the card was bound; ratio inference is deferred. */
        void coverCached(Bitmap bitmap);

        /** A bitmap that finished loading; ratio inference may run immediately. */
        void coverDecoded(Bitmap bitmap);

        /** The failed-cover reload button: retry, rebind and restore the scroll position. */
        void coverRetry();
    }

    private final Activity activity;
    private final LibraryUiState state;
    private final Ui.Palette colors;
    private final IntSupplier generation;
    private final EntryStatus status;
    private final Listener listener;
    private CoverRepository<Bitmap> covers;

    public PosterAdapter(
            Activity activity,
            LibraryUiState state,
            Ui.Palette colors,
            CoverRepository<Bitmap> covers,
            IntSupplier generation,
            EntryStatus status,
            Listener listener) {
        this.activity = activity;
        this.state = state;
        this.colors = colors;
        this.covers = covers;
        this.generation = generation;
        this.status = status;
        this.listener = listener;
    }

    /** Covers are per server: the page swaps the repository when it switches profiles. */
    public void setCovers(CoverRepository<Bitmap> value) {
        covers = value;
    }

    public int getCount() {
        return state.visible.size();
    }

    public Object getItem(int p) {
        return state.visible.get(p);
    }

    public long getItemId(int p) {
        return p;
    }

    private final class PosterFrame extends FrameLayout {
        PosterFrame() {
            super(activity);
        }

        @Override
        protected void onMeasure(int width, int height) {
            int w = MeasureSpec.getSize(width);
            super.onMeasure(
                    width,
                    MeasureSpec.makeMeasureSpec(
                            Math.max(1, Math.round(w / state.coverRatio)), MeasureSpec.EXACTLY));
        }
    }

    private final class Card {
        String imageKey;
        LinearLayout root;
        PosterFrame frame;
        ImageView poster, placeholder;
        TextView title, info, badge, imageHint;
        LinearLayout credits;
        ImageView heart;
        ProgressBar progress;
    }

    private TextView label(String value, int size, int color) {
        TextView t = Ui.text(activity, value, size, color);
        t.setFontFeatureSettings("kern");
        return t;
    }

    private ImageView glyph(String name, int tint, int size) {
        ImageView v = new ImageView(activity);
        v.setImageDrawable(Ui.iconDrawable(name, tint));
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, size), Ui.dp(activity, size)));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    private LinearLayout.LayoutParams spacing(
            int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(
                Ui.dp(activity, left),
                Ui.dp(activity, top),
                Ui.dp(activity, right),
                Ui.dp(activity, bottom));
        return p;
    }

    private FrameLayout filterCapsule(
            String caption, String description, int textSize, Runnable action) {
        FrameLayout target = new FrameLayout(activity);
        target.setFocusable(true);
        target.setContentDescription(description);
        target.setBackground(Ui.ripple(activity, Color.TRANSPARENT, 16));
        target.setOnClickListener(v -> action.run());
        Button face = Ui.button(activity, caption, action);
        face.setTextSize(textSize);
        face.setPadding(
                Ui.dp(activity, 7), Ui.dp(activity, 2), Ui.dp(activity, 7), Ui.dp(activity, 2));
        face.setMinHeight(0);
        face.setMinimumHeight(0);
        face.setBackground(Ui.ripple(activity, colors.soft, 16));
        face.setFocusable(false);
        face.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        target.addView(face, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return target;
    }

    private void credit(Card card, String value, boolean studio) {
        FrameLayout button =
                filterCapsule(
                        value,
                        activity.getString(
                                R.string.main_credit_filter,
                                studio
                                        ? activity.getString(R.string.main_studio)
                                        : activity.getString(R.string.main_actor),
                                value),
                        11,
                        () -> listener.credit(value, studio));
        card.credits.addView(button, spacing(-2, Ui.dp(activity, 40), 0, 0, 4, 0));
    }

    // Automatic mode keeps the whole artwork visible; a fixed ratio is a deliberate crop, so fill
    // the frame.
    private ImageView.ScaleType posterScaleType() {
        return CoverRatioPolicy.crop(state.coverMode)
                ? ImageView.ScaleType.CENTER_CROP
                : ImageView.ScaleType.FIT_CENTER;
    }

    public View getView(int position, View recycled, ViewGroup parent) {
        Card c;
        if (recycled == null) {
            c = new Card();
            c.root = Ui.column(activity);
            c.root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            c.root.setBackground(Ui.ripple(activity, colors.bg, 6));
            c.root.setTag(c);
            c.frame = new PosterFrame();
            c.frame.setBackground(Ui.rounded(colors.surface, 6, activity));
            c.frame.setClipToOutline(true);
            c.root.addView(c.frame, new LinearLayout.LayoutParams(-1, -2));
            c.poster = new ImageView(activity);
            c.frame.addView(c.poster, new FrameLayout.LayoutParams(-1, -1));
            c.placeholder = glyph("film", colors.muted, 42);
            c.frame.addView(
                    c.placeholder,
                    new FrameLayout.LayoutParams(
                            Ui.dp(activity, 42), Ui.dp(activity, 42), Gravity.CENTER));
            View gradient = new View(activity);
            gradient.setBackground(
                    new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[] {Color.TRANSPARENT, 0xAA000000}));
            c.frame.addView(
                    gradient, new FrameLayout.LayoutParams(-1, Ui.dp(activity, 64), Gravity.BOTTOM));
            c.badge = label("", 10, Color.WHITE);
            c.badge.setPadding(
                    Ui.dp(activity, 7),
                    Ui.dp(activity, 4),
                    Ui.dp(activity, 7),
                    Ui.dp(activity, 4));
            c.badge.setBackground(Ui.rounded(0xAA101418, 7, activity));
            FrameLayout.LayoutParams badgeParams =
                    new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT | Gravity.TOP);
            badgeParams.setMargins(0, Ui.dp(activity, 9), Ui.dp(activity, 9), 0);
            c.frame.addView(c.badge, badgeParams);
            c.heart = glyph("heart", colors.accent, 19);
            FrameLayout.LayoutParams heartParams =
                    new FrameLayout.LayoutParams(
                            Ui.dp(activity, 27), Ui.dp(activity, 27), Gravity.LEFT | Gravity.TOP);
            heartParams.setMargins(Ui.dp(activity, 8), Ui.dp(activity, 8), 0, 0);
            c.heart.setPadding(
                    Ui.dp(activity, 4),
                    Ui.dp(activity, 4),
                    Ui.dp(activity, 4),
                    Ui.dp(activity, 4));
            c.heart.setBackground(Ui.rounded(0xBB101418, 8, activity));
            c.frame.addView(c.heart, heartParams);
            ImageView play = glyph("play", Color.WHITE, 19);
            FrameLayout.LayoutParams playParams =
                    new FrameLayout.LayoutParams(
                            Ui.dp(activity, 19), Ui.dp(activity, 19), Gravity.LEFT | Gravity.BOTTOM);
            playParams.setMargins(Ui.dp(activity, 11), 0, 0, Ui.dp(activity, 12));
            c.frame.addView(play, playParams);
            c.progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            c.progress.setMax(1000);
            c.progress.setProgressTintList(ColorStateList.valueOf(colors.accent));
            c.progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x66000000));
            c.frame.addView(
                    c.progress, new FrameLayout.LayoutParams(-1, Ui.dp(activity, 3), Gravity.BOTTOM));
            c.title = label("", 15, colors.text);
            c.title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            c.title.setMaxLines(2);
            c.title.setMinLines(2);
            c.title.setEllipsize(TextUtils.TruncateAt.END);
            c.root.addView(c.title, spacing(-1, -2, 1, 6, 1, 0));
            c.info = label("", 10, Color.WHITE);
            c.info.setSingleLine();
            c.info.setEllipsize(TextUtils.TruncateAt.END);
            c.info.setPadding(
                    Ui.dp(activity, 7),
                    Ui.dp(activity, 3),
                    Ui.dp(activity, 7),
                    Ui.dp(activity, 3));
            c.info.setBackground(Ui.rounded(0xB3101418, 7, activity));
            FrameLayout.LayoutParams infoParams =
                    new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT | Gravity.BOTTOM);
            infoParams.setMargins(0, 0, Ui.dp(activity, 7), Ui.dp(activity, 9));
            c.frame.addView(c.info, infoParams);
            HorizontalScrollView creditsScroll = new HorizontalScrollView(activity);
            creditsScroll.setHorizontalScrollBarEnabled(false);
            c.credits = Ui.row(activity);
            creditsScroll.addView(c.credits);
            c.root.addView(creditsScroll);
            c.imageHint = label(activity.getString(R.string.main_cover_failed), 11, colors.muted);
            c.imageHint.setGravity(Gravity.CENTER);
            c.imageHint.setMinimumHeight(Ui.dp(activity, 48));
            c.root.addView(c.imageHint);
        } else c = (Card) recycled.getTag();
        Entry e = state.visible.get(position);
        long resume = status.resume(e);
        boolean favorite = status.favorite(e);
        c.title.setText(e.title);
        c.badge.setText(
                e.duration > 0
                        ? Ui.time(e.duration)
                        : activity.getString(R.string.main_video_badge));
        c.info.setText(
                resume > 0
                        ? activity.getString(R.string.main_resume_at, Ui.time(resume))
                        : activity.getString(R.string.main_start_watching));
        c.heart.setVisibility(favorite ? View.VISIBLE : View.GONE);
        c.progress.setVisibility(resume > 0 ? View.VISIBLE : View.GONE);
        c.progress.setProgress(
                e.duration > 0 ? (int) Math.min(1000, resume * 1000 / e.duration) : 0);
        c.root.setContentDescription(
                e.title
                        + (e.duration > 0
                                ? activity.getString(R.string.main_card_duration, Ui.time(e.duration))
                                : "")
                        + (resume > 0
                                ? activity.getString(R.string.main_card_resume, Ui.time(resume))
                                : "")
                        + (favorite ? activity.getString(R.string.main_card_favorite) : "")
                        + activity.getString(R.string.main_card_actions));
        c.credits.removeAllViews();
        if (!e.studio.isEmpty()) credit(c, e.studio, true);
        for (String actor : e.actors) credit(c, actor, false);
        String imageKey = status.posterKey(e);
        c.imageHint.setContentDescription(
                activity.getString(R.string.main_reload_cover, e.title));
        final CoverRepository<Bitmap> repository = covers;
        c.imageHint.setVisibility(
                repository != null && repository.failed(imageKey) ? View.VISIBLE : View.GONE);
        c.imageHint.setOnClickListener(
                v -> {
                    if (covers != null) covers.retry(imageKey);
                    listener.coverRetry();
                });
        c.imageKey = imageKey;
        Bitmap bitmap = repository == null ? null : repository.cached(imageKey);
        listener.coverCached(bitmap);
        ImageView.ScaleType wanted = posterScaleType();
        if (c.poster.getScaleType() != wanted) c.poster.setScaleType(wanted);
        c.poster.setImageBitmap(bitmap);
        c.placeholder.setVisibility(bitmap == null ? View.VISIBLE : View.GONE);
        List<String> candidates = new ArrayList<>(e.posterCandidates);
        if (!e.poster.isBlank() && !candidates.contains(e.poster)) candidates.add(e.poster);
        final int imageGen = generation.getAsInt();
        final Card card = c;
        if (repository != null && bitmap == null && !candidates.isEmpty()) {
            repository.request(
                    generation.getAsInt(),
                    imageKey,
                    candidates,
                    (completedKey, image, width, height) -> {
                        if (covers != repository
                                || imageGen != generation.getAsInt()
                                || activity.isDestroyed()
                                || !completedKey.equals(card.imageKey)) return;
                        if (image != null) listener.coverDecoded(image);
                        card.poster.setImageBitmap(image);
                        card.placeholder.setVisibility(image == null ? View.VISIBLE : View.GONE);
                        card.imageHint.setVisibility(image == null ? View.VISIBLE : View.GONE);
                    });
        }
        return c.root;
    }
}
