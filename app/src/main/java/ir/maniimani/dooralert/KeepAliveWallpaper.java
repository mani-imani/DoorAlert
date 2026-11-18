package ir.maniimani.dooralert;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/**
 * A quiet dark live wallpaper whose real job is keeping DoorAlert alive.
 *
 * When the car is switched off, the head unit force-stops every user-installed app except
 * the one providing the live wallpaper (android.car.server SleepHelper.standby). The system
 * also re-binds the wallpaper after every boot. So while this is the wallpaper, the door
 * watcher survives sleep and starts again by itself.
 */
public class KeepAliveWallpaper extends WallpaperService {

    static boolean isActive(Context c) {
        try {
            android.app.WallpaperInfo info = WallpaperManager.getInstance(c).getWallpaperInfo();
            return info != null && c.getPackageName().equals(info.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    static ComponentName component(Context c) {
        return new ComponentName(c, KeepAliveWallpaper.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DoorService.start(this);
    }

    @Override
    public Engine onCreateEngine() {
        return new Engine() {
            private final Paint p = new Paint(Paint.DITHER_FLAG);

            @Override
            public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                super.onSurfaceChanged(holder, format, width, height);
                draw(holder, width, height);
            }

            @Override
            public void onVisibilityChanged(boolean visible) {
                if (visible) {
                    SurfaceHolder h = getSurfaceHolder();
                    if (h.getSurfaceFrame().width() > 0) draw(h, h.getSurfaceFrame().width(), h.getSurfaceFrame().height());
                    DoorService.start(KeepAliveWallpaper.this);
                }
            }

            /** Static image: near-black with a soft cool glow. Drawn once, costs nothing. */
            private void draw(SurfaceHolder holder, int w, int h) {
                Canvas c = null;
                try {
                    c = holder.lockCanvas();
                    if (c == null) return;
                    c.drawColor(Color.BLACK);
                    float r = Math.max(w, h) * 0.75f;
                    p.setShader(new RadialGradient(w * 0.72f, h * 0.30f, r,
                            new int[]{Color.rgb(22, 30, 52), Color.rgb(8, 10, 16), Color.BLACK},
                            new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, h, p);
                    p.setShader(new RadialGradient(w * 0.15f, h * 0.95f, r * 0.6f,
                            new int[]{Color.argb(60, 60, 20, 40), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, h, p);
                } catch (Throwable ignored) {
                } finally {
                    if (c != null) {
                        try {
                            holder.unlockCanvasAndPost(c);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        };
    }
}
