package com.winlator.cmod;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

public class MyApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        // إجبار الاتجاه على LTR منذ البداية
        super.attachBaseContext(forceLtrContext(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        forceLtr();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // منع النظام من إعادة تطبيق الوضع RTL إذا تغيرت لغة الهاتف أثناء التشغيل
        forceLtr();
    }

    // دالة لإجبار الاتجاه داخل التطبيق نفسه
    private void forceLtr() {
        Resources res = getResources();
        Configuration config = res.getConfiguration();
        // Locale.US يفرض اتجاه LTR (يسار لليمين)
        config.setLayoutDirection(Locale.US);
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    // دالة لإجبار الاتجاه عند إنشاء السياق (Context)
    private Context forceLtrContext(Context context) {
        Resources res = context.getResources();
        Configuration config = res.getConfiguration();
        config.setLayoutDirection(Locale.US);
        return context.createConfigurationContext(config);
    }
}
