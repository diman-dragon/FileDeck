package dev.filedeck.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Плагины нужно зарегистрировать до super.onCreate()
        registerPlugin(FileManagerPlugin.class);
        registerPlugin(CleanerPlugin.class);
        registerPlugin(TrashPlugin.class);
        registerPlugin(ArchivePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
