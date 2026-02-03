package com.example.ghostcam;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends Activity {

    private EditText usernameEdit;
    private EditText passwordEdit;
    private CheckBox saveLoginCheckbox;
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "GhostCamPrefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_SAVE_LOGIN = "save_login";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_login);

            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            usernameEdit = findViewById(R.id.username);
            passwordEdit = findViewById(R.id.password);
            saveLoginCheckbox = findViewById(R.id.save_login_checkbox);
            Button loginButton = findViewById(R.id.login_button);

            loadSavedLogin();

            loginButton.setOnClickListener(v -> attemptLogin());
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadSavedLogin() {
        boolean saveLogin = prefs.getBoolean(KEY_SAVE_LOGIN, false);
        saveLoginCheckbox.setChecked(saveLogin);
        
        if (saveLogin) {
            usernameEdit.setText(prefs.getString(KEY_USERNAME, ""));
            passwordEdit.setText(prefs.getString(KEY_PASSWORD, ""));
        }
    }

    private void attemptLogin() {
        String username = usernameEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        // 保存登录信息
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_SAVE_LOGIN, saveLoginCheckbox.isChecked());
        if (saveLoginCheckbox.isChecked()) {
            editor.putString(KEY_USERNAME, username);
            editor.putString(KEY_PASSWORD, password);
        } else {
            editor.remove(KEY_USERNAME);
            editor.remove(KEY_PASSWORD);
        }
        editor.apply();

        // 跳转到主界面
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
