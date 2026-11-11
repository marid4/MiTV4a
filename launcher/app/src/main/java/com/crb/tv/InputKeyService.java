package com.crb.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Layanan Aksesibilitas opsional untuk menangkap tombol INPUT / TV pada remote TV
 * dan membuka popup pemilih HDMI/AV/Live TV di CleanYourTV.
 */
public class InputKeyService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_TV_INPUT || code == KeyEvent.KEYCODE_TV) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("open_inputs", true);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                return true;
            }
        }
        return super.onKeyEvent(event);
    }
}
