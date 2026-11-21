package app.multipost;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

/** Ayudas para armar las pantallas en código. */
final class Ui {
    private Ui() {
    }

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Columna con margen dentro de un ScrollView. Devuelve la columna; su padre es el scroll. */
    static LinearLayout page(Context c) {
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 16);
        col.setPadding(p, p, p, dp(c, 32));
        scroll.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    static void add(LinearLayout col, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(col.getContext(), 8);
        col.addView(v, lp);
    }

    static TextView heading(LinearLayout col, String text) {
        TextView t = new TextView(col.getContext());
        t.setText(text);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(col.getContext(), 20), 0, 0);
        add(col, t);
        return t;
    }

    static TextView text(LinearLayout col, String text) {
        TextView t = new TextView(col.getContext());
        t.setText(text);
        t.setTextSize(14);
        t.setTextIsSelectable(true);
        add(col, t);
        return t;
    }

    static EditText input(LinearLayout col, String hint, boolean multiline) {
        EditText e = new EditText(col.getContext());
        e.setHint(hint);
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setMinLines(3);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setSingleLine(true);
        }
        add(col, e);
        return e;
    }

    static Button button(LinearLayout col, String text) {
        Button b = new Button(col.getContext());
        b.setText(text);
        b.setAllCaps(false);
        add(col, b);
        return b;
    }

    static Spinner spinner(LinearLayout col, String[] labels) {
        Spinner s = new Spinner(col.getContext());
        ArrayAdapter<String> a = new ArrayAdapter<>(col.getContext(),
                android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        add(col, s);
        return s;
    }

    static int indexOf(String[] values, String v) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(v)) return i;
        }
        return 0;
    }
}
