package dev.filedeck.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Плагин нужно зарегистрировать до super.onCreate()
        registerPlugin(FileManagerPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
