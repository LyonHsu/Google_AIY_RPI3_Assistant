package com.example.androidthings.assistant.Setting;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;


import com.example.androidthings.assistant.R;

import java.util.List;

/**
 * Created by i_hfuhsu on 2017/3/24.
 */

public class AppsAdapter extends BaseAdapter {
    Activity activity;
    private List<ResolveInfo> apps;
    public AppsAdapter(Activity activity, List<ResolveInfo> apps){
        this.activity=activity;
        this.apps=apps;
    }

    @Override
    public int getCount() {
        return apps.size()+Setting.addCount;
    }

    @Override
    public Object getItem(int i) {
        return apps.get(i-Setting.addCount);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View v = view;
        Holder holder;
        if(v == null){
            v = LayoutInflater.from(activity).inflate(R.layout.app_list_item, null);
            holder = new Holder();
            holder.image = (ImageView) v.findViewById(R.id.imageView);
            holder.text = (TextView) v.findViewById(R.id.textView);
            holder.stext = (TextView) v.findViewById(R.id.textView2);

            v.setTag(holder);

            //holder.image.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        } else{
            holder = (Holder) v.getTag();
        }


        try {
            if (i == 1) {
                holder.image.setImageDrawable(activity.getDrawable(R.drawable.icon_ge));
                holder.text.setText("WIFI 設定(SETTING)");
                holder.stext.setText(""+this.getClass().getPackage().getName());
            }
            else if(i==0){
                holder.image.setImageDrawable(activity.getDrawable(android.R.drawable.ic_menu_revert));
                holder.text.setText("離開");
                holder.stext.setText("Back");
            }
            else {
                ResolveInfo info = apps.get(i-Setting.addCount);
                holder.image.setImageDrawable(info.activityInfo.loadIcon(activity.getPackageManager()));
                PackageManager pManager = activity.getPackageManager();
                holder.text.setText(info.loadLabel(pManager).toString());
                holder.stext.setText(info.activityInfo.packageName.toString());
            }
        }catch (IndexOutOfBoundsException e){
            e.printStackTrace();
        }
        return v;
    }

    class Holder{
        ImageView image;
        TextView text;
        TextView stext;
    }
}
