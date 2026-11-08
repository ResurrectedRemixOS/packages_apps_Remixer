package com.resurrection.settings;

import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.system.ErrnoException;
import android.system.Os;

import com.android.settings.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enables Resurrected Remix font overlays through the theme overlay category.
 */
public final class FontController {

    public static final String CATEGORY = "android.theme.customization.font";
    private static final String TARGET = "android";
    // Set by Lineage's always-on font overlay.
    private static final String DEFAULT_FAMILY = "google-sans-flex";
    /** FontRrCustomOverlay: its font is the file the user imported, not a system family. */
    public static final String CUSTOM_PACKAGE = "com.resurrection.theme.font.custom";
    /**
     * Where the framework's FontController reads the imported font. Settings may write under
     * /data/misc/user/0 and every app may read it, so no policy change is needed.
     */
    static final File CUSTOM_FONT = new File("/data/misc/user/0/fonts/custom.ttf");

    private final Context mContext;
    private final IOverlayManager mOverlayManager;

    public FontController(Context context) {
        mContext = context;
        mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));
    }

    public List<OverlayInfo> fonts() {
        List<OverlayInfo> fonts = new ArrayList<>();
        if (mOverlayManager == null) {
            return fonts;
        }
        try {
            for (OverlayInfo info : mOverlayManager.getOverlayInfosForTarget(
                    TARGET, UserHandle.USER_SYSTEM)) {
                if (CATEGORY.equals(info.category)) {
                    fonts.add(info);
                }
            }
        } catch (RemoteException ignored) {
        }
        Map<String, String> labels = new HashMap<>();
        for (OverlayInfo info : fonts) labels.put(info.packageName, label(info.packageName));
        fonts.sort(Comparator.comparing(info -> labels.get(info.packageName),
                String.CASE_INSENSITIVE_ORDER));
        return fonts;
    }

    public String enabledPackage() {
        for (OverlayInfo info : fonts()) {
            if (info.isEnabled()) {
                return info.packageName;
            }
        }
        return TARGET;
    }

    public String label(String pkg) {
        if (TARGET.equals(pkg)) {
            return mContext.getString(R.string.font_default);
        }
        if (CUSTOM_PACKAGE.equals(pkg)) {
            String name = familyName(CUSTOM_FONT);
            if (name != null) return name;
        }
        PackageManager pm = mContext.getPackageManager();
        try {
            return pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    public Typeface typeface(String pkg) {
        if (TARGET.equals(pkg)) {
            return Typeface.getSystemDefaultTypeface(DEFAULT_FAMILY);
        }
        if (CUSTOM_PACKAGE.equals(pkg)) {
            Typeface custom = hasCustomFont() ? new Typeface.Builder(CUSTOM_FONT).build() : null;
            return custom != null ? custom : Typeface.getSystemDefaultTypeface(DEFAULT_FAMILY);
        }
        try {
            PackageManager pm = mContext.getPackageManager();
            Resources res = pm.getResourcesForApplication(pkg);
            int id = res.getIdentifier("config_bodyFontFamily", "string", pkg);
            if (id == 0) {
                return Typeface.getSystemDefaultTypeface(DEFAULT_FAMILY);
            }
            // Typeface.create() would hand back the font in use for names like "google-sans".
            return Typeface.getSystemDefaultTypeface(res.getString(id));
        } catch (PackageManager.NameNotFoundException e) {
            return Typeface.getSystemDefaultTypeface(DEFAULT_FAMILY);
        }
    }

    public void apply(String pkg) throws RemoteException {
        if (mOverlayManager == null) {
            throw new RemoteException("overlay service missing");
        }
        if (TARGET.equals(pkg)) {
            for (OverlayInfo info : fonts()) {
                if (info.isEnabled()) {
                    mOverlayManager.setEnabled(info.packageName, false, UserHandle.USER_SYSTEM);
                }
            }
            writeSetting(null);
            return;
        }
        mOverlayManager.setEnabledExclusiveInCategory(pkg, UserHandle.USER_SYSTEM);
        writeSetting(pkg);
    }

    public boolean hasCustomFont() {
        return CUSTOM_FONT.isFile();
    }

    /** Copies the picked file over the custom font. False means it is not a font; nothing changes. */
    public boolean importFont(Uri uri) throws IOException {
        File dir = CUSTOM_FONT.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("mkdir " + dir);
        File tmp = new File(dir, CUSTOM_FONT.getName() + ".tmp");
        try (InputStream in = mContext.getContentResolver().openInputStream(uri);
                OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IOException("open " + uri);
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
            // Builder.build() returns null for a file minikin cannot read.
            if (new Typeface.Builder(tmp).build() == null) return false;
            Os.chmod(dir.getPath(), 0755);
            Os.chmod(tmp.getPath(), 0644);
            Os.rename(tmp.getPath(), CUSTOM_FONT.getPath());
        } catch (ErrnoException e) {
            throw new IOException(e);
        } finally {
            tmp.delete();
        }
        return true;
    }

    public void removeCustomFont() {
        CUSTOM_FONT.delete();
    }

    /** Apps only reload the file on a configuration change, so the overlay goes off and on. */
    public void reapplyCustomFont() throws RemoteException {
        apply(TARGET);
        apply(CUSTOM_PACKAGE);
    }

    /**
     * The family name from the font's name table: the typographic family when there is one,
     * else the family, preferring English and Windows/Unicode strings over Mac ones. Null when
     * the file has none.
     */
    static String familyName(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long base = 0;
            if (raf.readInt() == 0x74746366) { // ttcf: the first face
                raf.seek(12);
                base = raf.readInt() & 0xFFFFFFFFL;
            }
            raf.seek(base + 4);
            int tables = raf.readUnsignedShort();
            raf.seek(base + 12);
            long nameTable = -1;
            for (int i = 0; i < tables; i++) {
                int tag = raf.readInt();
                raf.skipBytes(4);
                long offset = raf.readInt() & 0xFFFFFFFFL;
                raf.skipBytes(4);
                if (tag == 0x6E616D65) { // name
                    nameTable = offset;
                    break;
                }
            }
            if (nameTable < 0) return null;
            raf.seek(nameTable + 2);
            int count = raf.readUnsignedShort();
            long strings = nameTable + raf.readUnsignedShort();
            String best = null;
            int bestScore = -1;
            for (int i = 0; i < count; i++) {
                raf.seek(nameTable + 6 + i * 12L);
                int platform = raf.readUnsignedShort();
                int encoding = raf.readUnsignedShort();
                int language = raf.readUnsignedShort();
                int nameId = raf.readUnsignedShort();
                int length = raf.readUnsignedShort();
                int offset = raf.readUnsignedShort();
                if (nameId != 1 && nameId != 16) continue;
                boolean mac = platform == 1;
                if (!mac && platform != 0 && platform != 3) continue;
                if (mac && encoding != 0) continue;
                int score = (nameId == 16 ? 4 : 0) + (mac ? 0 : 2)
                        + ((mac ? language == 0 : language == 0x409) ? 1 : 0);
                if (score <= bestScore) continue;
                byte[] bytes = new byte[length];
                raf.seek(strings + offset);
                raf.readFully(bytes);
                String name = new String(bytes,
                        mac ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_16BE).trim();
                if (name.isEmpty()) continue;
                best = name;
                bestScore = score;
            }
            return best;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void writeSetting(String pkg) {
        String current = Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES);
        JSONObject object;
        try {
            object = current == null ? new JSONObject() : new JSONObject(current);
        } catch (JSONException e) {
            object = new JSONObject();
        }
        try {
            if (pkg == null) {
                object.remove(CATEGORY);
            } else {
                object.put(CATEGORY, pkg);
            }
            Settings.Secure.putString(mContext.getContentResolver(),
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, object.toString());
        } catch (JSONException ignored) {
        }
    }
}
