package com.example.photoediting;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.vision.label.internal.client.LabelOptions;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.label.FirebaseVisionCloudImageLabelerOptions;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceAutoMLImageLabelerOptions;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceImageLabelerOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLOutput;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_CODE = 1000;
    private static final int IMAGE_CAPTURE_CODE = 1001;
    ImageView imageView;
    ImageDecoder.Source source;
    Bitmap selectedImage,smallImage;
    Button button;
    Uri image_uri;
    Uri imageData;
    TextView detectedText;
    private FirebaseStorage firebaseStorage;
    private StorageReference storageReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        button = findViewById(R.id.button);
        detectedText = findViewById(R.id.textView);

        firebaseStorage = FirebaseStorage.getInstance();
        storageReference = firebaseStorage.getReference();
    }

    public void detect(View view){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        smallImage.compress(Bitmap.CompressFormat.JPEG,100,outputStream);
        byte[] byteArray = outputStream.toByteArray();

        FirebaseVisionCloudImageLabelerOptions options = new FirebaseVisionCloudImageLabelerOptions.Builder().setConfidenceThreshold(0.7f).build();

        FirebaseVisionImageMetadata metadata = new FirebaseVisionImageMetadata.Builder()
                .setWidth(480)   // 480x360 is typically sufficient for
                .setHeight(360)  // image recognition
                .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                .build();
        FirebaseVisionImage Image = FirebaseVisionImage.fromByteArray( byteArray, metadata);
        FirebaseVisionImageLabeler detector = FirebaseVision.getInstance().getCloudImageLabeler(options);
        detector.processImage(Image)
                .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
                    @Override
                    public void onSuccess(List<FirebaseVisionImageLabel> labels) {
                        for(FirebaseVisionImageLabel label: labels){
                            detectedText.append(label.getText() + "\n");
                            String entityId =label.getEntityId();
                            float confidence = label.getConfidence();
                            System.out.println(label.getText() + " "+entityId +" "+confidence);
                        }
                        // detectedText.setText(stringBuilder.toString());
                    }
                });
    }

    public void download(View view){
        storageReference.child("images/photo_200x200.jpg").getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                String imageUri = uri.toString();
                Picasso.get().load(imageUri).into(imageView);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {

            }
        });
    }

    public void upload(View view){
        if(imageData != null) {
            storageReference.child("images").child("photo.jpg").putFile(imageData).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Toast.makeText(MainActivity.this,"Photo Uploading...",Toast.LENGTH_SHORT).show();
                    imageData=null;
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(MainActivity.this, e.getLocalizedMessage().toString(), Toast.LENGTH_LONG).show();
                }
            });
        }
        if(image_uri != null){
            storageReference.child("images").child("photo.jpg").putFile(image_uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Toast.makeText(MainActivity.this,"Photo Uploading...",Toast.LENGTH_SHORT).show();
                    image_uri=null;
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(MainActivity.this, e.getLocalizedMessage().toString(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    public void selectImage(View view){
        detectedText.setText("");
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1 );
        }else {
            Intent intentToGallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intentToGallery, 2);
        }
    }

    public void selectCamera(View view){
        detectedText.setText("");
        if (Build.VERSION.SDK_INT >= 28) {
            if(checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_DENIED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                            PackageManager.PERMISSION_DENIED) {
                String[] permision = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};

                requestPermissions(permision, PERMISSION_CODE);
            }
            else {
                openCamera();
            }
        }else {
            openCamera();
        }
    }

    private void openCamera(){
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera");
        image_uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri);
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE);
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == IMAGE_CAPTURE_CODE && resultCode == RESULT_OK){
            //makeSmallerImage(image_uri,300);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    source = ImageDecoder.createSource(this.getContentResolver(), image_uri);
                    selectedImage = ImageDecoder.decodeBitmap(source);
                    smallImage = makeSmallerImage(selectedImage,800);
                    imageView.setImageBitmap(smallImage);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // imageView.setImageURI(image_uri);
        }

        else if(requestCode == 2 && resultCode == RESULT_OK && data != null){

            imageData = data.getData();

            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    if(requestCode == 2) {
                        source = ImageDecoder.createSource(this.getContentResolver(), imageData);
                        selectedImage = ImageDecoder.decodeBitmap(source);
                        smallImage = makeSmallerImage(selectedImage,800);
                        imageView.setImageBitmap(smallImage);
                    }
                }else {
                    selectedImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageData);
                    smallImage = makeSmallerImage(selectedImage,800);
                    imageView.setImageBitmap(smallImage);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public Bitmap makeSmallerImage(Bitmap image, int maximumSize){
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float)width / (float)height;
        if(bitmapRatio > 1){
            width = maximumSize;
            height = (int)(width / bitmapRatio);
        }else{
            height = maximumSize;
            width = (int)(height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(image,width,height,true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 1 ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intentToGallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intentToGallery, 2);
            }
        }else if(requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            }else {
                Toast.makeText(this,"Permission denied...",Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}
