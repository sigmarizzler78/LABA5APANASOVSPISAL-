package com.example.laba5;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "PopupPrefs";
    private static final String KEY_DONT_SHOW_AGAIN = "dontShowAgain";

    private EditText journalIdInput;
    private Button downloadButton, viewButton, deleteButton;
    private TextView statusTextView;
    private File downloadDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        journalIdInput = findViewById(R.id.journalIdInput);
        downloadButton = findViewById(R.id.downloadButton);
        viewButton = findViewById(R.id.viewButton);
        deleteButton = findViewById(R.id.deleteButton);
        statusTextView = findViewById(R.id.statusTextView);

        downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "journals");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        downloadButton.setOnClickListener(v -> downloadFile());
        viewButton.setOnClickListener(v -> openFile());
        deleteButton.setOnClickListener(v -> deleteFile());

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean dontShowAgain = preferences.getBoolean(KEY_DONT_SHOW_AGAIN, false);

        if (!dontShowAgain) {
            new Handler().post(() -> showPopupWindow());
        }
    }

    private void showPopupWindow() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.activity_popup_window, null);

        final PopupWindow popupWindow = new PopupWindow(
                popupView,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                true
        );

        Button okButton = popupView.findViewById(R.id.okButton);
        CheckBox dontShowAgainCheckbox = popupView.findViewById(R.id.dontShowAgainCheckbox);

        okButton.setOnClickListener(v -> {
            if (dontShowAgainCheckbox.isChecked()) {
                SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(KEY_DONT_SHOW_AGAIN, true);
                editor.apply();
            }
            popupWindow.dismiss();
        });

        popupWindow.showAtLocation(findViewById(android.R.id.content), android.view.Gravity.CENTER, 0, 0);
    }

    private void downloadFile() {
        String journalId = journalIdInput.getText().toString();
        if (journalId.isEmpty()) {
            Toast.makeText(this, "Введите ID журнала", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileUrl = "http://ntv.ifmo.ru/file/journal/" + journalId + ".pdf";
        File outputFile = new File(downloadDir, journalId + ".pdf");

        if (!isConnected()) {
            statusTextView.setText("Нет подключения");
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL(fileUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP ||
                        connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM) {
                    String newLocation = connection.getHeaderField("Location");
                    connection.disconnect();
                    url = new URL(newLocation);
                    connection = (HttpURLConnection) url.openConnection();
                }

                connection.connect();
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    FileOutputStream outputStream = new FileOutputStream(outputFile);

                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    inputStream.close();
                    outputStream.close();

                    runOnUiThread(() -> {
                        statusTextView.setText("Файл загружен");
                        viewButton.setEnabled(true);
                        deleteButton.setEnabled(true);
                    });
                } else {
                    runOnUiThread(() -> statusTextView.setText("Ошибка загрузки"));
                }

                connection.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> statusTextView.setText("Ошибка загрузки"));
            }
        }).start();
    }

    private void openFile() {
        String journalId = journalIdInput.getText().toString();
        File file = new File(downloadDir, journalId + ".pdf");

        if (!file.exists()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri fileUri = FileProvider.getUriForFile(this, "com.example.laba5.fileprovider", file);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileUri, "application/pdf");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Нет приложения для открытия PDF", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFile() {
        String journalId = journalIdInput.getText().toString();
        File file = new File(downloadDir, journalId + ".pdf");

        if (file.exists() && file.delete()) {
            statusTextView.setText("Файл удален");
            viewButton.setEnabled(false);
            deleteButton.setEnabled(false);
        } else {
            Toast.makeText(this, "Не удалось удалить файл", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isConnected() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }
}
