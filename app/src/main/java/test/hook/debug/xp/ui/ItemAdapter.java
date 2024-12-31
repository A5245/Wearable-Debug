package test.hook.debug.xp.ui;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import kotlin.Pair;

/**
 * @author user
 */
public class ItemAdapter extends BaseAdapter {
    private final List<Pair<String, View.OnClickListener>> nodes = new ArrayList<>();

    public void addNode(String name, View.OnClickListener listener) {
        nodes.add(new Pair<>(name, listener));
    }

    @Override
    public int getCount() {
        return nodes.size();
    }

    @Override
    public Object getItem(int position) {
        return nodes.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            TextView view = new TextView(parent.getContext());
            view.setTextColor(Color.BLACK);
            view.setTextSize(30);
            convertView = view;
        }

        Pair<String, View.OnClickListener> item = nodes.get(position);

        ((TextView) convertView).setText(item.getFirst());
        convertView.setOnClickListener(item.getSecond());

        return convertView;
    }
}
