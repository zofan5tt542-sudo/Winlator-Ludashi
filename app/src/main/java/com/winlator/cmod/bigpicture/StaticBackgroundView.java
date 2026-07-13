package com.winlator.cmod.bigpicture;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;

public class StaticBackgroundView extends View {

    private Bitmap originalBitmap;
    private Bitmap scaledBitmap;
    private Paint paint;
    private String currentMode = "stretch";
    private static final String TAG = "StaticBgView";

    public StaticBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setDither(true);
        loadDefaultWallpaper();
    }

    private void loadDefaultWallpaper() {
        try {
            AssetManager assetManager = getContext().getAssets();
            InputStream inputStream = assetManager.open("default_bg.png");
            originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (originalBitmap != null) {
                Log.i(TAG, "تم تحميل الصورة بنجاح");
                requestLayout();
                invalidate();
            } else {
                Log.e(TAG, "فشل تحميل الصورة");
                setBackgroundColor(Color.BLACK);
            }
        } catch (IOException e) {
            Log.e(TAG, "خطأ في تحميل الصورة: " + e.getMessage());
            setBackgroundColor(Color.BLACK);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        if (w > 0 && h > 0 && originalBitmap != null && !originalBitmap.isRecycled()) {
            applyCurrentMode(w, h);
        }
    }

    private void applyCurrentMode(int viewWidth, int viewHeight) {
        if (originalBitmap == null || originalBitmap.isRecycled()) return;

        // تنظيف الصورة المقاسة القديمة
        if (scaledBitmap != null && !scaledBitmap.isRecycled() && scaledBitmap != originalBitmap) {
            scaledBitmap.recycle();
            scaledBitmap = null;
        }

        switch (currentMode) {
            case "stretch":
                // تمديد الصورة لملء الشاشة بالكامل
                scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, viewWidth, viewHeight, true);
                paint.setShader(null);
                Log.i(TAG, "Stretch mode: " + viewWidth + "x" + viewHeight);
                break;

            case "center":
                // الصورة بحجمها الطبيعي في المنتصف
                scaledBitmap = originalBitmap;
                paint.setShader(null);
                Log.i(TAG, "Center mode: original size " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight());
                break;

            case "tile":
                // تكرار الصورة
                scaledBitmap = null;
                paint.setShader(new BitmapShader(originalBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
                Log.i(TAG, "Tile mode");
                break;

            default:
                // الوضع الافتراضي: stretch
                scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, viewWidth, viewHeight, true);
                paint.setShader(null);
                break;
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (currentMode.equals("tile") && paint.getShader() != null) {
            // رسم التكرار
            canvas.drawPaint(paint);
        } 
        else if (scaledBitmap != null && !scaledBitmap.isRecycled()) {
            if (currentMode.equals("center")) {
                // توسيط الصورة
                float left = (getWidth() - scaledBitmap.getWidth()) / 2f;
                float top = (getHeight() - scaledBitmap.getHeight()) / 2f;
                canvas.drawBitmap(scaledBitmap, left, top, paint);
            } else {
                // stretch - رسم يغطي الشاشة كاملة
                canvas.drawBitmap(scaledBitmap, 0, 0, paint);
            }
        }
        else if (originalBitmap != null && !originalBitmap.isRecycled()) {
            // Fallback: رسم الصورة الأصلية في المنتصف
            float left = (getWidth() - originalBitmap.getWidth()) / 2f;
            float top = (getHeight() - originalBitmap.getHeight()) / 2f;
            canvas.drawBitmap(originalBitmap, left, top, paint);
        } else {
            canvas.drawColor(Color.BLACK);
        }
    }

    public void setWallpaper(Bitmap bitmap, String mode) {
        if (bitmap == null || bitmap.isRecycled()) return;
        
        // حفظ الوضع الجديد
        currentMode = (mode != null) ? mode : "stretch";
        
        // تنظيف الصور القديمة
        if (originalBitmap != null && !originalBitmap.isRecycled() && originalBitmap != bitmap) {
            originalBitmap.recycle();
        }
        if (scaledBitmap != null && !scaledBitmap.isRecycled() && scaledBitmap != originalBitmap && scaledBitmap != bitmap) {
            scaledBitmap.recycle();
        }
        
        originalBitmap = bitmap;
        
        // تطبيق الوضع الجديد على الفور
        if (getWidth() > 0 && getHeight() > 0) {
            applyCurrentMode(getWidth(), getHeight());
        } else {
            requestLayout();
        }
        invalidate();
    }

    public void cleanup() {
        if (scaledBitmap != null && !scaledBitmap.isRecycled() && scaledBitmap != originalBitmap) {
            scaledBitmap.recycle();
            scaledBitmap = null;
        }
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
            originalBitmap = null;
        }
        paint.setShader(null);
    }

    public void resetToDefaultWallpaper() {
        // حفظ الوضع الحالي مؤقتاً
        String previousMode = currentMode;
        
        // تنظيف الصور القديمة
        cleanup();
        
        // تحميل الصورة الافتراضية من assets
        try {
            AssetManager assetManager = getContext().getAssets();
            InputStream inputStream = assetManager.open("default_bg.png");
            originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (originalBitmap != null) {
                Log.i(TAG, "تم تحميل الصورة الافتراضية بنجاح");
                // استعادة الوضع السابق (stretch غالباً)
                currentMode = previousMode;
                
                // تطبيق الوضع على حجم الشاشة الحالي
                if (getWidth() > 0 && getHeight() > 0) {
                    applyCurrentMode(getWidth(), getHeight());
                } else {
                    requestLayout();
                }
                invalidate();
            } else {
                Log.e(TAG, "فشل تحميل الصورة الافتراضية");
                setBackgroundColor(Color.BLACK);
            }
        } catch (IOException e) {
            Log.e(TAG, "خطأ في تحميل الصورة الافتراضية: " + e.getMessage());
            setBackgroundColor(Color.BLACK);
        }
    }
            }
