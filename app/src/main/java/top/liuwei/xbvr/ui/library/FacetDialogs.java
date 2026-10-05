package top.liuwei.xbvr.ui.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import top.liuwei.xbvr.R;
import top.liuwei.xbvr.Ui;
import top.liuwei.xbvr.domain.LibraryFilterState;
import top.liuwei.xbvr.domain.Models.Entry;

/**
 * Category picker and studio/actor/tags facet dialog. Both only edit the live filter state and ask
 * the page to re-run the filter; cancelling commits nothing because the draft values are local to
 * the dialog until the positive button is pressed.
 */
public final class FacetDialogs {
    private final MainView view;
    private final Activity activity;

    FacetDialogs(MainView view) {
        this.view = view;
        this.activity = view.activity();
    }

    void categories() {
        LibraryFilterState filter = view.library().state().filter;
        List<String> names = new ArrayList<>();
        names.add(MainView.CATEGORY_ALL);
        for (Entry e : view.library().state().entries)
            for (String name : e.groups)
                if (!names.contains(name)) names.add(name);
        AlertDialog d =
                new AlertDialog.Builder(activity)
                        .setTitle(view.tr(R.string.main_category_title))
                        .setSingleChoiceItems(
                                names.stream().map(view::categoryText).toArray(String[]::new),
                                Math.max(0, names.indexOf(filter.category)),
                                (dialog, index) -> {
                                    filter.category = names.get(index);
                                    view.updateCategory();
                                    view.actions().filter(false);
                                    dialog.dismiss();
                                })
                        .setNegativeButton(view.tr(R.string.main_cancel), null)
                        .create();
        view.track(d, 3);
        d.show();
        view.tintDialog(d);
    }

    void facetDialog(int kind) {
        view.setFacetKind(kind);
        LibraryFilterState f = view.library().state().filter;
        String name =
                kind == 0
                        ? view.tr(R.string.main_studio)
                        : kind == 1
                                ? view.tr(R.string.main_actor)
                                : view.tr(R.string.main_tags);
        TreeSet<String> options = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Entry e : view.library().state().entries) {
            if (kind == 0 && !e.studio.isEmpty()) options.add(e.studio);
            else if (kind == 1) options.addAll(e.actors);
            else if (kind == 2) options.addAll(e.tags);
        }
        if (options.isEmpty()) {
            Toast.makeText(
                            activity,
                            view.library().state().metadataBusy
                                    ? view.tr(R.string.main_facet_loading)
                                    : view.tr(R.string.main_facet_unavailable, name),
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        LinearLayout form = Ui.column(activity);
        form.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
        EditText find =
                view.field(
                        form,
                        view.tr(R.string.main_search_facet_options, name),
                        view.tr(R.string.main_search_facet, name),
                        "",
                        false);
        ListView list = new ListView(activity);
        list.setChoiceMode(
                kind == 2 ? ListView.CHOICE_MODE_MULTIPLE : ListView.CHOICE_MODE_SINGLE);
        form.addView(list, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 240)));
        ArrayList<String> shown = new ArrayList<>();
        String[] single = {kind == 0 ? f.studio : f.actor};
        LinkedHashSet<String> tags = new LinkedHashSet<>(f.tags);
        Runnable bind =
                () -> {
                    shown.clear();
                    String needle = find.getText().toString().trim().toLowerCase(Locale.ROOT);
                    for (String option : options)
                        if (option.toLowerCase(Locale.ROOT).contains(needle)) shown.add(option);
                    list.setAdapter(
                            new ArrayAdapter<>(
                                    activity,
                                    kind == 2
                                            ? android.R.layout.simple_list_item_multiple_choice
                                            : android.R.layout.simple_list_item_single_choice,
                                    shown));
                    for (int i = 0; i < shown.size(); i++)
                        list.setItemChecked(
                                i,
                                kind == 2
                                        ? tags.contains(shown.get(i))
                                        : shown.get(i).equals(single[0]));
                };
        bind.run();
        list.setOnItemClickListener(
                (a, v, p, id) -> {
                    String value = shown.get(p);
                    if (kind == 2) {
                        if (!tags.add(value)) tags.remove(value);
                    } else single[0] = value;
                });
        find.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

                    public void onTextChanged(CharSequence s, int st, int b, int c) {
                        bind.run();
                    }

                    public void afterTextChanged(Editable e) {}
                });
        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle(view.tr(R.string.main_facet_title, name))
                        .setView(form)
                        .setNegativeButton(view.tr(R.string.main_cancel), null)
                        .setNeutralButton(
                                view.tr(R.string.main_clear_facet),
                                (d, w) -> {
                                    if (kind == 0) f.studio = "";
                                    else if (kind == 1) f.actor = "";
                                    else f.tags.clear();
                                    view.actions().filter(false);
                                })
                        .setPositiveButton(
                                view.tr(R.string.main_apply_filters),
                                (d, w) -> {
                                    if (kind == 0) f.studio = single[0];
                                    else if (kind == 1) f.actor = single[0];
                                    else {
                                        f.tags.clear();
                                        f.tags.addAll(tags);
                                    }
                                    view.actions().filter(false);
                                })
                        .create();
        view.track(dialog, 5);
        dialog.show();
        view.tintDialog(dialog);
    }
}
