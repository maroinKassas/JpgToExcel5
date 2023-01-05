package com.example.JpgToExcel5;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.googlecode.tesseract.android.BuildConfig;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TESS_DATA = "/tessdata";

    private ActivityResultLauncher activityResultLauncher;
    private ActivityResultLauncher<String> activityOpenImage;
    private Bitmap bitmap = null;
    private ImageView imageView;
    private TessBaseAPI tessBaseAPI;
    private static Uri photoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = (ImageView)findViewById(R.id.image_view);

        activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                preCooking();
            }
        });

        activityOpenImage = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                photoUri = uri;
                preCooking();
            }
        });

        ((Button)findViewById(R.id.camera)).setOnClickListener(v -> {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            int n = 0;
            while(n < 2){
                File file = null;
                try {
                    if (n == 0) {
                        file = createImageFile();
                    } else {
                        file = createImageFile2();
                    }
                } catch (IOException e) {
                    makeToast("Failed make file : " + e);
                }
                if (file != null) {
                    photoUri = FileProvider.getUriForFile(
                            getApplicationContext(),
                            BuildConfig.APPLICATION_ID + "." + getLocalClassName() + ".provider",
                            file);

                    intent.putExtra(MediaStore.EXTRA_OUTPUT,photoUri);
                }
                if (intent.resolveActivity(getPackageManager()) != null) {
                    activityResultLauncher.launch(intent);
                    break;
                }
                n++;
            }
        });

        ((Button)findViewById(R.id.galerie)).setOnClickListener(v -> activityOpenImage.launch("image/*"));

        ((Button)findViewById(R.id.scan)).setOnClickListener(v -> letCooking());

        checkPermission();
        prepareTessData();
    }

    private void updateInformation(String str) {
        ((TextView) findViewById(R.id.information)).setText(str);
    }

    private void letCooking() {
        if(bitmap != null){
            updateInformation("Scanning..");
            new Thread(() -> {
                String textImage = getText(bitmap);
                runOnUiThread(() -> updateInformation(textImage));
            }).start();
        }else {
            updateInformation("Bitmap is empty!");
        }
    }

    private void preCooking() {
        if (photoUri != null){

            updateInformation("Please wait");

            bitmap = grabImage(photoUri);
            imageView.setImageBitmap(bitmap);

            new Thread(() -> runOnUiThread(() -> {
                imageView.setImageBitmap(bitmap);
                updateInformation("Ready to scan");
            })).start();
        } else {
            makeToast("Uri is Null");
        }
    }

    private void makeToast(String txt) {
        Toast.makeText(MainActivity.this, txt,Toast.LENGTH_SHORT).show();
    }

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)== PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[] {Manifest.permission.CAMERA}, 120);
        }
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 121);
        }
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 122);
        }
    }

    private void prepareTessData() {
        try {
            File dir = getExternalFilesDir(TESS_DATA);
            if (!dir.exists()) {
                if (!dir.mkdir()) {
                    makeToast("Cannot make Dir");
                }
            }
            String[] fileList = getAssets().list("");
            for (String fileName : fileList) {

                String pathToDataFile = dir + "/" + fileName;
                int IndexOfExtension = fileName.lastIndexOf(".");
                String extension = (IndexOfExtension >= 0) ? fileName.substring(IndexOfExtension) : "";

                if (!(new File(pathToDataFile)).exists() && extension.equals(".traineddata")) {

                    InputStream inputStream = getAssets().open(fileName);
                    OutputStream outputStream = new FileOutputStream(pathToDataFile);
                    byte[] buff = new byte[1024];
                    int length;

                    while (( length = inputStream.read(buff)) > 0) {
                        outputStream.write(buff, 0, length);
                    }

                    inputStream.close();
                    outputStream.close();
                }
            }
        } catch (Exception e) {
            makeToast("Error :" + e.getMessage());
        }
    }

    private File createImageFile() throws IOException {
        File storageDir = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return new File(storageDir, "timeStamp.jpg");
    }

    private File createImageFile2() throws IOException {
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(storageDir,"timeStamp.jpg");
    }


    private String getText(Bitmap bitmap) {
        try {
            tessBaseAPI = new TessBaseAPI();
        } catch (Exception e){
            makeToast(e.getMessage());
        }

        String dataPath = getExternalFilesDir("/").getPath() + "/";
        tessBaseAPI.init(dataPath, "fra");

        tessBaseAPI.setImage(bitmap);
        String textImage = "No result";

        try {
            textImage = tessBaseAPI.getUTF8Text();
        } catch (Exception e){
            makeToast(e.getMessage());
        }

        tessBaseAPI.end();

        return textImage;
    }

    private Bitmap grabImage(Uri url) {
        this.getContentResolver().notifyChange(url, null);
        ContentResolver contentResolver = this.getContentResolver();
        Bitmap bitmap = null;
        try {
            bitmap = android.provider.MediaStore.Images.Media.getBitmap(contentResolver, photoUri);
        } catch (Exception e) {
            makeToast("Failed to load");
        }
        return bitmap;
    }
}