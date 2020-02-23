package com.example.cropper.translate;

import android.Manifest;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import static com.example.cropper.translate.GlobalVars.BASE_REQ_URL;
import static com.example.cropper.translate.GlobalVars.DEFAULT_LANG_POS;
import static com.example.cropper.translate.GlobalVars.LANGUAGE_CODES;


import com.example.cropper.CropImage;
import com.example.cropper.CropImageView;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

public class TranslationActivity extends AppCompatActivity implements TextToSpeech.OnInitListener{

    EditText mEdtResult;
    ImageView mPreview;

    public static final String LOG_TAG = TranslationActivity.class.getName();
    private static final int REQ_CODE_SPEECH_INPUT = 1;
    private static final int CAMERA_REQUEST_CODE = 200;
    private static final int STORAGE_REQUEST_CODE = 400;
    private static final int IMAGE_PICK_GALLERY_CODE = 600;
    private static final int IMAGE_PICK_CAMERA_CODE = 601;

    private TextToSpeech mTextToSpeech;                     //    Text to Speech Engine
    private Spinner mSpinnerLanguageFrom;                   //    Dropdown list for selecting base language (From)
    private Spinner mSpinnerLanguageTo;                     //    Dropdown list for selecting translation language (To)
    private String mLanguageCodeFrom = "en";                //    Language Code (From)
    private String mLanguageCodeTo = "en";                  //    Language Code (To)
    private ImageView mImageSpeak1;                          //    Speak button to read out translated text (Text to Speech)
    private ImageView mImageImage;                          //    Image button to choose image to translate (Image to Text)
    private ImageView mImageSpeak2;
    private EditText mTextInput;                            //    Input text ( in From language )
    private TextView mTextTranslated;                       //    Output Translated text ( in To language )
    private Dialog process_tts;                             //    Dialog box for Text to Speech Engine Language Switch
    HashMap<String, String> map = new HashMap<>();
    volatile boolean activityRunning;                       //    To track status of current activity

    String cameraPermission[];
    String storagePermission[];

    Uri image_uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translation);

        activityRunning=true;
        TextView mEmptyTextView = (TextView) findViewById(R.id.empty_view_not_connected);
        mSpinnerLanguageFrom = (Spinner) findViewById(R.id.spinner_language_from);
        mSpinnerLanguageTo = (Spinner) findViewById(R.id.spinner_language_to);
        Button mButtonTranslate = (Button) findViewById(R.id.button_translate);         //      Translate button to translate text
        ImageView mImageSwap = (ImageView) findViewById(R.id.image_swap);               //      Swap Language button to swap languages
        ImageView mImageListen = (ImageView) findViewById(R.id.image_listen);           //      Mic button for Speech to text
        mImageSpeak1 = (ImageView) findViewById(R.id.image_speak1);
        mImageSpeak2 = (ImageView) findViewById(R.id.image_speak2);
        mImageImage = (ImageView) findViewById(R.id.image_image);
        ImageView mClearText = (ImageView) findViewById(R.id.clear_text);               //      Clear button to clear text fields
        mTextInput = (EditText) findViewById(R.id.text_input);
        mTextTranslated = (TextView) findViewById(R.id.text_translated);
        mTextTranslated.setMovementMethod(new ScrollingMovementMethod());
        process_tts = new Dialog(TranslationActivity.this);
        process_tts.setContentView(R.layout.dialog_processing_tts);
        process_tts.setTitle(getString(R.string.process_tts));
        TextView title = (TextView) process_tts.findViewById(android.R.id.title);
        // title.setSingleLine(false);
        mTextToSpeech = new TextToSpeech(this, this);

        cameraPermission = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        storagePermission = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        //  CHECK INTERNET CONNECTION
        if (!isOnline()) {
            mEmptyTextView.setVisibility(View.VISIBLE);
        } else {
            mEmptyTextView.setVisibility(View.GONE);
            //  GET LANGUAGES LIST
            new GetLanguages().execute();
            //  SPEECH TO TEXT
            mImageListen.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, mLanguageCodeFrom);
                    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt));
                    try {
                        startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
                    } catch (ActivityNotFoundException a) {
                        Toast.makeText(getApplicationContext(), getString(R.string.language_not_supported), Toast.LENGTH_SHORT).show();
                    }
                }
            });
            //  IMAGE TO TEXT
            mImageImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showImageImportDialog();
                }

            });

            //  TEXT TO SPEECH
            mImageSpeak1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    speakOut();
                }
            });
            mImageSpeak2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    speakOut2();
                }
            });
            //  TRANSLATE
           /* mTextInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    String input = mTextInput.getText().toString();
                    // String input = "hi everyone";
                    new TranslateText().execute(input);
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });*/
           mButtonTranslate.setOnClickListener(new View.OnClickListener() {
               @Override
               public void onClick(View view) {
                   String input = mTextInput.getText().toString();
                   // String input = "hi everyone";
                   new TranslateText().execute(input);
               }
           });
            //  SWAP BUTTON
            mImageSwap.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String temp = mLanguageCodeFrom;
                    mLanguageCodeFrom = mLanguageCodeTo;
                    mLanguageCodeTo = temp;
                    int posFrom = mSpinnerLanguageFrom.getSelectedItemPosition();
                    int posTo = mSpinnerLanguageTo.getSelectedItemPosition();
                    mSpinnerLanguageFrom.setSelection(posTo);
                    mSpinnerLanguageTo.setSelection(posFrom);
                    String textFrom = mTextInput.getText().toString();
                    String textTo = mTextTranslated.getText().toString();
                    mTextInput.setText(textTo);
                    mTextTranslated.setText(textFrom);
                }
            });
            //  CLEAR TEXT
            mClearText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mTextInput.setText("");
                    mTextTranslated.setText("");
                }
            });
            //  SPINNER LANGUAGE FROM
            mSpinnerLanguageFrom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    mLanguageCodeFrom = LANGUAGE_CODES.get(position);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    Toast.makeText(getApplicationContext(), "No option selected", Toast.LENGTH_SHORT).show();
                }
            });
            //  SPINNER LANGUAGE TO
            mSpinnerLanguageTo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    mLanguageCodeTo = LANGUAGE_CODES.get(position);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    Toast.makeText(getApplicationContext(), "No option selected", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void showImageImportDialog() {
        String[] items = {"Camera", "Gallery"};
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Select Image");
        dialog.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                if (i == 0) {
                    if (!checkCameraPermission()) {
                        requestCameraPermission();
                    }
                    else
                        pickCamera();
                }
                if (i == 1) {
                    if (!checkStoragePermission()) {
                        requestStoragePermission();
                    } else
                        pickGallery();
                }
            }
        });
        dialog.create().show();
    }
    private void pickGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, IMAGE_PICK_GALLERY_CODE);
    }

    private void pickCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "NewPic");
        values.put(MediaStore.Images.Media.DESCRIPTION, "Image to Text");
        image_uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri);
        startActivityForResult(cameraIntent, IMAGE_PICK_CAMERA_CODE);
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this, storagePermission, STORAGE_REQUEST_CODE);
    }

    private boolean checkStoragePermission() {
        boolean result = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
        return result;
    }

    private void requestCameraPermission() {
        CropImage.activity(null).setGuidelines(CropImageView.Guidelines.ON).start(this);
        ActivityCompat.requestPermissions(this, cameraPermission, CAMERA_REQUEST_CODE);
    }

    private boolean checkCameraPermission() {
        boolean result = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == (PackageManager.PERMISSION_GRANTED);
        boolean result1 = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
        return result && result1;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case CAMERA_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean writeStoryAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    if (cameraAccepted && writeStoryAccepted) {
                        pickCamera();
                    }
                    else
                        Toast.makeText(this, " Camera Permission denied", Toast.LENGTH_SHORT).show();

                }
                break;
            case STORAGE_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean writeStoryAccept = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    if (writeStoryAccept) {
                        pickGallery();
                    } else
                        Toast.makeText(this, "Storage Permission denied", Toast.LENGTH_SHORT).show();

                }
                break;
        }
    }
    /**
     * Start pick image activity with chooser.
     */
    public void onSelectImageClick(View view) {
        CropImage.activity(null).setGuidelines(CropImageView.Guidelines.ON).start(this);
    }

    //  CHECK INTERNET CONNECTION
    public  boolean isOnline()
    {   try {
            ConnectivityManager connectivityManager = (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected();
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }
        return false;
    }
    //  RESULT OF SPEECH INPUT
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //choose image
        if (resultCode == RESULT_OK) {
            if (requestCode == IMAGE_PICK_GALLERY_CODE) {
                CropImage.activity(data.getData()).setGuidelines(CropImageView.Guidelines.ON).start(this);
            }
            if (requestCode == IMAGE_PICK_CAMERA_CODE) {
                CropImage.activity(image_uri).setGuidelines(CropImageView.Guidelines.ON).start(this);
            }
        }
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                Uri resultUri = result.getUri();
                Bitmap bitmap = null;
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(),resultUri);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                TextRecognizer recognizer = new TextRecognizer.Builder(getApplicationContext()).build();
                if (!recognizer.isOperational()) {
                    Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
                }
                else {
                    Frame frame = new Frame.Builder().setBitmap(bitmap).build();
                    SparseArray<TextBlock> items = recognizer.detect(frame);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < items.size(); i++) {
                        TextBlock myItem = items.valueAt(i);
                        sb.append(myItem.getValue());
                        sb.append("\n");
                    }
                    mTextInput.setText(sb.toString());
                }
            }
            else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Exception error = result.getError();
                Toast.makeText(this, "" + error, Toast.LENGTH_SHORT).show();
            }
        }
        //end choose image
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {
                    /*
                            Dialog box to show list of processed Speech to text results
                            User selects matching text to display in chat
                     */
                    final Dialog match_text_dialog = new Dialog(TranslationActivity.this);
                    match_text_dialog.setContentView(R.layout.dialog_matches_frag);
                    match_text_dialog.setTitle(getString(R.string.select_matching_text));
                    ListView textlist = (ListView)match_text_dialog.findViewById(R.id.list);
                    final ArrayList<String> matches_text = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,matches_text);
                    textlist.setAdapter(adapter);
                    textlist.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            mTextInput.setText(matches_text.get(position));
                            match_text_dialog.dismiss();
                        }
                    });
                    match_text_dialog.show();
                    break;
                }
            }
        }
    }
    //  INITIALISE TEXT TO SPEECH ENGINE
    @Override
    public void onInit(int status) {
        Log.e("Inside----->", "onInit");
        if (status == TextToSpeech.SUCCESS) {
            int result = mTextToSpeech.setLanguage(new Locale("en"));
            if (result == TextToSpeech.LANG_MISSING_DATA) {
                Toast.makeText(getApplicationContext(), getString(R.string.language_pack_missing), Toast.LENGTH_SHORT).show();
            } else if (result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(getApplicationContext(), getString(R.string.language_not_supported), Toast.LENGTH_SHORT).show();
            }
            mImageSpeak1.setEnabled(true);
            mImageSpeak2.setEnabled(true);
            mTextToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.e("Inside","OnStart");
                    process_tts.hide();
                }
                @Override
                public void onDone(String utteranceId) {
                }
                @Override
                public void onError(String utteranceId) {
                }
            });
        } else {
            Log.e(LOG_TAG,"TTS Initilization Failed");
        }
    }
    //  TEXT TO SPEECH ACTION
    @SuppressWarnings("deprecation")
    private void speakOut2(){
        int result = mTextToSpeech.setLanguage(new Locale(mLanguageCodeTo));
        Log.e("Inside","speakOut "+mLanguageCodeTo+" "+result);
        if (result == TextToSpeech.LANG_MISSING_DATA ){
            Toast.makeText(getApplicationContext(),getString(R.string.language_pack_missing),Toast.LENGTH_SHORT).show();
            Intent installIntent = new Intent();
            installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            startActivity(installIntent);
        } else if(result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(getApplicationContext(),getString(R.string.language_not_supported),Toast.LENGTH_SHORT).show();
        } else {
            String textMessage = mTextTranslated.getText().toString();
            process_tts.show();
            map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UniqueID");
            mTextToSpeech.speak(textMessage, TextToSpeech.QUEUE_FLUSH, map);
        }
    }
    private void speakOut(){
        int result = mTextToSpeech.setLanguage(new Locale(mLanguageCodeFrom));
        Log.e("Inside","speakOut "+mLanguageCodeFrom+" "+result);
        if (result == TextToSpeech.LANG_MISSING_DATA ){
            Toast.makeText(getApplicationContext(),getString(R.string.language_pack_missing),Toast.LENGTH_SHORT).show();
            Intent installIntent = new Intent();
            installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            startActivity(installIntent);
        } else if(result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(getApplicationContext(),getString(R.string.language_not_supported),Toast.LENGTH_SHORT).show();
        } else {
            String textMessage = mTextInput.getText().toString();
            process_tts.show();
            map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UniqueID");
            mTextToSpeech.speak(textMessage, TextToSpeech.QUEUE_FLUSH, map);
        }
    }
    //  WHEN ACTIVITY IS PAUSED
    @Override
    protected void onPause() {
        if(mTextToSpeech!=null){
            mTextToSpeech.stop();
        }
        super.onPause();
    }
    //  WHEN ACTIVITY IS DESTROYED
    @Override
    public void onDestroy() {
        if (mTextToSpeech != null) {
            mTextToSpeech.stop();
            mTextToSpeech.shutdown();
        }
        activityRunning=false;
        process_tts.dismiss();
        super.onDestroy();
    }
    //  SUBCLASS TO TRANSLATE TEXT ON BACKGROUND THREAD
    private class TranslateText extends AsyncTask<String,Void,String> {
        @Override
        protected String doInBackground(String... input) {
            Uri baseUri = Uri.parse(BASE_REQ_URL);
            Uri.Builder uriBuilder = baseUri.buildUpon();
            uriBuilder.appendPath("translate")
                    .appendQueryParameter("key",getString(R.string.API_KEY))
                    .appendQueryParameter("lang",mLanguageCodeFrom+"-"+mLanguageCodeTo)
                    .appendQueryParameter("text",input[0]);
            Log.e("String Url ---->",uriBuilder.toString());
            return QueryUtils.fetchTranslation(uriBuilder.toString());
        }
        @Override
        protected void onPostExecute(String result) {
            if(activityRunning) {
                mTextTranslated.setText(result);
            }
        }
    }
    //  SUBCLASS TO GET LIST OF LANGUAGES ON BACKGROUND THREAD
    private class GetLanguages extends AsyncTask<Void,Void,ArrayList<String>>{
        @Override
        protected ArrayList<String> doInBackground(Void... params) {
            Uri baseUri = Uri.parse(BASE_REQ_URL);
            Uri.Builder uriBuilder = baseUri.buildUpon();
            uriBuilder.appendPath("getLangs")
                    .appendQueryParameter("key",getString(R.string.API_KEY))
                    .appendQueryParameter("ui","en");
            Log.e("String Url ---->",uriBuilder.toString());
            return QueryUtils.fetchLanguages(uriBuilder.toString());
        }
        @Override
        protected void onPostExecute(ArrayList<String> result) {
            if (activityRunning) {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(TranslationActivity.this, android.R.layout.simple_spinner_item, result);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                mSpinnerLanguageFrom.setAdapter(adapter);
                mSpinnerLanguageTo.setAdapter(adapter);
                //  SET DEFAULT LANGUAGE SELECTIONS
                mSpinnerLanguageFrom.setSelection(DEFAULT_LANG_POS);
                mSpinnerLanguageTo.setSelection(DEFAULT_LANG_POS);
            }
        }
    }
}