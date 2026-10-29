package app.gameport.hook;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A game's "browse" button of its Workshop asks the system to open Steam's page, and the system gives it to the headset's browser: GamePort cannot ask to be offered the link
 * (the system keeps web links for the apps verified for the site). So the game's own requests to start a screen go through here, and the Workshop page of the game is handed to GamePort
 * instead, where the account is already signed in. Everything else goes through unchanged, and so does the page when GamePort cannot be reached.
 */
final class WorkshopRedirect extends Instrumentation {
    private static final String TAG = "GamePortHook";

    private final Instrumentation base;
    private final Context context;
    private final Method forActivity;
    private final Method forName;

    private WorkshopRedirect(Instrumentation base, Context context) throws Exception {
        this.base = base;
        this.context = context;
        forActivity = base.getClass().getMethod("execStartActivity", Context.class, IBinder.class, IBinder.class, Activity.class, Intent.class, int.class, Bundle.class);
        forName = base.getClass().getMethod("execStartActivity", Context.class, IBinder.class, IBinder.class, String.class, Intent.class, int.class, Bundle.class);
    }

    /** Puts the redirection in front of what starts the screens of this process. Nothing is changed when it cannot be done. */
    static void install(Context context) {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Object current = thread.getMethod("currentActivityThread").invoke(null);
            Field field = thread.getDeclaredField("mInstrumentation");
            field.setAccessible(true);
            Instrumentation base = (Instrumentation) field.get(current);
            if (base instanceof WorkshopRedirect) return;
            field.set(current, new WorkshopRedirect(base, context));
            Log.i(TAG, "Workshop pages are handed to GamePort");
        } catch (Throwable t) {
            Log.w(TAG, "could not watch the pages the game opens", t);
        }
    }

    /** The game a Workshop page of Steam's community is for, or 0 when the address is not one: either the page of the game's Workshop or the browse page that names the game. */
    static long workshopApp(Uri uri) {
        if (uri == null) return 0;
        String host = uri.getHost();
        String scheme = uri.getScheme();
        String path = uri.getPath();
        if (host == null || path == null || !("https".equals(scheme) || "http".equals(scheme))) return 0;
        if (!"steamcommunity.com".equalsIgnoreCase(host) && !"www.steamcommunity.com".equalsIgnoreCase(host)) return 0;
        java.util.regex.Matcher page = java.util.regex.Pattern.compile("/app/(\\d+)/workshop.*").matcher(path);
        if (page.matches()) return Long.parseLong(page.group(1));
        if (path.startsWith("/workshop/")) {
            String id = uri.getQueryParameter("appid");
            if (id != null && id.matches("\\d{1,10}")) return Long.parseLong(id);
        }
        return 0;
    }

    private Intent redirected(Intent intent) {
        try {
            if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return intent;
            Uri uri = intent.getData();
            String host = uri.getHost();
            // Only the host and the path are written, never the query: it is how the kind of page the game asks for is learnt.
            if (host != null && host.toLowerCase().contains("steam")) Log.i(TAG, "the game opens a page of " + host + uri.getPath());
            long app = workshopApp(uri);
            if (app == 0) return intent;
            Intent own = new Intent(intent).setComponent(null).setPackage(Owner.packageName(context))
                .setData(Uri.parse("https://steamcommunity.com/app/" + app + "/workshop/"));
            if (context.getPackageManager().resolveActivity(own, 0) == null) return intent;
            Log.i(TAG, "a Workshop page is opened in GamePort");
            return own;
        } catch (Throwable t) {
            return intent;
        }
    }

    // These two are what a screen of the game (or the game itself) goes through to start another one; the system class has them hidden, so they are not marked as overriding.
    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Activity target, Intent intent, int requestCode, Bundle options) {
        return call(forActivity, who, contextThread, token, target, redirected(intent), requestCode, options);
    }

    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, String target, Intent intent, int requestCode, Bundle options) {
        return call(forName, who, contextThread, token, target, redirected(intent), requestCode, options);
    }

    private ActivityResult call(Method method, Object... args) {
        try {
            return (ActivityResult) method.invoke(base, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
