/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settingslib.qrcode.QrCamera;

/** Scans a look's QR code and hands its text back to Looks. */
public class LookScanActivity extends Activity implements TextureView.SurfaceTextureListener,
        QrCamera.ScannerCallback {
    static final String EXTRA_TEXT = "text";
    private static final int REQUEST_CAMERA = 1;

    private TextureView mPreview;
    private QrCamera mCamera;
    private boolean mDone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float density = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        mPreview = new TextureView(this);
        mPreview.setSurfaceTextureListener(this);
        root.addView(mPreview, new FrameLayout.LayoutParams(-1, -1));
        View frame = new View(this);
        GradientDrawable border = new GradientDrawable();
        border.setCornerRadius(24 * density);
        border.setStroke(Math.round(3 * density), getColor(android.R.color.system_accent1_300));
        frame.setBackground(border);
        int size = Math.round(250 * density);
        root.addView(frame, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        TextView hint = new TextView(this);
        hint.setText(R.string.looks_scan_hint);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.bottomMargin = Math.round(96 * density);
        root.addView(hint, lp);
        setContentView(root);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            finish();
            return;
        }
        startCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCamera();
    }

    @Override
    protected void onPause() {
        stopCamera();
        super.onPause();
    }

    private void startCamera() {
        if (mCamera != null || mDone || !mPreview.isAvailable()
                || checkSelfPermission(Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mCamera = new QrCamera(this, this);
        mCamera.start(mPreview.getSurfaceTexture());
    }

    private void stopCamera() {
        if (mCamera != null) {
            mCamera.stop();
            mCamera = null;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        startCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        stopCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    @Override
    public void handleSuccessfulResult(String result) {
        runOnUiThread(() -> {
            if (mDone) return;
            mDone = true;
            stopCamera();
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_TEXT, result));
            finish();
        });
    }

    @Override
    public void handleCameraFailure() {
        runOnUiThread(() -> {
            Toast.makeText(this, R.string.looks_scan_failed, Toast.LENGTH_SHORT).show();
            stopCamera();
            finish();
        });
    }

    @Override
    public Size getViewSize() {
        return new Size(mPreview.getWidth(), mPreview.getHeight());
    }

    @Override
    public void setTransform(Matrix transform) {
        runOnUiThread(() -> mPreview.setTransform(transform));
    }

    @Override
    public boolean isValid(String qrCode) {
        return LooksActivity.parse(qrCode) != null;
    }
}
