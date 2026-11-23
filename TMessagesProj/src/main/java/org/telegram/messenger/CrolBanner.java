package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;

/**
 * CrolGram: локальный баннер в шапке СВОЕГО профиля (только на этом устройстве).
 * banner_mode: 0 нет, 1 градиент (banner_grad = индекс пресета CrolStyle), 2 картинка (banner_image = путь к файлу).
 */
public class CrolBanner {

    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Rect src = new Rect();
    private static final RectF dst = new RectF();
    private static Bitmap bitmap;
    private static String bitmapPath;
    private static LinearGradient gradient;
    private static int gradW, gradH, gradIdx = -1;

    public static boolean isActive() {
        return CrolConfig.getInt("banner_mode", 0) != 0;
    }

    /** Рисует баннер в прямоугольнике 0..w x 0..h. Возвращает true, если что-то нарисовано. */
    public static boolean draw(Canvas canvas, int w, int h) {
        int mode = CrolConfig.getInt("banner_mode", 0);
        if (mode == 0 || w <= 0 || h <= 0) {
            return false;
        }
        try {
            if (mode == 1) {
                int idx = CrolConfig.getInt("banner_grad", 0);
                if (idx < 0 || idx >= CrolStyle.PRESETS.length) {
                    idx = 0;
                }
                if (gradient == null || gradW != w || gradH != h || gradIdx != idx) {
                    int[] src = CrolStyle.PRESETS[idx];
                    int[] cols = new int[src.length];
                    for (int i = 0; i < src.length; i++) {
                        cols[i] = 0xFF000000 | src[i];
                    }
                    gradient = new LinearGradient(0, 0, w, h, cols, null, Shader.TileMode.CLAMP);
                    gradW = w;
                    gradH = h;
                    gradIdx = idx;
                }
                paint.setShader(gradient);
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(null);
                return true;
            }
            String path = CrolConfig.getString("banner_image", "").trim();
            if (!TextUtils.equals(path, bitmapPath)) {
                bitmapPath = path;
                bitmap = TextUtils.isEmpty(path) ? null : BitmapFactory.decodeFile(path);
                if (bitmap == null && !TextUtils.isEmpty(path)) {
                    CrolConfig.logOnce("banner_bmp", "banner: не удалось прочитать картинку " + path);
                }
            }
            if (bitmap == null) {
                return false;
            }
            int bw = bitmap.getWidth();
            int bh = bitmap.getHeight();
            float scale = Math.max(w / (float) bw, h / (float) bh);
            int sw = Math.min(bw, Math.round(w / scale));
            int sh = Math.min(bh, Math.round(h / scale));
            int l = (bw - sw) / 2;
            int t = (bh - sh) / 2;
            src.set(l, t, l + sw, t + sh);
            dst.set(0, 0, w, h);
            paint.setShader(null);
            paint.setColor(0xFFFFFFFF);
            canvas.drawBitmap(bitmap, src, dst, paint);
            paint.setColor(0x40000000);
            canvas.drawRect(0, 0, w, h, paint);
            return true;
        } catch (Throwable e) {
            CrolConfig.logOnce("banner_err", "banner: " + e);
            return false;
        }
    }
}
