package app.gameport.hook;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Talks to GamePort's content provider. Each call carries the game's package name, which GamePort checks. */
final class ProviderLink implements GamePortLink {
    private final Context context;
    private final String pkg;
    private final Uri base;

    ProviderLink(Context context) {
        this.context = context;
        this.pkg = context.getPackageName();
        this.base = Owner.cloud(context);
    }

    private Bundle call(String method, Bundle extras) throws IOException {
        Bundle result = context.getContentResolver().call(base, method, pkg, extras);
        if (result == null) throw new IOException("GamePort refused or did not answer: " + method);
        return result;
    }

    @Override
    public Begin begin() throws IOException {
        Bundle result = call("begin", null);
        String status = result.getString("status", "ERROR");
        List<SaveRule> rules = new ArrayList<>();
        String[] lines = result.getStringArray("rules");
        if (lines != null) for (String line : lines) rules.add(SaveRule.parse(line));
        return new Begin(status, rules);
    }

    @Override
    public Plan plan(List<LocalFile> files, String force) throws IOException {
        Bundle extras = new Bundle();
        String[] encoded = new String[files.size()];
        for (int i = 0; i < encoded.length; i++) encoded[i] = files.get(i).encode();
        extras.putStringArray("files", encoded);
        if (force != null) extras.putString("force", force);
        Bundle result = call("plan", extras);

        List<Download> downloads = new ArrayList<>();
        String[] lines = result.getStringArray("download");
        if (lines != null) {
            for (String line : lines) {
                String[] p = line.split("\t", -1);
                downloads.add(new Download(p[0], p[1], p[2], Long.parseLong(p[4])));
            }
        }
        return new Plan(result.getString("action", "NONE"), downloads, asList(result.getStringArray("deleteLocal")), asList(result.getStringArray("upload")));
    }

    @Override
    public String conflictChoice() throws IOException {
        return call("conflict", null).getString("choice", "PENDING");
    }

    @Override
    public void showConflict() {
        Intent screen = new Intent().setClassName(Owner.packageName(context), "app.gameport.feature.sync.SyncActivity")
                .putExtra("pkg", pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(screen);
    }

    @Override
    public InputStream fetch(String cloudName) throws IOException {
        Uri uri = base.buildUpon().appendPath("fetch").appendQueryParameter("pkg", pkg).appendQueryParameter("name", cloudName).build();
        return context.getContentResolver().openInputStream(uri);
    }

    @Override
    public OutputStream push(String rel) throws IOException {
        Uri uri = base.buildUpon().appendPath("push").appendQueryParameter("pkg", pkg).appendQueryParameter("rel", rel).build();
        ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "w");
        if (descriptor == null) throw new FileNotFoundException("no upload target for " + rel);
        return new ParcelFileDescriptor.AutoCloseOutputStream(descriptor);
    }

    @Override
    public boolean acknowledge(List<LocalFile> files) throws IOException {
        Bundle extras = new Bundle();
        String[] encoded = new String[files.size()];
        for (int i = 0; i < encoded.length; i++) encoded[i] = files.get(i).encode();
        extras.putStringArray("files", encoded);
        return "OK".equals(call("ack", extras).getString("status"));
    }

    @Override
    public boolean observe(List<LocalFile> files) throws IOException {
        Bundle extras = new Bundle();
        String[] encoded = new String[files.size()];
        for (int i = 0; i < encoded.length; i++) encoded[i] = files.get(i).encode();
        extras.putStringArray("files", encoded);
        return "OK".equals(call("local", extras).getString("status"));
    }

    @Override
    public boolean commit() throws IOException {
        return "OK".equals(call("commit", null).getString("status"));
    }

    @Override
    public void end() {
        try {
            call("end", null);
        } catch (IOException ignored) {
            // GamePort may already be gone; nothing to release then.
        }
    }

    private static List<String> asList(String[] values) {
        List<String> list = new ArrayList<>();
        if (values != null) for (String value : values) list.add(value);
        return list;
    }
}
