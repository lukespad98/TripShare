package com.example.tripshare;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;


/**
 * A simple {@link Fragment} subclass.
 */
public class AddBlogsFragment extends Fragment {

    public AddBlogsFragment() {
        // Required empty public constructor
    }

    FirebaseAuth firebaseAuth;
    EditText title, des;
    private static final int CAMERA_REQUEST = 100;
    private static final int STORAGE_REQUEST = 200;
    String[] cameraPermission;
    String[] storagePermission;
    ProgressDialog pd;
    ImageView image;
    String edititle, editdes, editimage;
    private static final int IMAGEPICK_GALLERY_REQUEST = 300;
    private static final int IMAGE_PICKCAMERA_REQUEST = 400;

    Uri imageuri = null;
    String name, email, uid, dp;
    DatabaseReference databaseReference;
    Button upload;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        firebaseAuth = FirebaseAuth.getInstance();
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        View view = inflater.inflate(R.layout.fragment_add_blogs, container, false);

        title = view.findViewById(R.id.ptitle);
        des = view.findViewById(R.id.pdes);
        image = view.findViewById(R.id.imagep);
        upload = view.findViewById(R.id.pupload);
        pd = new ProgressDialog(getContext());
        pd.setCanceledOnTouchOutside(false);
        Intent intent = getActivity().getIntent();

        // Retrieving the user data like name ,email and profile pic using query
        databaseReference = FirebaseDatabase.getInstance().getReference("Users");
        Query query = databaseReference.orderByChild("email").equalTo(email);
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot dataSnapshot1 : dataSnapshot.getChildren()) {
                    name = dataSnapshot1.child("name").getValue(String.class);
                    email = "" + dataSnapshot1.child("email").getValue(String.class);
                    dp = "" + dataSnapshot1.child("image").getValue(String.class);
                    System.out.println("User Name: " + name + "User email: " + email);
                    System.out.println("Firebase user: " + firebaseUser);
                }
                System.out.println("Post name: " + name + "Post email: " + email);
                upload.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String titl = "" + title.getText().toString().trim();
                        String description = "" + des.getText().toString().trim();

                        // If empty set error
                        if (TextUtils.isEmpty(titl)) {
                            title.setError("Title Cant be empty");
                            Toast.makeText(getContext(), "Title can't be left empty", Toast.LENGTH_LONG).show();
                            return;
                        }

                        // If empty set error
                        if (TextUtils.isEmpty(description)) {
                            des.setError("Description Cant be empty");
                            Toast.makeText(getContext(), "Description can't be left empty", Toast.LENGTH_LONG).show();
                            return;
                        }

                        // If empty show error
                        if (imageuri == null) {
                            Toast.makeText(getContext(), "Select an Image", Toast.LENGTH_LONG).show();
                            return;
                        } else {
                            uploadData(titl, description);
                        }
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        // Initialising camera and storage permission
        cameraPermission = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        storagePermission = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        // After click on image we will be selecting an image
        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImagePicDialog();
            }
        });

        // Now we will upload out blog

        return view;
    }

    private void showImagePicDialog() {
        String options[] = {"Camera", "Gallery"};
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Pick Image From");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // check for the camera and storage permission if
                // not given the request for permission
                if (which == 0) {
                    if (!checkCameraPermission()) {
                        requestPermissions();
                    } else {
                        pickFromCamera();
                    }
                } else if (which == 1) {
                    if (!checkStoragePermission()) {
                        requestPermissions();
                    } else {
                        pickFromGallery();
                    }
                }
            }
        });
        builder.create().show();
    }

    // check for storage permission
    private Boolean checkStoragePermission() {
        boolean result = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_IMAGES)
                == (PackageManager.PERMISSION_GRANTED);
        boolean result1 = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_VIDEO)
                == (PackageManager.PERMISSION_GRANTED);
        return result && result1;
    }

    // if not given then request for permission after that check if request is given or not
    private ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // Check if permissions were granted
                if (result.containsValue(false)) {
                    // Permission denied
                    Toast.makeText(getContext(), "Permission denied", Toast.LENGTH_SHORT).show();
                } else {
                    showImagePicDialog();// Permissions granted, handle accordingly
                }
            });

    // request for permission to write data into storage
    private void requestPermissions() {
        String[] permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES};
        requestPermissionLauncher.launch(permissions);
    }

    // check camera permission to click picture using camera
    private Boolean checkCameraPermission() {
        boolean result = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == (PackageManager.PERMISSION_GRANTED);
        return result;
    }

    // request for permission to click photo using camera in app


    // if access is given then pick image from camera and then put
    // the imageuri in intent extra and pass to startactivityforresult
    private void pickFromCamera() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.Media.TITLE, "Temp_pic");
        contentValues.put(MediaStore.Images.Media.DESCRIPTION, "Temp Description");
        imageuri = getActivity().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
        Intent camerIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        camerIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageuri);
        startActivityForResult(camerIntent, IMAGE_PICKCAMERA_REQUEST);
    }

    // if access is given then pick image from gallery
    private void pickFromGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);
        galleryIntent.setType("image/*");
        startActivityForResult(galleryIntent, IMAGEPICK_GALLERY_REQUEST);
    }

    // Upload the value of blog data into firebase
    private void uploadData(final String titl, final String description) {
        // show the progress dialog box
        pd.setMessage("Publishing Post");
        pd.show();
        final String timestamp = String.valueOf(System.currentTimeMillis());
        String filepathname = "Posts/" + "post" + timestamp;
        Bitmap bitmap = ((BitmapDrawable) image.getDrawable()).getBitmap();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] data = byteArrayOutputStream.toByteArray();

        // initialising the storage reference for updating the data
        StorageReference storageReference1 = FirebaseStorage.getInstance().getReference().child(filepathname);
        storageReference1.putBytes(data).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // getting the url of image uploaded
                Task<Uri> uriTask = taskSnapshot.getStorage().getDownloadUrl();
                while (!uriTask.isSuccessful()) ;
                String downloadUri = uriTask.getResult().toString();
                if (uriTask.isSuccessful()) {
                    // if task is successful the update the data into firebase
                    HashMap<Object, String> hashMap = new HashMap<>();
                    hashMap.put("uid", uid);
                    hashMap.put("uname", name);
                    hashMap.put("uemail", email);
                    hashMap.put("udp", dp);
                    hashMap.put("title", titl);
                    hashMap.put("description", description);
                    hashMap.put("uimage", downloadUri);
                    hashMap.put("ptime", timestamp);
                    hashMap.put("plike", "0");
                    hashMap.put("pcomments", "0");

                    // set the data into firebase and then empty the title ,description and image data
                    DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("Posts");
                    databaseReference.child(timestamp).setValue(hashMap)
                            .addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void aVoid) {
                                    pd.dismiss();
                                    Toast.makeText(getContext(), "Published", Toast.LENGTH_LONG).show();
                                    title.setText("");
                                    des.setText("");
                                    image.setImageURI(null);
                                    imageuri = null;
                                    startActivity(new Intent(getContext(), DashboardActivity.class));
                                    getActivity().finish();
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    pd.dismiss();
                                    Toast.makeText(getContext(), "Failed", Toast.LENGTH_LONG).show();
                                }
                            });
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                pd.dismiss();
                Toast.makeText(getContext(), "Failed", Toast.LENGTH_LONG).show();
            }
        });
    }

    // Here we are getting data from image
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == getActivity().RESULT_OK) {
            if (requestCode == IMAGEPICK_GALLERY_REQUEST) {
                imageuri = data.getData();
                image.setImageURI(imageuri);
            }
            if (requestCode == IMAGE_PICKCAMERA_REQUEST) {
                image.setImageURI(imageuri);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}