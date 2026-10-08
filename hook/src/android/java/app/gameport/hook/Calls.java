package app.gameport.hook;

import android.content.ContentProviderClient;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;

/**
 * The calls of the game to GamePort. They go through an unstable connection to its provider: when GamePort is stopped (a headset does it a few
 * seconds after a game starts, to free memory), a call in flight fails, and the game goes on. Through the usual connection, Android stops every
 * process that depends on the provider of a process that dies, and the game would be stopped with GamePort.
 */
final class Calls {
    private Calls() {}

    /** Same as {@code ContentResolver.call}: the answer, or null when GamePort gave none; an exception when it is gone. */
    static Bundle call(Context context, Uri uri, String method, String arg, Bundle extras) {
        ContentProviderClient client = context.getContentResolver().acquireUnstableContentProviderClient(uri);
        if (client == null) return null;
        try {
            return client.call(method, arg, extras);
        } catch (RemoteException e) {
            throw new IllegalStateException("GamePort went away during " + method, e);
        } finally {
            client.close();
        }
    }
}
