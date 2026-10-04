package com.resurrection.settings;

import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Enables Resurrection Remix font overlays through the theme overlay category.
 */
public final class FontController {

    public static final String CATEGORY = "android.theme.customization.font";
    private static final String TARGET = "android";

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
        fonts.sort(Comparator.comparing(info -> label(info.packageName), String.CASE_INSENSITIVE_ORDER));
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
        PackageManager pm = mContext.getPackageManager();
        try {
            return pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    public Typeface typeface(String pkg) {
        if (TARGET.equals(pkg)) {
            return Typeface.DEFAULT;
        }
        try {
            PackageManager pm = mContext.getPackageManager();
            Resources res = pm.getResourcesForApplication(pkg);
            int id = res.getIdentifier("config_bodyFontFamily", "string", pkg);
            if (id == 0) {
                return Typeface.DEFAULT;
            }
            return Typeface.create(res.getString(id), Typeface.NORMAL);
        } catch (PackageManager.NameNotFoundException e) {
            return Typeface.DEFAULT;
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
