package app.gameport.hook;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

/**
 * The GamePort that patched this game: the one the hook talks to. Several can be on a device (the released one and a test build), each with
 * its own games, so the game says which one in its manifest. A game patched before that was written down belongs to the released one.
 */
final class Owner {
    private static final String RELEASED = "app.gameport";
    private static final String META = "app.gameport.owner";

    private static volatile String cached;

    private Owner() {}

    /** The package name of the GamePort this game belongs to. */
    static String packageName(Context context) {
        String known = cached;
        if (known != null) return known;
        String owner = RELEASED;
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            Bundle meta = info.metaData;
            String value = meta == null ? null : meta.getString(META);
            if (value != null && !value.isEmpty()) owner = value;
        } catch (Throwable ignored) {
            // Keeps the released GamePort.
        }
        cached = owner;
        return owner;
    }

    /** The provider that answers this game. */
    static Uri cloud(Context context) {
        return Uri.parse("content://" + packageName(context) + ".cloud");
    }
}
