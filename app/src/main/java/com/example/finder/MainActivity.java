package com.example.finder;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_MESSAGE = "com.example.finder.MESSAGE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_main);
        Button findButton = findViewById(R.id.findButton);
        findButton.setOnClickListener(v -> {
            if (validateSearchText()) {
                Intent intent = new Intent(v.getContext(), CameraPreviewActivity.class);
                EditText searchText = findViewById(R.id.searchText);
                String message = searchText.getText().toString();
                intent.putExtra(EXTRA_MESSAGE, message);
                startActivity(intent);
            } else {
                Context context = getApplicationContext();
                CharSequence errorText = "Invalid Text!";
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, errorText, duration);
                toast.show();
            }
        });
    }

    private boolean validateSearchText() {
        EditText searchText = findViewById(R.id.searchText);
        return searchText.getText().length() != 0;
    }
}