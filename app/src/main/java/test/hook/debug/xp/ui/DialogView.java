package test.hook.debug.xp.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;

import test.hook.debug.xp.Res;

/**
 * @author user
 */
public class DialogView {
    private final View view;
    private final ItemAdapter adapter;

    private DialogView(View view) {
        this.view = view;

        ListView list = view.findViewById(Res.options);

        this.adapter = new ItemAdapter();

        list.setAdapter(adapter);
    }

    public static DialogView create(Context context) {
        return new DialogView(LayoutInflater.from(context).inflate(Res.main, null));
    }

    public void addNode(String name, View.OnClickListener listener) {
        adapter.addNode(name, listener);
    }

    public View getView() {
        return view;
    }

}
